package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageNamespace;

import java.util.Objects;

public abstract class ImageNamespaceUtils {

    public static String getSpecId(V1alpha1ImageNamespace object) {
        Objects.requireNonNull(object.getSpec());
        Objects.requireNonNull(object.getSpec().getId());

        return object.getSpec().getId();
    }

}
