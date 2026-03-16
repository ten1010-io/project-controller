package io.ten1010.aipub.projectcontroller.mutating.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.mutating.V1AdmissionReviewUtils;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import io.ten1010.common.jsonpatch.JsonPatchBuilder;
import io.ten1010.common.jsonpatch.JsonPatchOperationBuilder;
import io.ten1010.common.jsonpatch.dto.JsonPatchOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserOwnerReviewHandler implements ReviewHandler {

  private static final String OPERATION_CREATE = "CREATE";

  // v2 테스트용: ownerReference 대신 annotation으로 기록. 정식 전환 시 ownerReference 방식으로 복원.
  private static final String OWNER_REF_ANNOTATION_KEY_V2 =
      "aipub.ten1010.io/owner-reference-v2";

  private final UserInfoAnalyzer userInfoAnalyzer;
  private final Set<String> exceptGvkSet;
  private final ObjectMapper mapper;

  public UserOwnerReviewHandler(UserInfoAnalyzer userInfoAnalyzer, Set<String> exceptGvkSet) {
    this.userInfoAnalyzer = userInfoAnalyzer;
    this.exceptGvkSet = exceptGvkSet;
    this.mapper = new ObjectMapperFactory().createObjectMapper();
  }

  @Override
  public boolean canHandle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());

    V1AdmissionReviewRequest request = review.getRequest();
    if (!OPERATION_CREATE.equals(request.getOperation())) {
      return false;
    }
    return request.getNamespace() != null && !request.getNamespace().isEmpty();
  }

  @Override
  public void handle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());

    V1AdmissionReviewRequest request = review.getRequest();
    Objects.requireNonNull(request.getKind());
    Objects.requireNonNull(request.getKind().getGroup());
    Objects.requireNonNull(request.getKind().getVersion());
    Objects.requireNonNull(request.getKind().getKind());
    Objects.requireNonNull(request.getUserInfo());
    Objects.requireNonNull(request.getObject());

    String gvk = request.getKind().getGroup() + "/"
        + request.getKind().getVersion() + "/"
        + request.getKind().getKind();
    if (this.exceptGvkSet.contains(gvk)) {
      V1AdmissionReviewUtils.allow(review);
      return;
    }

    UserInfoAnalysis analysis;
    try {
      analysis = this.userInfoAnalyzer.analyze(request.getUserInfo());
    } catch (Exception e) {
      // Python: get_aipub_user non-404 ApiException → 500
      log.warn("Failed to analyze user info", e);
      V1AdmissionReviewUtils.reject(review, 500,
          "Failed to get aipub user with following error. " + e.getMessage());
      return;
    }

    if (!analysis.isAipubMember()) {
      V1AdmissionReviewUtils.allow(review);
      return;
    }

    if (analysis.getAipubUser().isEmpty()) {
      V1AdmissionReviewUtils.reject(review, 400,
          "Not found aipub user: " + analysis.getUsername());
      return;
    }

    // v2 테스트용: ownerReference를 annotation JSON으로 기록하여 Python 원본과 비교.
    //  정식 전환 시 이 블록을 ownerReference 패치 방식으로 복원할 것.
    V1alpha1AipubUser aipubUser = analysis.getAipubUser().get();
    V1OwnerReference ownerRef = K8sObjectUtils.buildV1OwnerReference(aipubUser, false, false);
    String ownerRefJson = this.mapper.valueToTree(ownerRef).toString();

    JsonNode objectNode = request.getObject();
    JsonNode existingAnnotations = objectNode.path("metadata").path("annotations");

    JsonPatchBuilder jsonPatchBuilder = new JsonPatchBuilder();

    if (!existingAnnotations.isObject()) {
      JsonPatchOperation initAnnotationsOp = new JsonPatchOperationBuilder()
          .add()
          .setPath("/metadata/annotations")
          .setValue(this.mapper.createObjectNode())
          .build();
      jsonPatchBuilder.addToOperations(initAnnotationsOp);
    }

    String annotationPath = "/metadata/annotations/"
        + OWNER_REF_ANNOTATION_KEY_V2.replace("/", "~1");
    JsonPatchOperation patchOp = new JsonPatchOperationBuilder()
        .add()
        .setPath(annotationPath)
        .setValue(this.mapper.getNodeFactory().textNode(ownerRefJson))
        .build();
    jsonPatchBuilder.addToOperations(patchOp);

    V1AdmissionReviewUtils.allow(review, jsonPatchBuilder.build());
  }

}
