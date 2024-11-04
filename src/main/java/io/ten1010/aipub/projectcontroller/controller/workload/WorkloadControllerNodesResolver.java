package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1Node;

import java.util.List;

public interface WorkloadControllerNodesResolver {

    List<V1Node> getNodes(KubernetesObject workloadController);

}
