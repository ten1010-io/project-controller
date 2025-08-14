package io.ten1010.aipub.projectcontroller.controller.cr;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.ApiResponse;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ProjectApiConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeMaintenance;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeMaintenanceAction;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeMaintenanceStatus;
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
    private final CoreV1Api coreV1Api;
    private final StatusPatchHelper<V1alpha1NodeMaintenance> statusPatchHelper;
    private final String namespace = "project-controller";

    public NodeMaintenanceReconciler(
            SharedInformerFactory sharedInformerFactory,
            K8sApiProvider k8sApiProvider) {
        this.keyResolver = new KeyResolver();
        this.projectIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1NodeMaintenance.class)
                .getIndexer();
        this.coreV1Api = new CoreV1Api(k8sApiProvider.getApiClient());
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
        var action = Objects.requireNonNull(spec.getAction());
        var actionType = Objects.requireNonNull(action.getType());
        String nodeMaintenanceName = K8sObjectUtils.getName(nodeMaintenance);
        List<String> targetNodeNames = Objects.requireNonNull(spec.getTargetNodes());

        if (status != null && (
                targetNodeNames.equals(status.getAllEffectedNodes()) && action.equals(status.getAction())
        )) {
            return new Result(false);
        }

        List<String> effectedNodes = new ArrayList<>();
        for (String targetNodeName : targetNodeNames) {
            var targetNodeRequest = coreV1Api.readNode(targetNodeName);
            var targetNodeResponse = targetNodeRequest.executeWithHttpInfo();
            if (targetNodeResponse.getStatusCode() != 200) {
                return new Result(false);
            }
            V1Node targetNode = targetNodeResponse.getData();
            Objects.requireNonNull(targetNode.getSpec());

            //
            switch (actionType) {
                case "cordon":
                    //
                    executeSchedulable(targetNode, true, nodeMaintenanceName);
                    effectedNodes.add(targetNodeName);
                    break;
                case "uncordon":
                    //
                    executeSchedulable(targetNode, false, nodeMaintenanceName);
                    effectedNodes.add(targetNodeName);
                    break;
                case "drain":
                    //
                    if (targetNode.getSpec().getUnschedulable() == null || Boolean.FALSE.equals(targetNode.getSpec().getUnschedulable())) {
                        log.error("Node {} isn't cordon state", nodeMaintenanceName);
                        return new Result(false);
                    }

                    var podRequest = coreV1Api.listNamespacedPod(namespace);
                    var podResponse = podRequest.executeWithHttpInfo();
                    if (podResponse.getStatusCode() != 200) {
                        return new Result(false);
                    }
                    executePodDelete(podResponse, targetNodeName, action, nodeMaintenanceName);
                    effectedNodes.add(targetNodeName);
                    break;
                default:
            }
        }

        //
        V1alpha1NodeMaintenanceStatus edited = new V1alpha1NodeMaintenanceStatus();
        edited.setAllEffectedNodes(targetNodeNames);
        edited.setAction(action);
        edited.setStatus("COMPLETED");
        nodeMaintenance.setStatus(edited);

        this.updateNodeMaintenanceStatus(nodeMaintenance);

        return new Result(false);
    }

    private void executePodDelete(ApiResponse<V1PodList> podResponse, String targetNodeName, V1alpha1NodeMaintenanceAction action, String maintenanceName) throws ApiException {
        boolean isIgnoreDaemonSets = Boolean.TRUE.equals(action.getIgnoreDaemonSets());
        boolean isForce = Boolean.TRUE.equals(action.getForce());
        for (V1Pod _item : podResponse.getData().getItems()) {
            V1PodSpec _spec = Objects.requireNonNull(_item.getSpec());
            if (targetNodeName.equals(_spec.getNodeName())) {
                V1ObjectMeta _metadata = Objects.requireNonNull(_item.getMetadata());
                String podName = _metadata.getName();
                String podNamespace = _metadata.getNamespace();
                int gracePeriodSeconds = isForce ? 0 : Objects.requireNonNull(_spec.getTerminationGracePeriodSeconds()).intValue();

                if (isIgnoreDaemonSets) {
                    var ownerReferences = Objects.requireNonNull(_metadata.getOwnerReferences());
                    boolean isDaemonset = false;
                    for (V1OwnerReference ownerReference : ownerReferences) {
                        if (ownerReference.getKind().equalsIgnoreCase("DaemonSet")) {
                            isDaemonset = true;
                            break;
                        }
                    }
                    if (!isDaemonset) {
                        log.info("drain daemonSet ignore : pod name - {} / namespace - {}", podName, podNamespace);
                        coreV1Api.deleteNamespacedPod(podName, podNamespace)
                                .gracePeriodSeconds(gracePeriodSeconds)
                                .execute();
                    }
                } else {
                    log.info("drain all pods : pod name - {} / namespace - {}", podName, podNamespace);
                    coreV1Api.deleteNamespacedPod(podName, podNamespace)
                            .gracePeriodSeconds(gracePeriodSeconds)
                            .execute();
                }
            }
        }
    }

    private void executeSchedulable(V1Node targetNode, boolean isUnschedulable, String maintenanceName) throws ApiException {
        String nodeName = targetNode.getMetadata().getName();
        targetNode.getSpec().setUnschedulable(isUnschedulable);

        if (isUnschedulable) {
            log.info("cordon : node name - {} / yaml - {}", nodeName, maintenanceName);
        } else {
            log.info("uncordon : node name - {} / yaml - {}", nodeName, maintenanceName);
        }
        coreV1Api.replaceNode(nodeName, targetNode).execute();
    }

    private void updateNodeMaintenanceStatus(V1alpha1NodeMaintenance nodeMaintenance) throws ApiException {
        Objects.requireNonNull(nodeMaintenance.getStatus());
        this.statusPatchHelper.patchStatus(null, K8sObjectUtils.getName(nodeMaintenance), nodeMaintenance.getStatus());
    }

}
