package io.ten1010.aipub.projectcontroller.controller.cr;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.ApiResponse;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ProjectApiConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeMaintenance;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeMaintenanceAction;
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
    private final CustomObjectsApi customObjectsApi;
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
        this.customObjectsApi = new CustomObjectsApi(k8sApiProvider.getApiClient());
        // todo NodeMaintenance.setStatus 를 하지 않는다면, statusPatchHelper는 필요없음
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

        //
        var nodeMaintenance = nodeMaintenanceOpt.get();
        var metadata = Objects.requireNonNull(nodeMaintenance.getMetadata());
        var spec = Objects.requireNonNull(nodeMaintenance.getSpec());
        var action = Objects.requireNonNull(spec.getAction());
        var actionType = Objects.requireNonNull(action.getType());
        String nodeMaintenanceName = Objects.requireNonNull(metadata.getName());
        List<String> targetNodeNames = Objects.requireNonNull(spec.getTargetNodes());

        List<String> effectedNodes = new ArrayList<>();
        int countPod = 0;
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
                    // todo
                    // countPod = countPod(podResponse, targetNodeName);
                    break;
                default:
            }
        }

        //
        deleteClusterCustomObject(nodeMaintenanceName);

        // todo
//        V1alpha1NodeMaintenanceStatus status = new V1alpha1NodeMaintenanceStatus();
//        status.setAllEffectedNodes(targetNodeNames);
//        status.setUntilCount(countPod);
//        nodeMaintenance.setStatus(status);
//        this.statusPatchHelper.patchStatus(null, K8sObjectUtils.getName(nodeMaintenance), nodeMaintenance.getStatus());

        return new Result(true);
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
                                .executeAsync(null);
                    }
                } else {
                    log.info("drain all pods : pod name - {} / namespace - {}", podName, podNamespace);
                    coreV1Api.deleteNamespacedPod(podName, podNamespace)
                            .gracePeriodSeconds(gracePeriodSeconds)
                            .executeAsync(null);
                }
            }
        }
    }

    private int countPod(ApiResponse<V1PodList> podResponse, String targetNodeName) {
        int count = 0;
        for (V1Pod _item : podResponse.getData().getItems()) {
            if (targetNodeName.equals(Objects.requireNonNull(_item.getSpec()).getNodeName())) {
                var ownerReferences = Objects.requireNonNull(Objects.requireNonNull(_item.getMetadata()).getOwnerReferences());
                boolean isDaemonset = false;
                for (V1OwnerReference ownerReference : ownerReferences) {
                    if (ownerReference.getKind().equalsIgnoreCase("DaemonSet")) {
                        isDaemonset = true;
                        break;
                    }
                }
                if (!isDaemonset) {
                    count++;
                }
            }
        }

        return count;
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

    private void deleteClusterCustomObject(String maintenanceName) throws ApiException {
        customObjectsApi.deleteClusterCustomObject(
                ProjectApiConstants.PROJECT_GROUP,
                ProjectApiConstants.VERSION,
                ProjectApiConstants.NODE_MAINTENANCE_RESOURCE_PLURAL,
                maintenanceName
        ).execute();
    }

}
