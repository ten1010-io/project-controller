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
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ProjectApiConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeMaintenance;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;

@Slf4j
public class NodeMaintenanceReconciler extends AbstractReconciler {

    private final KeyResolver keyResolver;
    private final Indexer<V1alpha1NodeMaintenance> projectIndexer;
    private final CoreV1Api coreV1Api;
    private final CustomObjectsApi customObjectsApi;
    private final String namespace = "project-controller";

    public NodeMaintenanceReconciler(
            ReconciliationService reconciliationService,
            SharedInformerFactory sharedInformerFactory,
            K8sApiProvider k8sApiProvider) {
        this.keyResolver = new KeyResolver();
        this.projectIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1NodeMaintenance.class)
                .getIndexer();
        this.coreV1Api = new CoreV1Api(k8sApiProvider.getApiClient());
        this.customObjectsApi = new CustomObjectsApi(k8sApiProvider.getApiClient());
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
        String nodeMaintenanceName = metadata.getName();
        String targetNodeName = Objects.requireNonNull(spec.getTargetNode());
        var targetNodeRequest = coreV1Api.readNode(targetNodeName);
        var targetNodeResponse = targetNodeRequest.executeWithHttpInfo();
        if (targetNodeResponse.getStatusCode() != 200) {
            return new Result(false);
        }
        V1Node targetNode = targetNodeResponse.getData();
        Objects.requireNonNull(targetNode.getSpec());

        //
        Objects.requireNonNull(action.getType());
        switch (action.getType()) {
            case "cordon":
                //
                executeSchedulable(targetNode, true, nodeMaintenanceName);
                break;
            case "uncordon":
                //
                executeSchedulable(targetNode, false, nodeMaintenanceName);
                break;
            case "drain":
                //
                var podRequest = coreV1Api.listNamespacedPod(namespace);
                var podResponse = podRequest.executeWithHttpInfo();
                if (podResponse.getStatusCode() != 200) {
                    return new Result(false);
                }
                executePodDelete(podResponse, targetNodeName, action.getIgnoreDaemonSets(), nodeMaintenanceName);
                break;
            default:
        }

        return new Result(true);
    }

    private void executePodDelete(ApiResponse<V1PodList> podResponse, String targetNodeName, boolean isIgnoreDaemonSets, String maintenanceName) throws ApiException {
        for (V1Pod _item : podResponse.getData().getItems()) {
            V1PodSpec _spec = Objects.requireNonNull(_item.getSpec());
            if (targetNodeName.equals(_spec.getNodeName())) {
                V1ObjectMeta _metadata = Objects.requireNonNull(_item.getMetadata());
                String podName = _metadata.getName();
                String podNamespace = _metadata.getNamespace();
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
                        coreV1Api.deleteNamespacedPod(podName, podNamespace).execute();
                    }
                } else {
                    log.info("drain all pods : pod name - {} / namespace - {}", podName, podNamespace);
                    coreV1Api.deleteNamespacedPod(podName, podNamespace).execute();
                }
            }
        }

        deleteClusterCO(maintenanceName);
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

        deleteClusterCO(maintenanceName);
    }

    private void deleteClusterCO(String maintenanceName) throws ApiException {
        customObjectsApi.deleteClusterCustomObject(
                ProjectApiConstants.PROJECT_GROUP,
                ProjectApiConstants.VERSION,
                ProjectApiConstants.NODE_MAINTENANCE_RESOURCE_PLURAL,
                maintenanceName
        ).execute();
    }

}
