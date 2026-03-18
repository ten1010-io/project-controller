package io.ten1010.aipub.projectcontroller.validating.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sGroupConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.mutating.V1AdmissionReviewUtils;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1UserInfo;
import io.ten1010.aipub.projectcontroller.mutating.service.ReviewHandler;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UserLabelValidateHandler implements ReviewHandler {

  private static final String OPERATION_UPDATE = "UPDATE";

  // v2 테스트용: UserLabelReviewHandler 와 동일한 키 사용
  private static final String USERNAME_LABEL_KEY_V2 =
      LabelConstants.OBJECT_OWN_USERNAME_KEY + "-v2";
  private static final String USERID_LABEL_KEY_V2 =
      LabelConstants.OBJECT_OWN_USERID_KEY + "-v2";

  @Override
  public boolean canHandle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());

    V1AdmissionReviewRequest request = review.getRequest();
    return OPERATION_UPDATE.equals(request.getOperation());
  }

  @Override
  public void handle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());

    V1AdmissionReviewRequest request = review.getRequest();
    V1UserInfo userInfo = request.getUserInfo();
    if (userInfo == null || userInfo.getGroups() == null || userInfo.getGroups().isEmpty()) {
      V1AdmissionReviewUtils.allow(review);
      return;
    }

    if (!userInfo.getGroups().contains(K8sGroupConstants.AIPUB_MEMBER_GROUP_NAME)) {
      V1AdmissionReviewUtils.allow(review);
      return;
    }

    if (userInfo.getGroups().contains(K8sGroupConstants.AIPUB_ADMIN_GROUP_NAME)) {
      V1AdmissionReviewUtils.allow(review);
      return;
    }

    JsonNode currentObj = request.getObject();
    JsonNode oldObj = request.getOldObject();
    if (currentObj == null || oldObj == null) {
      V1AdmissionReviewUtils.allow(review);
      return;
    }

    JsonNode currentLabels = currentObj.path("metadata").path("labels");
    JsonNode oldLabels = oldObj.path("metadata").path("labels");

    String currentUserName = getTextValue(currentLabels, USERNAME_LABEL_KEY_V2);
    String oldUserName = getTextValue(oldLabels, USERNAME_LABEL_KEY_V2);
    if (!Objects.equals(currentUserName, oldUserName)) {
      V1AdmissionReviewUtils.reject(review, 403, "Not allowed to update user name label");
      return;
    }

    String currentUserId = getTextValue(currentLabels, USERID_LABEL_KEY_V2);
    String oldUserId = getTextValue(oldLabels, USERID_LABEL_KEY_V2);
    if (!Objects.equals(currentUserId, oldUserId)) {
      V1AdmissionReviewUtils.reject(review, 403, "Not allowed to update user id label");
      return;
    }

    V1AdmissionReviewUtils.allow(review);
  }

  private static String getTextValue(JsonNode labels, String key) {
    if (labels == null || labels.isMissingNode() || labels.isNull()) {
      return null;
    }
    JsonNode value = labels.get(key);
    if (value == null || value.isNull()) {
      return null;
    }
    return value.asText();
  }

}
