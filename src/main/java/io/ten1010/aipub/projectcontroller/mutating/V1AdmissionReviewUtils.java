package io.ten1010.aipub.projectcontroller.mutating;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.JsonPatchHelper;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewResponse;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1Kind;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1Status;
import io.ten1010.common.jsonpatch.dto.JsonPatch;
import io.ten1010.common.jsonpatch.dto.JsonPatchOperation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

public abstract class V1AdmissionReviewUtils {

  private static final ObjectMapper MAPPER = new ObjectMapperFactory().createObjectMapper();

  /**
   * 요청 대상이 core/v1 Namespace 인지 판별한다. Namespace CREATE 요청은
   * {@code request.namespace}가 오브젝트 자신의 이름으로 채워지므로 namespace 필드 유무만으로는
   * cluster-scoped 여부를 구분할 수 없다 — kind 로 판별해야 한다.
   */
  public static boolean isNamespaceRequest(V1AdmissionReviewRequest request) {
    V1Kind kind = request.getKind();
    if (kind == null) {
      return false;
    }
    String group = kind.getGroup();
    return (group == null || group.isEmpty()) && "Namespace".equals(kind.getKind());
  }

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

  public static void allowMerging(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());

    if (review.getResponse() == null) {
      V1AdmissionReviewResponse response = new V1AdmissionReviewResponse();
      response.setUid(review.getRequest().getUid());
      response.setAllowed(true);
      review.setResponse(response);
    }
  }

  public static void allowMerging(V1AdmissionReview review, JsonPatch jsonPatch) {
    allowMerging(review);

    Objects.requireNonNull(review.getResponse());

    List<JsonPatchOperation> allOps = new ArrayList<>(jsonPatch.getOperations());
    String existingPatchStr = review.getResponse().getPatch();
    if (existingPatchStr != null) {
      try {
        byte[] decoded = Base64.getDecoder().decode(existingPatchStr);
        JsonPatch existingPatch = MAPPER.readValue(decoded, JsonPatch.class);
        allOps.addAll(0, existingPatch.getOperations());
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    JsonPatch mergedPatch = new JsonPatch();
    mergedPatch.setOperations(allOps);

    String patch = new JsonPatchHelper(MAPPER).buildPatchString(mergedPatch);
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
