package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroup;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroupDaemonSetPolicy;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ObjectReference;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class NodeGroupUtils {

    public static Optional<V1alpha1NodeGroupDaemonSetPolicy> getSpecDaemonSetPolicy(V1alpha1NodeGroup object) {
        if (object.getSpec() == null ||
                object.getSpec().getPolicy() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(object.getSpec().getPolicy().getDaemonSet());
    }

    public static boolean getSpecAllowAllDaemonSets(V1alpha1NodeGroup object) {
        if (object.getSpec() == null ||
                object.getSpec().getPolicy() == null ||
                object.getSpec().getPolicy().getDaemonSet() == null ||
                object.getSpec().getPolicy().getDaemonSet().getAllowAllDaemonSets() == null) {
            return false;
        }
        return object.getSpec().getPolicy().getDaemonSet().getAllowAllDaemonSets();
    }

    public static List<String> getSpecAllowedNamespaces(V1alpha1NodeGroup object) {
        if (object.getSpec() == null ||
                object.getSpec().getPolicy() == null ||
                object.getSpec().getPolicy().getDaemonSet() == null ||
                object.getSpec().getPolicy().getDaemonSet().getAllowedNamespaces() == null) {
            return List.of();
        }
        return object.getSpec().getPolicy().getDaemonSet().getAllowedNamespaces();
    }

    public static List<V1alpha1ObjectReference> getSpecAllowedDaemonSets(V1alpha1NodeGroup object) {
        if (object.getSpec() == null ||
                object.getSpec().getPolicy() == null ||
                object.getSpec().getPolicy().getDaemonSet() == null ||
                object.getSpec().getPolicy().getDaemonSet().getAllowedDaemonSets() == null) {
            return List.of();
        }
        return object.getSpec().getPolicy().getDaemonSet().getAllowedDaemonSets();
    }

    public static boolean isNodeSelectorEnabled(V1alpha1NodeGroup object) {
        if (object.getSpec() == null ||
                object.getSpec().getEnableNodeSelector() == null) {
            return false;
        }
        return object.getSpec().getEnableNodeSelector();
    }

    public static Map<String, String> getSpecNodeSelector(V1alpha1NodeGroup object) {
        if (object.getSpec() == null ||
                object.getSpec().getNodeSelector() == null) {
            return Map.of();
        }
        return object.getSpec().getNodeSelector();
    }

    public static List<String> getSpecNodes(V1alpha1NodeGroup object) {
        if (object.getSpec() == null ||
                object.getSpec().getNodes() == null) {
            return List.of();
        }
        return object.getSpec().getNodes();
    }

}
