package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageReview;

import java.util.Optional;

public abstract class ImageReviewUtils {

    public static Optional<String> getImgNS(V1alpha1ImageReview object) {
        if (object.getSpec() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(object.getSpec().getImgNS());
    }

    public static Optional<String> getRepo(V1alpha1ImageReview object) {
        if (object.getSpec() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(object.getSpec().getRepo());
    }

}
