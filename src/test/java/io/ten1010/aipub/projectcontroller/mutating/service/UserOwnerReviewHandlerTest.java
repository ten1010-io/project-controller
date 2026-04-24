package io.ten1010.aipub.projectcontroller.mutating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1Kind;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1UserInfo;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserOwnerReviewHandlerTest {

  private UserOwnerReviewHandler handler;
  private UserInfoAnalyzer mockAnalyzer;
  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    this.mockAnalyzer = mock(UserInfoAnalyzer.class);
    this.handler = new UserOwnerReviewHandler(
        this.mockAnalyzer,
        Set.of("aipub.ten1010.io/v1alpha1/Commit"));
    this.mapper = new ObjectMapperFactory().createObjectMapper();
  }

  private V1AdmissionReview createReview(String operation, String namespace, String group,
      String version, String kind) {
    V1Kind v1Kind = new V1Kind();
    v1Kind.setGroup(group);
    v1Kind.setVersion(version);
    v1Kind.setKind(kind);

    V1UserInfo userInfo = new V1UserInfo();
    userInfo.setUsername("oidc:testuser");
    userInfo.setGroups(List.of("oidc:aipub-member", "system:authenticated"));

    V1AdmissionReviewRequest request = new V1AdmissionReviewRequest();
    request.setUid("test-uid");
    request.setOperation(operation);
    request.setNamespace(namespace);
    request.setKind(v1Kind);
    request.setUserInfo(userInfo);
    request.setObject(this.mapper.createObjectNode()
        .putObject("metadata")
        .putObject("labels").objectNode());

    JsonNode objNode = this.mapper.createObjectNode();
    ((com.fasterxml.jackson.databind.node.ObjectNode) objNode)
        .putObject("metadata");
    request.setObject(objNode);

    V1AdmissionReview review = new V1AdmissionReview();
    review.setApiVersion("admission.k8s.io/v1");
    review.setKind("AdmissionReview");
    review.setRequest(request);

    return review;
  }

  private V1alpha1AipubUser createAipubUser(String name, String uid) {
    V1alpha1AipubUser user = new V1alpha1AipubUser();
    user.setApiVersion("project.aipub.ten1010.io/v1alpha1");
    user.setKind("AipubUser");
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName(name);
    meta.setUid(uid);
    user.setMetadata(meta);
    return user;
  }

  @Test
  void canHandle_createNamespaced_returnsTrue() {
    V1AdmissionReview review = createReview("CREATE", "default", "apps", "v1", "Deployment");
    assertThat(this.handler.canHandle(review)).isTrue();
  }

  @Test
  void canHandle_updateOperation_returnsFalse() {
    V1AdmissionReview review = createReview("UPDATE", "default", "apps", "v1", "Deployment");
    assertThat(this.handler.canHandle(review)).isFalse();
  }

  @Test
  void canHandle_deleteOperation_returnsFalse() {
    V1AdmissionReview review = createReview("DELETE", "default", "apps", "v1", "Deployment");
    assertThat(this.handler.canHandle(review)).isFalse();
  }

  @Test
  void canHandle_noNamespace_returnsFalse() {
    V1AdmissionReview review = createReview("CREATE", null, "", "v1", "Namespace");
    assertThat(this.handler.canHandle(review)).isFalse();
  }

  @Test
  void canHandle_emptyNamespace_returnsFalse() {
    V1AdmissionReview review = createReview("CREATE", "", "", "v1", "Namespace");
    assertThat(this.handler.canHandle(review)).isFalse();
  }

  @Test
  void handle_exceptedGvk_allowsWithoutPatch() {
    V1AdmissionReview review = createReview(
        "CREATE", "default", "aipub.ten1010.io", "v1alpha1", "Commit");

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNull();
  }

  @Test
  void handle_nonMemberUser_allowsWithoutPatch() {
    V1AdmissionReview review = createReview("CREATE", "default", "apps", "v1", "Deployment");

    UserInfoAnalysis analysis = new UserInfoAnalysis(
        "testuser", List.of("system:authenticated"), null);
    when(this.mockAnalyzer.analyzeV2(any())).thenReturn(analysis);

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNull();
  }

  @Test
  void handle_memberUser_addsOwnerReference() {
    V1AdmissionReview review = createReview("CREATE", "default", "apps", "v1", "Deployment");

    V1alpha1AipubUser aipubUser = createAipubUser("testuser", "user-uid-123");
    UserInfoAnalysis analysis = new UserInfoAnalysis(
        "oidc:testuser",
        List.of("oidc:aipub-member", "system:authenticated"),
        aipubUser);
    when(this.mockAnalyzer.analyzeV2(any())).thenReturn(analysis);

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNotNull();
    assertThat(review.getResponse().getPatchType()).isEqualTo("JSONPatch");
  }

  @Test
  void handle_analyzerThrows_rejectsWithServerError() {
    V1AdmissionReview review = createReview("CREATE", "default", "apps", "v1", "Deployment");

    when(this.mockAnalyzer.analyzeV2(any())).thenThrow(new RuntimeException("test error"));

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isFalse();
    assertThat(review.getResponse().getStatus().getCode()).isEqualTo(500);
  }

}
