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
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserOwnerReviewHandler implements ReviewHandler {

  private static final String OPERATION_CREATE = "CREATE";

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
      V1AdmissionReviewUtils.allowMerging(review);
      return;
    }

    UserInfoAnalysis analysis;
    try {
      analysis = this.userInfoAnalyzer.analyzeV2(request.getUserInfo());
    } catch (Exception e) {
      // Python: get_aipub_user non-404 ApiException → 500
      log.warn("Failed to analyze user info", e);
      V1AdmissionReviewUtils.reject(review, 500,
          "Failed to get aipub user with following error. " + e.getMessage());
      return;
    }

    if (!analysis.isAipubMember()) {
      V1AdmissionReviewUtils.allowMerging(review);
      return;
    }

    if (analysis.getAipubUser().isEmpty()) {
      V1AdmissionReviewUtils.reject(review, 400,
          "Not found aipub user: " + analysis.getUsername());
      return;
    }

    V1alpha1AipubUser aipubUser = analysis.getAipubUser().get();
    V1OwnerReference ownerRef = K8sObjectUtils.buildV1OwnerReference(aipubUser, false, false);
    JsonNode ownerRefNode = this.mapper.valueToTree(ownerRef);

    JsonNode objectNode = request.getObject();
    JsonNode existingOwnerRefs = objectNode.path("metadata").path("ownerReferences");

    JsonPatchBuilder jsonPatchBuilder = new JsonPatchBuilder();

    if (!existingOwnerRefs.isArray()) {
      JsonPatchOperation initOwnerRefsOp = new JsonPatchOperationBuilder()
          .add()
          .setPath("/metadata/ownerReferences")
          .setValue(this.mapper.createArrayNode())
          .build();
      jsonPatchBuilder.addToOperations(initOwnerRefsOp);
    }

    JsonPatchOperation appendOwnerRefOp = new JsonPatchOperationBuilder()
        .add()
        .setPath("/metadata/ownerReferences/-")
        .setValue(ownerRefNode)
        .build();
    jsonPatchBuilder.addToOperations(appendOwnerRefOp);

    V1AdmissionReviewUtils.allowMerging(review, jsonPatchBuilder.build());
  }

}
