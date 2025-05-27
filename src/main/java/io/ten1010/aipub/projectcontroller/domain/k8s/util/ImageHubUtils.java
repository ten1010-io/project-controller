package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageHub;

import java.util.Objects;

public abstract class ImageHubUtils {

    public static String getSpecId(V1alpha1ImageHub object) {
        Objects.requireNonNull(object.getSpec());
        Objects.requireNonNull(object.getSpec().getId());

        return object.getSpec().getId();
    }

}
