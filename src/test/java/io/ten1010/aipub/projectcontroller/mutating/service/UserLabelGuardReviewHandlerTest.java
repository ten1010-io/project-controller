package io.ten1010.aipub.projectcontroller.mutating.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1Kind;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1UserInfo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserLabelGuardReviewHandlerTest {

  private UserLabelGuardReviewHandler handler;
  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    this.handler = new UserLabelGuardReviewHandler();
    this.mapper = new ObjectMapperFactory().createObjectMapper();
  }

  private ObjectNode createNamespaceNode(String name, Map<String, String> labels) {
    ObjectNode objNode = this.mapper.createObjectNode();
    ObjectNode metadata = objNode.putObject("metadata");
    metadata.put("name", name);
    ObjectNode labelsNode = metadata.putObject("labels");
    labels.forEach(labelsNode::put);
    return objNode;
  }

  private V1AdmissionReview createNamespaceUpdateReview(List<String> groups,
      Map<String, String> oldLabels, Map<String, String> newLabels) {
    V1Kind v1Kind = new V1Kind();
    v1Kind.setGroup("");
    v1Kind.setVersion("v1");
    v1Kind.setKind("Namespace");

    V1UserInfo userInfo = new V1UserInfo();
    userInfo.setUsername("oidc:testuser");
    userInfo.setGroups(groups);

    V1AdmissionReviewRequest request = new V1AdmissionReviewRequest();
    request.setUid("test-uid");
    request.setOperation("UPDATE");
    request.setNamespace("test-ns");
    request.setKind(v1Kind);
    request.setUserInfo(userInfo);
    request.setOldObject(createNamespaceNode("test-ns", oldLabels));
    request.setObject(createNamespaceNode("test-ns", newLabels));

    V1AdmissionReview review = new V1AdmissionReview();
    review.setApiVersion("admission.k8s.io/v1");
    review.setKind("AdmissionReview");
    review.setRequest(request);

    return review;
  }

  private static final List<String> MEMBER_GROUPS =
      List.of("oidc:aipub-member", "system:authenticated");
  private static final List<String> ADMIN_GROUPS =
      List.of("oidc:aipub-admin", "oidc:aipub-member", "system:authenticated");
  private static final List<String> NON_MEMBER_GROUPS =
      List.of("system:serviceaccounts", "system:authenticated");

  private static final Map<String, String> OWNER_LABELS = Map.of(
      LabelConstants.OBJECT_OWN_USERNAME_KEY, "userA",
      LabelConstants.OBJECT_OWN_USERID_KEY, "id-A");
  private static final Map<String, String> TAMPERED_LABELS = Map.of(
      LabelConstants.OBJECT_OWN_USERNAME_KEY, "userB",
      LabelConstants.OBJECT_OWN_USERID_KEY, "id-A");

  @Test
  void canHandle_updateNamespace_returnsTrue() {
    V1AdmissionReview review =
        createNamespaceUpdateReview(MEMBER_GROUPS, OWNER_LABELS, OWNER_LABELS);
    assertThat(this.handler.canHandle(review)).isTrue();
  }

  @Test
  void canHandle_createNamespace_returnsFalse() {
    V1AdmissionReview review =
        createNamespaceUpdateReview(MEMBER_GROUPS, OWNER_LABELS, OWNER_LABELS);
    review.getRequest().setOperation("CREATE");
    assertThat(this.handler.canHandle(review)).isFalse();
  }

  @Test
  void canHandle_updateNamespacedResource_returnsFalse() {
    V1AdmissionReview review =
        createNamespaceUpdateReview(MEMBER_GROUPS, OWNER_LABELS, OWNER_LABELS);
    V1Kind v1Kind = new V1Kind();
    v1Kind.setGroup("apps");
    v1Kind.setVersion("v1");
    v1Kind.setKind("Deployment");
    review.getRequest().setKind(v1Kind);
    assertThat(this.handler.canHandle(review)).isFalse();
  }

  // admin이 아닌 member의 소유자 라벨 변경은 권한 없음으로 거부한다
  @Test
  void handle_memberChangesUserLabel_rejects403() {
    V1AdmissionReview review =
        createNamespaceUpdateReview(MEMBER_GROUPS, OWNER_LABELS, TAMPERED_LABELS);

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isFalse();
    assertThat(review.getResponse().getStatus().getCode()).isEqualTo(403);
  }

  // 라벨 삭제도 변경으로 취급한다
  @Test
  void handle_memberRemovesUserLabel_rejects403() {
    V1AdmissionReview review =
        createNamespaceUpdateReview(MEMBER_GROUPS, OWNER_LABELS, Map.of());

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isFalse();
    assertThat(review.getResponse().getStatus().getCode()).isEqualTo(403);
  }

  @Test
  void handle_memberUpdatesWithoutLabelChange_allows() {
    V1AdmissionReview review =
        createNamespaceUpdateReview(MEMBER_GROUPS, OWNER_LABELS, OWNER_LABELS);

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
  }

  @Test
  void handle_adminChangesUserLabel_allows() {
    V1AdmissionReview review =
        createNamespaceUpdateReview(ADMIN_GROUPS, OWNER_LABELS, TAMPERED_LABELS);

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
  }

  // aipub 그룹 밖의 주체(시스템 컴포넌트 등)는 제한하지 않는다
  @Test
  void handle_nonMemberChangesUserLabel_allows() {
    V1AdmissionReview review =
        createNamespaceUpdateReview(NON_MEMBER_GROUPS, OWNER_LABELS, TAMPERED_LABELS);

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
  }

}
