package io.ten1010.aipub.projectcontroller.mutating.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.ten1010.aipub.projectcontroller.mutating.V1AdmissionReviewUtils;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Port of Python validate/user_authority_review.py (UserAuthorityReviewValidateService).
 * Validates that the mutate handler ran successfully by checking the dummy ownerReference.
 */
@Slf4j
public class UserAuthorityReviewValidateHandler implements ReviewHandler {

  private static final String OPERATION_CREATE = "CREATE";
  private static final String KIND = "UserAuthorityReview";

  @Override
  public boolean canHandle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());

    V1AdmissionReviewRequest request = review.getRequest();
    if (!OPERATION_CREATE.equals(request.getOperation())) {
      return false;
    }
    return request.getKind() != null && KIND.equals(request.getKind().getKind());
  }

  @Override
  public void handle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());
    Objects.requireNonNull(review.getRequest().getObject());

    JsonNode object = review.getRequest().getObject();
    JsonNode ownerRefs = object.path("metadata").path("ownerReferences");

    if (!ownerRefs.isArray()) {
      log.debug("UserAuthorityReview validate: ownerReferences not found");
      V1AdmissionReviewUtils.reject(review, 400,
          "Not found owner_references for user authority review");
      return;
    }

    if (ownerRefs.size() != 1) {
      log.debug("UserAuthorityReview validate: invalid ownerReferences size={}", ownerRefs.size());
      V1AdmissionReviewUtils.reject(review, 400, "Invalid owner_references");
      return;
    }

    String name = ownerRefs.get(0).path("name").asText("");
    if (!"dummy".equals(name)) {
      log.debug("UserAuthorityReview validate: invalid ownerReferences name={}", name);
      V1AdmissionReviewUtils.reject(review, 400, "Invalid owner_references");
      return;
    }

    V1AdmissionReviewUtils.allow(review);
  }

}
