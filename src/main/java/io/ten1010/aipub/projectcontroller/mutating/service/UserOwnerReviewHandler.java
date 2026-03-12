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
      log.debug("Failed to analyze user info, allowing without mutation", e);
      V1AdmissionReviewUtils.allow(review);
      return;
    }

    if (!analysis.isAipubMember()) {
      V1AdmissionReviewUtils.allow(review);
      return;
    }

    if (analysis.getAipubUser().isEmpty()) {
      V1AdmissionReviewUtils.allow(review);
      return;
    }

    V1alpha1AipubUser aipubUser = analysis.getAipubUser().get();
    V1OwnerReference ownerRef = K8sObjectUtils.buildV1OwnerReference(aipubUser, false, false);

    JsonNode objectNode = request.getObject();
    JsonNode existingRefs = objectNode.path("metadata").path("ownerReferences");

    JsonPatchBuilder jsonPatchBuilder = new JsonPatchBuilder();

    if (existingRefs.isMissingNode() || existingRefs.isNull() || !existingRefs.isArray()
        || existingRefs.isEmpty()) {
      JsonPatchOperation patchOp = new JsonPatchOperationBuilder()
          .add()
          .setPath("/metadata/ownerReferences")
          .setValue(this.mapper.valueToTree(List.of(ownerRef)))
          .build();
      jsonPatchBuilder.addToOperations(patchOp);
    } else {
      List<JsonNode> refList = new ArrayList<>();
      existingRefs.forEach(refList::add);
      refList.add(this.mapper.valueToTree(ownerRef));
      JsonPatchOperation patchOp = new JsonPatchOperationBuilder()
          .replace()
          .setPath("/metadata/ownerReferences")
          .setValue(this.mapper.valueToTree(refList))
          .build();
      jsonPatchBuilder.addToOperations(patchOp);
    }

    V1AdmissionReviewUtils.allow(review, jsonPatchBuilder.build());
  }

}
