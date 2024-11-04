package io.ten1010.aipub.projectcontroller.mutating.service;

import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;

public interface ReviewHandler {

    void handle(V1AdmissionReview review);

    boolean canHandle(V1AdmissionReview review);

}
