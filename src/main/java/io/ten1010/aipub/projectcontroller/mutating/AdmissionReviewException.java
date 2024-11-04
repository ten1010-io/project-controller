package io.ten1010.aipub.projectcontroller.mutating;

import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import lombok.Getter;

@Getter
public class AdmissionReviewException extends RuntimeException {

    private final V1AdmissionReview review;

    public AdmissionReviewException(Exception cause, V1AdmissionReview review) {
        super(cause);
        this.review = review;
    }

    @Override
    public synchronized Exception getCause() {
        return (Exception) super.getCause();
    }

}
