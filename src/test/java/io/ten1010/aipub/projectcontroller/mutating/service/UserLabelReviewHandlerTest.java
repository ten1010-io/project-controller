package io.ten1010.aipub.projectcontroller.mutating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUserSpec;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1Kind;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1UserInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserLabelReviewHandlerTest {

  private UserLabelReviewHandler handler;
  private UserInfoAnalyzer mockAnalyzer;
  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    this.mockAnalyzer = mock(UserInfoAnalyzer.class);
    ApiResourceDiscovery mockDiscovery = mock(ApiResourceDiscovery.class);
    ApiClient mockApiClient = mock(ApiClient.class);
    this.handler = new UserLabelReviewHandler(this.mockAnalyzer, mockDiscovery, mockApiClient);
    this.mapper = new ObjectMapperFactory().createObjectMapper();
  }

  private V1AdmissionReview createReview(String operation, String namespace) {
    V1Kind v1Kind = new V1Kind();
    v1Kind.setGroup("apps");
    v1Kind.setVersion("v1");
    v1Kind.setKind("Deployment");

    V1UserInfo userInfo = new V1UserInfo();
    userInfo.setUsername("oidc:testuser");
    userInfo.setGroups(List.of("oidc:aipub-member", "system:authenticated"));

    ObjectNode objNode = this.mapper.createObjectNode();
    objNode.putObject("metadata");

    V1AdmissionReviewRequest request = new V1AdmissionReviewRequest();
    request.setUid("test-uid");
    request.setOperation(operation);
    request.setNamespace(namespace);
    request.setKind(v1Kind);
    request.setUserInfo(userInfo);
    request.setObject(objNode);

    V1AdmissionReview review = new V1AdmissionReview();
    review.setApiVersion("admission.k8s.io/v1");
    review.setKind("AdmissionReview");
    review.setRequest(request);

    return review;
  }

  private V1alpha1AipubUser createAipubUser(String name, String uid, String userId) {
    V1alpha1AipubUser user = new V1alpha1AipubUser();
    user.setApiVersion("project.aipub.ten1010.io/v1alpha1");
    user.setKind("AipubUser");
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName(name);
    meta.setUid(uid);
    user.setMetadata(meta);
    V1alpha1AipubUserSpec spec = new V1alpha1AipubUserSpec();
    spec.setId(userId);
    user.setSpec(spec);
    return user;
  }

  @Test
  void canHandle_createNamespaced_returnsTrue() {
    V1AdmissionReview review = createReview("CREATE", "default");
    assertThat(this.handler.canHandle(review)).isTrue();
  }

  @Test
  void canHandle_updateOperation_returnsFalse() {
    V1AdmissionReview review = createReview("UPDATE", "default");
    assertThat(this.handler.canHandle(review)).isFalse();
  }

  @Test
  void canHandle_noNamespace_returnsFalse() {
    V1AdmissionReview review = createReview("CREATE", null);
    assertThat(this.handler.canHandle(review)).isFalse();
  }

  @Test
  void handle_memberUser_addsLabels() {
    V1AdmissionReview review = createReview("CREATE", "default");

    V1alpha1AipubUser aipubUser = createAipubUser("testuser", "uid-123", "user-id-456");
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
  void handle_memberUserNoUserId_rejects() {
    V1AdmissionReview review = createReview("CREATE", "default");

    V1alpha1AipubUser aipubUser = createAipubUser("testuser", "uid-123", null);
    aipubUser.getSpec().setId(null);
    UserInfoAnalysis analysis = new UserInfoAnalysis(
        "oidc:testuser",
        List.of("oidc:aipub-member", "system:authenticated"),
        aipubUser);
    when(this.mockAnalyzer.analyzeV2(any())).thenReturn(analysis);

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isFalse();
    assertThat(review.getResponse().getStatus().getCode()).isEqualTo(500);
  }

  @Test
  void handle_nonMemberNoOwner_allowsWithoutPatch() {
    V1AdmissionReview review = createReview("CREATE", "default");

    UserInfoAnalysis analysis = new UserInfoAnalysis(
        "system:serviceaccount:kube-system:replicaset-controller",
        List.of("system:serviceaccounts", "system:authenticated"),
        null);
    when(this.mockAnalyzer.analyzeV2(any())).thenReturn(analysis);

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNull();
  }

  @Test
  void handle_analyzerThrows_rejects() {
    V1AdmissionReview review = createReview("CREATE", "default");

    when(this.mockAnalyzer.analyzeV2(any())).thenThrow(new RuntimeException("test error"));

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isFalse();
    assertThat(review.getResponse().getStatus().getCode()).isEqualTo(500);
  }

}
