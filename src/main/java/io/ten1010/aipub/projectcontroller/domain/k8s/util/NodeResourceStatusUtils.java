package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import io.kubernetes.client.openapi.models.V1Node;

public class NodeResourceStatusUtils {

    public static String getName(V1Node object) {
        return K8sObjectUtils.getName(object);
    }

}
