package io.ten1010.aipub.projectcontroller.mutating.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.mutating.V1AdmissionReviewUtils;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1UserInfo;
import io.ten1010.common.jsonpatch.JsonPatchBuilder;
import io.ten1010.common.jsonpatch.JsonPatchOperationBuilder;
import io.ten1010.common.jsonpatch.dto.JsonPatchOperation;
import java.util.Objects;

public class UserLabelReviewHandlerV2 implements ReviewHandler {

  private static final String USERNAME_LABEL_PATH =
      "/metadata/labels/" + LabelConstants.OBJECT_OWN_USERNAME_KEY.replace("/", "~1");
  private static final String USERID_LABEL_PATH =
      "/metadata/labels/" + LabelConstants.OBJECT_OWN_USERID_KEY.replace("/", "~1");

  private final UserInfoAnalyzer userInfoAnalyzer;
  private final ObjectMapper mapper;

  public UserLabelReviewHandlerV2(UserInfoAnalyzer userInfoAnalyzer) {
    this.userInfoAnalyzer = userInfoAnalyzer;
    this.mapper = new ObjectMapperFactory().createObjectMapper();
  }

  @Override
  public boolean canHandle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());
    return "CREATE".equals(review.getRequest().getOperation())
        && review.getRequest().getNamespace() != null;
  }

  @Override
  public void handle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());
    Objects.requireNonNull(review.getRequest().getUserInfo());

    V1UserInfo userInfo = review.getRequest().getUserInfo();
    UserInfoAnalysis analysis = this.userInfoAnalyzer.analyzeV2(userInfo);

    if (!analysis.isAipubMember()) {
      V1AdmissionReviewUtils.allow(review);
      return;
    }

    V1alpha1AipubUser aipubUser = analysis.getAipubUser().orElse(null);
    if (aipubUser == null) {
      V1AdmissionReviewUtils.allow(review);
      return;
    }

    String userId = aipubUser.getSpec() != null ? aipubUser.getSpec().getId() : null;
    if (userId == null) {
      V1AdmissionReviewUtils.reject(review, 500, "user id not found");
      return;
    }

    String username = K8sObjectUtils.getName(aipubUser);

    JsonPatchBuilder jsonPatchBuilder = new JsonPatchBuilder();

    JsonNode objectNode = review.getRequest().getObject();
    JsonNode labelsNode = objectNode != null
        ? objectNode.path("metadata").path("labels")
        : null;
    boolean hasLabels = labelsNode != null && !labelsNode.isMissingNode() && !labelsNode.isNull();

    if (!hasLabels) {
      JsonPatchOperation initLabelsOp = new JsonPatchOperationBuilder()
          .add()
          .setPath("/metadata/labels")
          .setValue(mapper.createObjectNode())
          .build();
      jsonPatchBuilder.addToOperations(initLabelsOp);
    }

    JsonPatchOperation usernameLabelOp = new JsonPatchOperationBuilder()
        .add()
        .setPath(USERNAME_LABEL_PATH)
        .setValue(mapper.getNodeFactory().textNode(username))
        .build();
    jsonPatchBuilder.addToOperations(usernameLabelOp);

    JsonPatchOperation useridLabelOp = new JsonPatchOperationBuilder()
        .add()
        .setPath(USERID_LABEL_PATH)
        .setValue(mapper.getNodeFactory().textNode(userId))
        .build();
    jsonPatchBuilder.addToOperations(useridLabelOp);

    V1AdmissionReviewUtils.allow(review, jsonPatchBuilder.build());
  }

}
