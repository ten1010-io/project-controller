package io.ten1010.aipub.projectcontroller.controller.cr;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Node;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.BoundObjectResolver;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ProjectApiConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeMaintenance;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeMaintenanceAction;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeMaintenanceStatus;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeMaintenanceStatusAction;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.StatusPatchHelper;
import lombok.extern.slf4j.Slf4j;

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
            List<String> specAction = new ArrayList<>();
            for (V1alpha1NodeMaintenanceAction action : spec.getActions()) {
                specAction.add(action.getType());
            }
            List<String> statusAction = new ArrayList<>();
            List<String> statusNotCompleted = new ArrayList<>();
            for (V1alpha1NodeMaintenanceStatusAction action : status.getActions()) {
                statusAction.add(action.getType());
                if (action.getStatus() == null || !action.getStatus().equals("COMPLETED")) {
                    statusNotCompleted.add("NOT COMPLETED");
                }
            }
            if (specAction.equals(statusAction) && statusNotCompleted.isEmpty()) {
                return new Result(false);
            }
        }

        // NodeMaintenanceStatus 의 action 상태를 PROGRESS 으로 변경한다
        V1alpha1NodeMaintenanceStatus edited = new V1alpha1NodeMaintenanceStatus();
        edited.setAllEffectedNodes(targetNodeNames);
        List<V1alpha1NodeMaintenanceStatusAction> actionList = new ArrayList<>();
        for (V1alpha1NodeMaintenanceAction action : nodeMaintenance.getSpec().getActions()) {
            V1alpha1NodeMaintenanceStatusAction _action = new V1alpha1NodeMaintenanceStatusAction();
            _action.setType(action.getType());
            _action.setStatus("PROGRESS");
            actionList.add(_action);
        }
        edited.setActions(actionList);
        nodeMaintenance.setStatus(edited);
        System.out.println("edited progress = " + edited);
        this.updateNodeMaintenanceStatus(nodeMaintenance);

        // target Node 의 상태를 확인해서 COMPLETED 으로 변경한다.
        if (nodeMaintenance.getStatus() != null) {
            List<V1Node> nodeList = this.boundObjectResolver.getAllBoundNodeByNodeMaintenances(nodeMaintenance);
            for (V1alpha1NodeMaintenanceStatusAction action : nodeMaintenance.getStatus().getActions()) {
                if (action.getStatus().equals("COMPLETED")) {
                    break;
                }
                for (V1Node _node : nodeList) {
                    switch (action.getType()) {
                        case "cordon" -> {
                            if (_node.getSpec().getUnschedulable() != null && _node.getSpec().getUnschedulable()) {
                                action.setStatus("COMPLETED");
                            }
                        }
                        case "uncordon" -> {
                            if (_node.getSpec().getUnschedulable() != null && !_node.getSpec().getUnschedulable()) {
                                action.setStatus("COMPLETED");
                            }
                        }
                        case "drain" -> {
                            boolean isDeleted = this.boundObjectResolver.isDrainedTargetNode(_node);
                            if (isDeleted) {
                                action.setStatus("COMPLETED");
                            }
                        }
                    }
                }
            }
            this.updateNodeMaintenanceStatus(nodeMaintenance);
        }

        return new Result(false);
    }

    private void updateNodeMaintenanceStatus(V1alpha1NodeMaintenance nodeMaintenance) throws ApiException {
        Objects.requireNonNull(nodeMaintenance.getStatus());
        this.statusPatchHelper.patchStatus(null, K8sObjectUtils.getName(nodeMaintenance), nodeMaintenance.getStatus());
    }

}
