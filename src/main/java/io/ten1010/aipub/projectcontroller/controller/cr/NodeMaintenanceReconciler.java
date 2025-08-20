package io.ten1010.aipub.projectcontroller.controller.cr;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.BoundObjectResolver;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.*;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeMaintenance;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeMaintenanceAction;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeMaintenanceStatus;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeMaintenanceStatusAction;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.StatusPatchHelper;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class NodeMaintenanceReconciler extends AbstractReconciler {

    private final KeyResolver keyResolver;
    private final Indexer<V1alpha1NodeMaintenance> projectIndexer;
    private final BoundObjectResolver boundObjectResolver;
    private final StatusPatchHelper<V1alpha1NodeMaintenance> statusPatchHelper;

    public NodeMaintenanceReconciler(
            SharedInformerFactory sharedInformerFactory,
            K8sApiProvider k8sApiProvider) {
        this.keyResolver = new KeyResolver();
        this.projectIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1NodeMaintenance.class)
                .getIndexer();
        this.boundObjectResolver = new BoundObjectResolver(sharedInformerFactory);
        this.statusPatchHelper = new StatusPatchHelper<>(
                k8sApiProvider.getApiClient(),
                K8sObjectTypeConstants.NODE_MAINTENANCE_V1ALPHA1,
                ProjectApiConstants.NODE_MAINTENANCE_RESOURCE_PLURAL);
    }

    @Override
    protected Result reconcileInternal(Request request) throws ApiException {
        String projectKey = new RequestHelper(this.keyResolver).resolveKey(request);
        Optional<V1alpha1NodeMaintenance> nodeMaintenanceOpt = Optional.ofNullable(this.projectIndexer.getByKey(projectKey));
        if (nodeMaintenanceOpt.isEmpty()) {
            return new Result(false);
        }

        var nodeMaintenance = nodeMaintenanceOpt.get();
        var spec = Objects.requireNonNull(nodeMaintenance.getSpec());
        var status = nodeMaintenance.getStatus();
        List<String> targetNodeNames = Objects.requireNonNull(spec.getTargetNodes());

        if (status != null && targetNodeNames.equals(status.getAllEffectedNodes())) {
            List<String> specAction = spec.getActions().stream().map(V1alpha1NodeMaintenanceAction::getType).toList();
            List<String> statusAction = new ArrayList<>();
            List<String> statusNotCompleted = new ArrayList<>();
            for (V1alpha1NodeMaintenanceStatusAction action : status.getActions()) {
                statusAction.add(action.getType());
                if (action.getStatus() == null || !action.getStatus().equals(NodeMaintenanceConstants.NN_COMPLETED)) {
                    statusNotCompleted.add("NOT COMPLETED");
                }
            }
            if (specAction.equals(statusAction) && statusNotCompleted.isEmpty()) {
                return new Result(false);
            }
        }

        V1alpha1NodeMaintenanceStatus edited = new V1alpha1NodeMaintenanceStatus();
        edited.setAllEffectedNodes(targetNodeNames);
        List<V1alpha1NodeMaintenanceStatusAction> actionList = new ArrayList<>();
        for (V1alpha1NodeMaintenanceAction action : nodeMaintenance.getSpec().getActions()) {
            V1alpha1NodeMaintenanceStatusAction _action = new V1alpha1NodeMaintenanceStatusAction();
            _action.setType(action.getType());
            _action.setStatus(NodeMaintenanceConstants.NN_PROGRESS);
            actionList.add(_action);
        }
        edited.setActions(actionList);
        nodeMaintenance.setStatus(edited);
        this.updateNodeMaintenanceStatus(nodeMaintenance);

        if (nodeMaintenance.getStatus() != null) {
            List<V1Node> nodeList = this.boundObjectResolver.getAllBoundNodeByNodeMaintenances(nodeMaintenance);
            for (V1alpha1NodeMaintenanceStatusAction action : nodeMaintenance.getStatus().getActions()) {
                if (action.getType().equals(NodeMaintenanceConstants.NN_CORDON) && action.getStatus().equals(NodeMaintenanceConstants.NN_PROGRESS)) {
                    for (V1Node _node : nodeList) {
                        if (_node.getSpec().getUnschedulable() != null && _node.getSpec().getUnschedulable()) {
                            action.setStatus(NodeMaintenanceConstants.NN_COMPLETED);
                        }
                    }
                }
                if (action.getType().equals(NodeMaintenanceConstants.NN_UNCORDON) && action.getStatus().equals(NodeMaintenanceConstants.NN_PROGRESS)) {
                    for (V1Node _node : nodeList) {
                        if (_node.getSpec().getUnschedulable() == null || !_node.getSpec().getUnschedulable()) {
                            action.setStatus(NodeMaintenanceConstants.NN_COMPLETED);
                        }
                    }
                }
                if (action.getType().equals(NodeMaintenanceConstants.NN_DRAIN) && action.getStatus().equals(NodeMaintenanceConstants.NN_PROGRESS)) {
                    for (V1Node _node : nodeList) {
                        boolean isDeleted = isDrainedTargetNode(_node);
                        if (isDeleted) {
                            action.setStatus(NodeMaintenanceConstants.NN_COMPLETED);
                        }
                    }
                }
            }
            this.updateNodeMaintenanceStatus(nodeMaintenance);
        }

        return new Result(true, Duration.ofSeconds(1));
    }

    private boolean isDrainedTargetNode(V1Node node) {
        List<V1alpha1NodeMaintenance> nodeMaintenanceList = this.boundObjectResolver.getAllBoundNodeMaintenances(node);
        List<V1Pod> pods = this.boundObjectResolver.getAllBoundPods(node.getMetadata().getName());
        if (nodeMaintenanceList.isEmpty()) {
            return false;
        }
        int resultCnt = 0;
        for (V1alpha1NodeMaintenance allBoundNodeGroup : nodeMaintenanceList) {
            if (allBoundNodeGroup.getSpec().getTargetNodes().contains(node.getMetadata().getName())) {
                var actions = allBoundNodeGroup.getSpec().getActions();
                for (V1Pod pod : pods) {
                    var ownerReferences = Objects.requireNonNull(pod.getMetadata().getOwnerReferences());
                    boolean isDaemonSet = ownerReferences.stream()
                            .anyMatch(x -> x.getKind().equalsIgnoreCase(NodeMaintenanceConstants.NN_DAEMONSET));
                    for (V1alpha1NodeMaintenanceAction action : actions) {
                        if (action.getType().equals(NodeMaintenanceConstants.NN_DRAIN)) {
                            if (isDaemonSet) {
                                if (action.getIgnoreDaemonSets()) {
                                    resultCnt++;
                                }
                            } else {
                                resultCnt++;
                            }
                        }
                    }
                }
            }
        }
        return resultCnt == 0 ? true : false;
    }

    private void updateNodeMaintenanceStatus(V1alpha1NodeMaintenance nodeMaintenance) throws ApiException {
        Objects.requireNonNull(nodeMaintenance.getStatus());
        this.statusPatchHelper.patchStatus(null, K8sObjectUtils.getName(nodeMaintenance), nodeMaintenance.getStatus());
    }

}
