package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Node;
import io.ten1010.aipub.projectcontroller.controller.BoundObjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.NodeUtils;

import java.util.List;

public class DefaultWorkloadControllerNodesResolver implements WorkloadControllerNodesResolver {

    private final KeyResolver keyResolver;
    private final Indexer<V1alpha1Project> projectIndexer;
    private final BoundObjectResolver boundObjectResolver;

    public DefaultWorkloadControllerNodesResolver(SharedInformerFactory sharedInformerFactory) {
        this.keyResolver = new KeyResolver();
        this.projectIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1Project.class)
                .getIndexer();
        this.boundObjectResolver = new BoundObjectResolver(sharedInformerFactory);
    }

    @Override
    public List<V1Node> getNodes(KubernetesObject workloadController) {
        String projKey = this.keyResolver.resolveKey(K8sObjectUtils.getNamespace(workloadController));
        V1alpha1Project project = this.projectIndexer.getByKey(projKey);
        if (project == null) {
            return List.of();
        }
        return NodeUtils.getProjectManagedNodes(this.boundObjectResolver.getAllBoundNodes(project));
    }

}
