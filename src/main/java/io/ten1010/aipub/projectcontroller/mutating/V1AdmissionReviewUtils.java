package io.ten1010.aipub.projectcontroller.mutating;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.JsonPatchHelper;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewResponse;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1Status;
import io.ten1010.common.jsonpatch.dto.JsonPatch;

import java.util.Objects;

public abstract class V1AdmissionReviewUtils {

    private static final ObjectMapper MAPPER = new ObjectMapperFactory().createObjectMapper();

    public static V1AdmissionReview clone(V1AdmissionReview review) {
        V1AdmissionReview clone = new V1AdmissionReview();
        clone.setApiVersion(review.getApiVersion());
        clone.setKind(review.getKind());
        clone.setRequest(review.getRequest());
        clone.setResponse(review.getResponse());

        return clone;
    }

    public static void allow(V1AdmissionReview review) {
        Objects.requireNonNull(review.getRequest());

        V1AdmissionReviewResponse response = new V1AdmissionReviewResponse();
        response.setUid(review.getRequest().getUid());
        response.setAllowed(true);

        review.setResponse(response);
    }

    public static void allow(V1AdmissionReview review, JsonPatch jsonPatch) {
        allow(review);

        String patch = new JsonPatchHelper(MAPPER).buildPatchString(jsonPatch);

        Objects.requireNonNull(review.getResponse());
        review.getResponse().setPatchType("JSONPatch");
        review.getResponse().setPatch(patch);
    }

    public static void reject(V1AdmissionReview review, int code, String reason) {
        Objects.requireNonNull(review.getRequest());

        V1Status status = new V1Status();
        status.setCode(code);
        status.setMessage(reason);

        V1AdmissionReviewResponse response = new V1AdmissionReviewResponse();
        response.setUid(review.getRequest().getUid());
        response.setAllowed(false);
        response.setStatus(status);

        review.setResponse(response);
    }

}
