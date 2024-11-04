package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1Taint;
import io.ten1010.aipub.projectcontroller.domain.k8s.IsolationModeValueEnum;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.ProjectManagedValueEnum;

import java.util.List;
import java.util.Map;

public abstract class NodeUtils {

    public static List<V1Taint> getTaints(V1Node object) {
        if (object.getSpec() == null) {
            return List.of();
        }
        if (object.getSpec().getTaints() == null) {
            return List.of();
        }
        return object.getSpec().getTaints();
    }

    public static boolean isProjectManaged(V1Node node) {
        Map<String, String> labels = K8sObjectUtils.getLabels(node);
        String value = labels.get(LabelConstants.PROJECT_MANAGED_KEY);
        if (value == null) {
            return false;
        }
        return value.equalsIgnoreCase(ProjectManagedValueEnum.TRUE.getStr());
    }

    public static boolean isStrictIsolationMode(V1Node node) {
        Map<String, String> labels = K8sObjectUtils.getLabels(node);
        String value = labels.get(LabelConstants.ISOLATION_MODE_KEY);
        if (value == null) {
            return false;
        }
        return value.equalsIgnoreCase(IsolationModeValueEnum.STRICT.getStr());
    }

    public static List<V1Node> getProjectManagedNodes(List<V1Node> nodes) {
        return nodes.stream()
                .filter(NodeUtils::isProjectManaged)
                .toList();
    }

}
