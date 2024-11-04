package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1Node;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeKey;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
public class CompositeWorkloadControllerNodesResolver implements WorkloadControllerNodesResolver {

    private final Map<Class<? extends KubernetesObject>, WorkloadControllerNodesResolver> resolvers;

    @Override
    public List<V1Node> getNodes(KubernetesObject workloadController) {
        WorkloadControllerNodesResolver resolver = this.resolvers.get(workloadController.getClass());
        if (resolver == null) {
            throw new UnsupportedControllerException(K8sObjectTypeKey.of(workloadController));
        }
        return resolver.getNodes(workloadController);
    }

}
