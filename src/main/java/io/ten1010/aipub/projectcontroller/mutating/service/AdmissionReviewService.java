package io.ten1010.aipub.projectcontroller.mutating.service;

import io.ten1010.aipub.projectcontroller.mutating.AdmissionReviewConstants;
import io.ten1010.aipub.projectcontroller.mutating.IllegalPropertyException;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class AdmissionReviewService {

    private static void doCommonValidation(V1AdmissionReview review) {
        V1AdmissionReviewRequest request = review.getRequest();
        if (request == null) {
            throw IllegalPropertyException.createNullError(AdmissionReviewConstants.REVIEW_OBJECT_NAME, "/request");
        }

        if (request.getKind() == null) {
            throw IllegalPropertyException.createNullError(AdmissionReviewConstants.REVIEW_OBJECT_NAME, "/request/kind");
        }
        if (request.getKind().getGroup() == null) {
            throw IllegalPropertyException.createNullError(AdmissionReviewConstants.REVIEW_OBJECT_NAME, "/request/kind/group");
        }
        if (request.getKind().getVersion() == null) {
            throw IllegalPropertyException.createNullError(AdmissionReviewConstants.REVIEW_OBJECT_NAME, "/request/kind/version");
        }
        if (request.getKind().getKind() == null) {
            throw IllegalPropertyException.createNullError(AdmissionReviewConstants.REVIEW_OBJECT_NAME, "/request/kind/kind");
        }

        if (request.getUserInfo() == null) {
            throw IllegalPropertyException.createNullError(AdmissionReviewConstants.REVIEW_OBJECT_NAME, "/request/userInfo");
        }
        if (request.getUserInfo().getUsername() == null) {
            throw IllegalPropertyException.createNullError(AdmissionReviewConstants.REVIEW_OBJECT_NAME, "/request/userInfo/username");
        }
        if (request.getUserInfo().getGroups() == null) {
            throw IllegalPropertyException.createNullError(AdmissionReviewConstants.REVIEW_OBJECT_NAME, "/request/userInfo/groups");
        }

        if (request.getObject() == null) {
            throw IllegalPropertyException.createNullError(AdmissionReviewConstants.REVIEW_OBJECT_NAME, "/request/object");
        }

        if (request.getUid() == null) {
            throw IllegalPropertyException.createNullError(AdmissionReviewConstants.REVIEW_OBJECT_NAME, "/request/uid");
        }
    }

    private final ReviewHandler reviewHandler;

    public void review(V1AdmissionReview review) {
        doCommonValidation(review);

        if (this.reviewHandler.canHandle(review)) {
            this.reviewHandler.handle(review);
            return;
        }
        throw new IllegalPropertyException(AdmissionReviewConstants.REVIEW_OBJECT_NAME, "", "Not supported");
    }

}
