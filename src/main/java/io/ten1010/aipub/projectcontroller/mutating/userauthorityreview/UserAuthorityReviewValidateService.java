package io.ten1010.aipub.projectcontroller.mutating.userauthorityreview;

import com.fasterxml.jackson.databind.JsonNode;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;

import java.util.Objects;

public class UserAuthorityReviewValidateService {

    static final String REVIEW_KIND = "UserAuthorityReview";

    public ValidateResult validate(V1AdmissionReviewRequest request) {
        if (!"CREATE".equals(request.getOperation())) {
            return ValidateResult.skip();
        }
        Objects.requireNonNull(request.getKind());
        if (!REVIEW_KIND.equals(request.getKind().getKind())) {
            return ValidateResult.skip();
        }

        Objects.requireNonNull(request.getObject());
        JsonNode ownerRefs = request.getObject().path("metadata").path("ownerReferences");

        if (ownerRefs.isMissingNode() || ownerRefs.isNull()) {
            return ValidateResult.rejected(400,
                    "Not found owner_references for user authority review");
        }
        if (ownerRefs.size() != 1) {
            return ValidateResult.rejected(400, "Invalid owner_references");
        }
        if (!"dummy".equals(ownerRefs.get(0).path("name").asText())) {
            return ValidateResult.rejected(400, "Invalid owner_references");
        }

        return ValidateResult.allowed();
    }

}
