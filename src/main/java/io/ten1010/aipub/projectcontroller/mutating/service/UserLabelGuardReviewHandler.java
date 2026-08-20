package io.ten1010.aipub.projectcontroller.mutating.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.mutating.V1AdmissionReviewUtils;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

/**
 * Namespace UPDATE 에서 소유자 라벨(username/userid)의 무단 변경을 막는 가드.
 *
 * <p>CREATE 시 {@code UserLabelReviewHandler}가 토큰에서 추출해 주입한 라벨은 요청자 신원의
 * 기록이므로, aipub admin 이 아닌 aipub member 가 UPDATE 로 이를 추가/변경/삭제하는 것은 권한
 * 없음으로 거부한다. 워크로드류는 {@code UserLabelSynchronizer}가 주기 보정하지만 Namespace 는
 * 보정 장치가 없어 이 가드가 유일한 방어선이다.
 *
 * <p>aipub 그룹에 속하지 않은 주체(시스템 컴포넌트, cluster-admin 등)와 aipub admin 은 제한하지
 * 않는다 — 컨트롤러/운영자의 정정 경로를 막지 않기 위함이다.
 */
@Slf4j
public class UserLabelGuardReviewHandler implements ReviewHandler {

  private static final String OPERATION_UPDATE = "UPDATE";

  private static final List<String> GUARDED_LABEL_KEYS = List.of(
      LabelConstants.OBJECT_OWN_USERNAME_KEY,
      LabelConstants.OBJECT_OWN_USERID_KEY);

  @Override
  public boolean canHandle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());

    V1AdmissionReviewRequest request = review.getRequest();
    if (!OPERATION_UPDATE.equals(request.getOperation())) {
      return false;
    }
    return V1AdmissionReviewUtils.isNamespaceRequest(request);
  }

  @Override
  public void handle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());

    V1AdmissionReviewRequest request = review.getRequest();
    Objects.requireNonNull(request.getUserInfo());
    Objects.requireNonNull(request.getUserInfo().getGroups());
    Objects.requireNonNull(request.getObject());
    Objects.requireNonNull(request.getOldObject());

    String changedKey = findChangedLabelKey(request.getOldObject(), request.getObject());
    if (changedKey == null) {
      V1AdmissionReviewUtils.allow(review);
      return;
    }

    List<String> groups = request.getUserInfo().getGroups();
    if (UserInfoAnalyzer.isAipubMember(groups) && !UserInfoAnalyzer.isAipubAdmin(groups)) {
      log.debug("UserLabelGuard: deny label change. user={}, label={}",
          request.getUserInfo().getUsername(), changedKey);
      V1AdmissionReviewUtils.reject(review, HttpStatus.FORBIDDEN.value(),
          "Only aipub admin can modify user label: " + changedKey);
      return;
    }

    V1AdmissionReviewUtils.allow(review);
  }

  @Nullable
  private static String findChangedLabelKey(JsonNode oldObject, JsonNode newObject) {
    JsonNode oldLabels = oldObject.path("metadata").path("labels");
    JsonNode newLabels = newObject.path("metadata").path("labels");
    for (String key : GUARDED_LABEL_KEYS) {
      String oldValue = textValue(oldLabels, key);
      String newValue = textValue(newLabels, key);
      if (!Objects.equals(oldValue, newValue)) {
        return key;
      }
    }
    return null;
  }

  @Nullable
  private static String textValue(JsonNode labels, String key) {
    JsonNode value = labels.get(key);
    if (value == null) {
      return null;
    }
    return value.textValue();
  }

}
