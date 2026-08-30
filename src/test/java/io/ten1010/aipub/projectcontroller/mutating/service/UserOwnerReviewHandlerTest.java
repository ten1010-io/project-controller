package io.ten1010.aipub.projectcontroller.mutating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1Kind;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1UserInfo;
import java.util.List;
import java.util.Map;
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
        Set.of("aipub.ten1010.io/v1alpha1/Commit"),
        new NamespaceAllowlistResolver(new Cache<>()));
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

  // Namespace CREATE 는 request.namespace 가 자신의 이름으로 채워지지만 ownerReference 는 붙이지 않는다
  @Test
  void canHandle_createNamespaceWithSelfNamespace_returnsFalse() {
    V1AdmissionReview review = createReview("CREATE", "test-ns", "", "v1", "Namespace");
    assertThat(this.handler.canHandle(review)).isFalse();
  }

  // cluster-scoped ClusterVolume 은 request.namespace 가 비어 오지만 ownerReference 주입 대상이다
  @Test
  void canHandle_createClusterVolume_returnsTrue() {
    V1AdmissionReview review = createReview(
        "CREATE", null, "aipub.ten1010.io", "v1alpha1", "ClusterVolume");
    assertThat(this.handler.canHandle(review)).isTrue();
  }

  @Test
  void canHandle_updateClusterVolume_returnsFalse() {
    V1AdmissionReview review = createReview(
        "UPDATE", null, "aipub.ten1010.io", "v1alpha1", "ClusterVolume");
    assertThat(this.handler.canHandle(review)).isFalse();
  }

  // ClusterVolume 외의 cluster-scoped 리소스는 그대로 제외된다
  @Test
  void canHandle_createOtherClusterScopedResource_returnsFalse() {
    V1AdmissionReview review = createReview(
        "CREATE", null, "project.aipub.ten1010.io", "v1alpha1", "Project");
    assertThat(this.handler.canHandle(review)).isFalse();
  }

  // PersistentVolume 은 소유자 라벨 전파 대상(UserLabelReviewHandler)이지만 AipubUser ownerReference
  // 는 붙이지 않는다 — 둘 다 cluster-scoped 라 참조가 성립하므로, 붙이면 사용자 삭제 시
  // ClusterVolume 의 복제/앵커 PV 가 GC 로 함께 지워진다
  @Test
  void canHandle_createPersistentVolume_returnsFalse() {
    V1AdmissionReview review = createReview("CREATE", null, "", "v1", "PersistentVolume");
    assertThat(this.handler.canHandle(review)).isFalse();
  }

  // 같은 이름의 다른 그룹 kind 는 대상이 아니다
  @Test
  void canHandle_createClusterVolumeOfOtherGroup_returnsFalse() {
    V1AdmissionReview review = createReview(
        "CREATE", null, "example.com", "v1alpha1", "ClusterVolume");
    assertThat(this.handler.canHandle(review)).isFalse();
  }

  @Test
  void handle_clusterVolumeByMemberUser_addsOwnerReference() {
    V1AdmissionReview review = createReview(
        "CREATE", null, "aipub.ten1010.io", "v1alpha1", "ClusterVolume");

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

  // 백엔드 SA 등 비멤버가 만든 ClusterVolume 은 무변경 통과된다
  @Test
  void handle_clusterVolumeByNonMemberUser_allowsWithoutPatch() {
    V1AdmissionReview review = createReview(
        "CREATE", null, "aipub.ten1010.io", "v1alpha1", "ClusterVolume");

    UserInfoAnalysis analysis = new UserInfoAnalysis(
        "system:serviceaccount:aipub:aipub-backend",
        List.of("system:serviceaccounts", "system:authenticated"), null);
    when(this.mockAnalyzer.analyzeV2(any())).thenReturn(analysis);

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNull();
  }

  // config 로 제외한 GVK 는 cluster-scoped 경로에서도 무변경 통과된다
  @Test
  void handle_exceptedClusterVolumeGvk_allowsWithoutPatch() {
    UserOwnerReviewHandler exceptingHandler = new UserOwnerReviewHandler(
        this.mockAnalyzer,
        Set.of("aipub.ten1010.io/v1alpha1/ClusterVolume"),
        new NamespaceAllowlistResolver(new Cache<>()));
    V1AdmissionReview review = createReview(
        "CREATE", null, "aipub.ten1010.io", "v1alpha1", "ClusterVolume");

    exceptingHandler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNull();
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
  void handle_allowlistedNamespace_allowsWithoutPatch() {
    Cache<V1Namespace> namespaceCache = new Cache<>();
    namespaceCache.add(new V1Namespace().metadata(new V1ObjectMeta()
        .name("kubevirt")
        .labels(Map.of(LabelConstants.ALLOWLISTED_KEY, "true"))));
    UserOwnerReviewHandler allowlistAwareHandler = new UserOwnerReviewHandler(
        this.mockAnalyzer, Set.of(), new NamespaceAllowlistResolver(namespaceCache));
    V1AdmissionReview review = createReview("CREATE", "kubevirt", "apps", "v1", "Deployment");

    allowlistAwareHandler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNull();
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
