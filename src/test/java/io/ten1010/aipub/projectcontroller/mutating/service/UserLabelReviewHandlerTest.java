package io.ten1010.aipub.projectcontroller.mutating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
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
    this.handler = new UserLabelReviewHandler(this.mockAnalyzer, mockDiscovery, mockApiClient,
        new NamespaceAllowlistResolver(new Cache<>()));
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

  private V1AdmissionReview createNamespaceReview(String namespaceName) {
    V1AdmissionReview review = createReview("CREATE", namespaceName);
    V1Kind v1Kind = new V1Kind();
    v1Kind.setGroup("");
    v1Kind.setVersion("v1");
    v1Kind.setKind("Namespace");
    review.getRequest().setKind(v1Kind);
    ObjectNode objNode = this.mapper.createObjectNode();
    objNode.putObject("metadata").put("name", namespaceName);
    review.getRequest().setObject(objNode);
    return review;
  }

  private V1AdmissionReview createClusterVolumeReview() {
    // cluster-scoped 요청은 request.namespace 가 비어 온다
    V1AdmissionReview review = createReview("CREATE", null);
    V1Kind v1Kind = new V1Kind();
    v1Kind.setGroup("aipub.ten1010.io");
    v1Kind.setVersion("v1alpha1");
    v1Kind.setKind("ClusterVolume");
    review.getRequest().setKind(v1Kind);
    ObjectNode objNode = this.mapper.createObjectNode();
    objNode.putObject("metadata").put("name", "cv-test");
    review.getRequest().setObject(objNode);
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

  // 소유권 대상은 전부 네임스페이스 리소스이므로 네임스페이스 없는 요청은 처리하지 않는다
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
  void canHandle_createNamespace_returnsTrue() {
    V1AdmissionReview review = createNamespaceReview("test-ns");
    assertThat(this.handler.canHandle(review)).isTrue();
  }

  @Test
  void handle_memberCreatesNamespace_addsLabels() {
    V1AdmissionReview review = createNamespaceReview("test-ns");

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

  // 네임스페이스 오브젝트 자체는 allowlist여도 라벨을 주입한다 — allowlist 스킵은
  // "allowlist 네임스페이스 안의 리소스" 규칙이지 네임스페이스 자신의 규칙이 아니다
  @Test
  void handle_memberCreatesAllowlistedNamespace_addsLabels() {
    Cache<V1Namespace> namespaceCache = new Cache<>();
    namespaceCache.add(new V1Namespace().metadata(new V1ObjectMeta()
        .name("allowlisted-ns")
        .labels(java.util.Map.of(LabelConstants.ALLOWLISTED_KEY, "true"))));
    UserLabelReviewHandler allowlistAwareHandler = new UserLabelReviewHandler(
        this.mockAnalyzer, mock(ApiResourceDiscovery.class), mock(ApiClient.class),
        new NamespaceAllowlistResolver(namespaceCache));

    V1AdmissionReview review = createNamespaceReview("allowlisted-ns");

    V1alpha1AipubUser aipubUser = createAipubUser("testuser", "uid-123", "user-id-456");
    UserInfoAnalysis analysis = new UserInfoAnalysis(
        "oidc:testuser",
        List.of("oidc:aipub-member", "system:authenticated"),
        aipubUser);
    when(this.mockAnalyzer.analyzeV2(any())).thenReturn(analysis);

    allowlistAwareHandler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNotNull();
  }

  // allowlist 네임스페이스 "안의" 리소스는 기존대로 라벨 없이 통과한다 (회귀 방지)
  @Test
  void handle_namespacedResourceInAllowlistedNamespace_allowsWithoutPatch() {
    Cache<V1Namespace> namespaceCache = new Cache<>();
    namespaceCache.add(new V1Namespace().metadata(new V1ObjectMeta()
        .name("allowlisted-ns")
        .labels(java.util.Map.of(LabelConstants.ALLOWLISTED_KEY, "true"))));
    UserLabelReviewHandler allowlistAwareHandler = new UserLabelReviewHandler(
        this.mockAnalyzer, mock(ApiResourceDiscovery.class), mock(ApiClient.class),
        new NamespaceAllowlistResolver(namespaceCache));

    V1AdmissionReview review = createReview("CREATE", "allowlisted-ns");

    allowlistAwareHandler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNull();
  }

  // 비멤버(시스템 컴포넌트)가 만드는 네임스페이스는 owner 전파 경로가 없으므로 라벨 없이 허용
  @Test
  void handle_nonMemberCreatesNamespace_allowsWithoutPatch() {
    V1AdmissionReview review = createNamespaceReview("test-ns");

    UserInfoAnalysis analysis = new UserInfoAnalysis(
        "system:serviceaccount:kube-system:namespace-controller",
        List.of("system:serviceaccounts", "system:authenticated"),
        null);
    when(this.mockAnalyzer.analyzeV2(any())).thenReturn(analysis);

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNull();
  }

  // admin 토큰에는 aipub-member 그룹이 없지만 (k8s RBAC 별개 그룹) Namespace 는 admin 도 라벨 대상
  @Test
  void handle_adminCreatesNamespace_addsLabels() {
    V1AdmissionReview review = createNamespaceReview("test-ns");

    V1alpha1AipubUser aipubUser = createAipubUser("aipubadmin", "uid-admin", "admin-id-1");
    UserInfoAnalysis analysis = new UserInfoAnalysis(
        "oidc:aipubadmin",
        List.of("oidc:aipub-admin", "system:authenticated"),
        aipubUser);
    when(this.mockAnalyzer.analyzeV2(any())).thenReturn(analysis);

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNotNull();
  }

  // AipubUser CR 없는 admin 은 거부하지 않고 라벨 없이 허용한다 (admin 운영 경로 보존)
  @Test
  void handle_adminWithoutAipubUserCreatesNamespace_allowsWithoutPatch() {
    V1AdmissionReview review = createNamespaceReview("test-ns");

    UserInfoAnalysis analysis = new UserInfoAnalysis(
        "oidc:someadmin",
        List.of("oidc:aipub-admin", "system:authenticated"),
        null);
    when(this.mockAnalyzer.analyzeV2(any())).thenReturn(analysis);

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNull();
  }

  // namespaced 리소스는 admin 라벨 대상이 아니다 (기존 quota/소유권 동작 보존)
  @Test
  void handle_adminCreatesNamespacedResource_allowsWithoutPatch() {
    V1AdmissionReview review = createReview("CREATE", "default");

    V1alpha1AipubUser aipubUser = createAipubUser("aipubadmin", "uid-admin", "admin-id-1");
    UserInfoAnalysis analysis = new UserInfoAnalysis(
        "oidc:aipubadmin",
        List.of("oidc:aipub-admin", "system:authenticated"),
        aipubUser);
    when(this.mockAnalyzer.analyzeV2(any())).thenReturn(analysis);

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNull();
  }

  @Test
  void canHandle_createClusterVolume_returnsTrue() {
    assertThat(this.handler.canHandle(createClusterVolumeReview())).isTrue();
  }

  // ClusterVolume 은 라벨이 유일한 소유자 기록이므로 멤버 생성 시 반드시 찍혀야 한다
  @Test
  void handle_memberCreatesClusterVolume_addsLabels() {
    V1AdmissionReview review = createClusterVolumeReview();
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

  // 백엔드 SA·시스템 컴포넌트 생성은 무변경 통과 (cluster-scoped 는 owner 전파 경로가 없다)
  @Test
  void handle_nonMemberCreatesClusterVolume_allowsWithoutPatch() {
    V1AdmissionReview review = createClusterVolumeReview();
    UserInfoAnalysis analysis = new UserInfoAnalysis(
        "system:serviceaccount:aipub:aipub-backend",
        List.of("system:serviceaccounts", "system:authenticated"), null);
    when(this.mockAnalyzer.analyzeV2(any())).thenReturn(analysis);

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNull();
  }

  // 멤버인데 AipubUser CR 이 없으면 400 — CREATE 가 막힌다 (Namespace 와 같은 계약)
  @Test
  void handle_memberWithoutAipubUserCreatesClusterVolume_rejects() {
    V1AdmissionReview review = createClusterVolumeReview();
    UserInfoAnalysis analysis = new UserInfoAnalysis(
        "oidc:ghost",
        List.of("oidc:aipub-member", "system:authenticated"), null);
    when(this.mockAnalyzer.analyzeV2(any())).thenReturn(analysis);

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isFalse();
    assertThat(review.getResponse().getStatus().getCode()).isEqualTo(400);
  }

  // admin 전용 라벨링은 Namespace 에만 적용된다 — ClusterVolume 은 member 검사만 쓴다
  @Test
  void handle_adminCreatesClusterVolume_allowsWithoutPatch() {
    V1AdmissionReview review = createClusterVolumeReview();
    V1alpha1AipubUser aipubUser = createAipubUser("aipubadmin", "uid-admin", "admin-id-1");
    UserInfoAnalysis analysis = new UserInfoAnalysis(
        "oidc:aipubadmin",
        List.of("oidc:aipub-admin", "system:authenticated"),
        aipubUser);
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
