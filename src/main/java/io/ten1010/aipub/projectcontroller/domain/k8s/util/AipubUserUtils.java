package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;

import java.util.List;

public abstract class AipubUserUtils {

    public static List<String> getAllBoundImageNamespaces(V1alpha1AipubUser object) {
        if (object.getStatus() == null ||
                object.getStatus().getAllBoundImageNamespaces() == null) {
            return List.of();
        }
        return object.getStatus().getAllBoundImageNamespaces();
    }

    public static List<String> getAllBoundProjects(V1alpha1AipubUser object) {
        if (object.getStatus() == null ||
                object.getStatus().getAllBoundProjects() == null) {
            return List.of();
        }
        return object.getStatus().getAllBoundProjects();
    }

}
