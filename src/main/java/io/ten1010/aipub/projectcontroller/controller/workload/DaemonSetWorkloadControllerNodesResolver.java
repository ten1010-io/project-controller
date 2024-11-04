package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Node;
import io.ten1010.aipub.projectcontroller.controller.BoundObjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroup;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.NodeUtils;
import io.ten1010.aipub.projectcontroller.informer.IndexerConstants;

import java.util.ArrayList;
import java.util.List;

public class DaemonSetWorkloadControllerNodesResolver implements WorkloadControllerNodesResolver {

    private final KeyResolver keyResolver;
    private final Indexer<V1alpha1Project> projectIndexer;
    private final Indexer<V1alpha1NodeGroup> nodeGroupIndexer;
    private final BoundObjectResolver boundObjectResolver;

    public DaemonSetWorkloadControllerNodesResolver(SharedInformerFactory sharedInformerFactory) {
        this.keyResolver = new KeyResolver();
        this.projectIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1Project.class)
                .getIndexer();
        this.nodeGroupIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1NodeGroup.class)
                .getIndexer();
        this.boundObjectResolver = new BoundObjectResolver(sharedInformerFactory);
    }

    @Override
    public List<V1Node> getNodes(KubernetesObject workloadController) {
        String projKey = this.keyResolver.resolveKey(K8sObjectUtils.getNamespace(workloadController));
        V1alpha1Project project = this.projectIndexer.getByKey(projKey);
        List<V1Node> nodes = new ArrayList<>();
        if (project != null) {
            nodes.addAll(this.boundObjectResolver.getAllBoundNodes(project));
        }

        List<V1alpha1NodeGroup> allowedNodeGroups = new ArrayList<>();
        List<V1alpha1NodeGroup> nodeGroupThatAllowAllDaemonSets = this.nodeGroupIndexer.byIndex(
                IndexerConstants.ALLOW_ALL_DAEMON_SETS_TO_NODE_GROUPS_INDEXER_NAME,
                IndexerConstants.TRUE_VALUE);
        List<V1alpha1NodeGroup> nodeGroupThatAllowNamespaces = this.nodeGroupIndexer.byIndex(
                IndexerConstants.ALLOWED_NAMESPACE_TO_NODE_GROUPS_INDEXER_NAME,
                K8sObjectUtils.getNamespace(workloadController));
        List<V1alpha1NodeGroup> nodeGroupThatAllowDaemonSets = this.nodeGroupIndexer.byIndex(
                IndexerConstants.ALLOWED_DAEMON_SET_TO_NODE_GROUPS_INDEXER_NAME,
                this.keyResolver.resolveKey(
                        K8sObjectUtils.getNamespace(workloadController),
                        K8sObjectUtils.getName(workloadController)));
        allowedNodeGroups.addAll(nodeGroupThatAllowAllDaemonSets);
        allowedNodeGroups.addAll(nodeGroupThatAllowNamespaces);
        allowedNodeGroups.addAll(nodeGroupThatAllowDaemonSets);
        List<V1Node> nodesFromDaemonSetPolicy = allowedNodeGroups.stream()
                .flatMap(e -> this.boundObjectResolver.getAllBoundNodes(e).stream())
                .toList();
        nodes.addAll(nodesFromDaemonSetPolicy);
        nodes = NodeUtils.getProjectManagedNodes(nodes);

        return K8sObjectUtils.distinctByKey(this.keyResolver, nodes);
    }

}
