package io.ten1010.aipub.projectcontroller.mutating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.ten1010.aipub.projectcontroller.configuration.AipubProperties;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sGroupConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.SubjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectBinding;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectSpec;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1UserInfo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectReviewHandlerTest {

  private static final String TRUSTED_SA = "system:serviceaccount:aipub:aipub-resources-manager";

  private ProjectReviewHandler handler;
  private ObjectMapper mapper;
  private Indexer<V1alpha1Project> projectIndexer;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    this.projectIndexer = mock(Indexer.class);
    SharedInformerFactory factory = mock(SharedInformerFactory.class);
    mockInformer(factory, V1alpha1Project.class, this.projectIndexer);

    Cache<V1Namespace> namespaceCache = new Cache<>();
    namespaceCache.add(new V1Namespace().metadata(new V1ObjectMeta()
        .name("kubevirt")
        .labels(Map.of(LabelConstants.ALLOWLISTED_KEY, "true"))));

    AipubProperties aipubProperties = new AipubProperties();
    aipubProperties.setReservedNamespace(List.of("aipub"));
    aipubProperties.setBindingTrustedServiceAccounts(List.of(TRUSTED_SA));

    this.handler = new ProjectReviewHandler(
        aipubProperties, mock(SubjectResolver.class), factory,
        new NamespaceAllowlistResolver(namespaceCache));
    this.mapper = new ObjectMapperFactory().createObjectMapper();
  }

  @SuppressWarnings("unchecked")
  private <T extends KubernetesObject> void mockInformer(SharedInformerFactory factory,
      Class<T> clazz, Indexer<T> indexer) {
    SharedIndexInformer<T> informer = mock(SharedIndexInformer.class);
    when(informer.getIndexer()).thenReturn(indexer);
    when(factory.getExistingSharedIndexInformer(clazz)).thenReturn(informer);
  }

  private V1AdmissionReview createReview(String projectName, List<String> groups) {
    return createReview(projectName, groups, "CREATE");
  }

  private V1AdmissionReview createReview(String projectName, List<String> groups,
      String operation) {
    return createReview(project(projectName, null, null), "admin", groups, operation);
  }

  private V1AdmissionReview createReview(V1alpha1Project project, String username,
      List<String> groups, String operation) {
    V1UserInfo userInfo = new V1UserInfo();
    userInfo.setUsername(username);
    userInfo.setGroups(groups);

    V1AdmissionReviewRequest request = new V1AdmissionReviewRequest();
    request.setUid("test-uid");
    request.setOperation(operation);
    request.setUserInfo(userInfo);
    request.setObject(this.mapper.valueToTree(project));
    request.setName(project.getMetadata().getName());

    V1AdmissionReview review = new V1AdmissionReview();
    review.setApiVersion("admission.k8s.io/v1");
    review.setKind("AdmissionReview");
    review.setRequest(request);
    return review;
  }

  @Test
  @DisplayName("system admin이라도 reserved 이름 project 생성은 거부한다")
  void reservedName_systemAdmin_rejectsConflict() {
    V1AdmissionReview review = createReview("aipub",
        List.of(K8sGroupConstants.SYSTEM_MASTERS_GROUP_NAME));

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isFalse();
    assertThat(review.getResponse().getStatus().getCode()).isEqualTo(409);
  }

  @Test
  @DisplayName("system admin이라도 allowlist 네임스페이스 이름 project 생성은 거부한다")
  void allowlistedName_systemAdmin_rejectsConflict() {
    V1AdmissionReview review = createReview("kubevirt",
        List.of(K8sGroupConstants.SYSTEM_MASTERS_GROUP_NAME));

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isFalse();
    assertThat(review.getResponse().getStatus().getCode()).isEqualTo(409);
  }

  @Test
  @DisplayName("reserved 이름이라도 UPDATE는 hard block을 통과한다(finalizer 제거 교착 방지)")
  void reservedName_update_allows() {
    V1AdmissionReview review = createReview("aipub",
        List.of(K8sGroupConstants.SYSTEM_MASTERS_GROUP_NAME), "UPDATE");

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
  }

  @Test
  @DisplayName("allowlist 네임스페이스 이름이라도 UPDATE는 hard block을 통과한다")
  void allowlistedName_update_allows() {
    V1AdmissionReview review = createReview("kubevirt",
        List.of(K8sGroupConstants.SYSTEM_MASTERS_GROUP_NAME), "UPDATE");

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
  }

  @Test
  @DisplayName("reserved/allowlist가 아닌 이름은 system admin에게 허용한다")
  void ordinaryName_systemAdmin_allows() {
    V1AdmissionReview review = createReview("team-alpha",
        List.of(K8sGroupConstants.SYSTEM_MASTERS_GROUP_NAME));

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
  }


  private static V1alpha1Project project(String name, List<String> imageHubs,
      List<String> nodes) {
    V1alpha1Project project = new V1alpha1Project();
    project.setMetadata(new V1ObjectMeta().name(name));
    V1alpha1ProjectBinding binding = new V1alpha1ProjectBinding();
    binding.setImageHubs(imageHubs);
    binding.setNodes(nodes);
    V1alpha1ProjectSpec spec = new V1alpha1ProjectSpec();
    spec.setBinding(binding);
    project.setSpec(spec);
    return project;
  }

  private void givenExistingProject(V1alpha1Project existing) {
    when(this.projectIndexer.getByKey(anyString())).thenReturn(existing);
  }

  private static final List<String> SERVICE_ACCOUNT_GROUPS = List.of(
      K8sGroupConstants.SYSTEM_SERVICEACCOUNTS_GROUP_NAME,
      K8sGroupConstants.SYSTEM_AUTHENTICATED_GROUP_NAME);

  @Test
  @DisplayName("신뢰 서비스계정이 imageHubs 만 걷어내는 UPDATE 는 허용한다")
  void trustedServiceAccount_onlyImageHubsRemoved_allows() {
    givenExistingProject(project("team-alpha", List.of("common", "deleted-hub"), List.of("n1")));
    V1AdmissionReview review = createReview(project("team-alpha", List.of("common"), List.of("n1")),
        TRUSTED_SA, SERVICE_ACCOUNT_GROUPS, "UPDATE");

    this.handler.handle(review);

    assertThat(review.getResponse().getAllowed()).isTrue();
  }

  @Test
  @DisplayName("신뢰 서비스계정이라도 imageHubs 외 spec 이 함께 바뀌면 거부한다")
  void trustedServiceAccount_nodesAlsoChanged_rejectsForbidden() {
    givenExistingProject(project("team-alpha", List.of("deleted-hub"), List.of("n1")));
    V1AdmissionReview review = createReview(project("team-alpha", List.of(), List.of("n1", "n2")),
        TRUSTED_SA, SERVICE_ACCOUNT_GROUPS, "UPDATE");

    this.handler.handle(review);

    assertThat(review.getResponse().getAllowed()).isFalse();
    assertThat(review.getResponse().getStatus().getCode()).isEqualTo(403);
  }

  @Test
  @DisplayName("목록에 없는 서비스계정의 imageHubs 변경은 기존대로 거부한다")
  void untrustedServiceAccount_imageHubsRemoved_rejectsForbidden() {
    givenExistingProject(project("team-alpha", List.of("deleted-hub"), List.of("n1")));
    V1AdmissionReview review = createReview(project("team-alpha", List.of(), List.of("n1")),
        "system:serviceaccount:aipub:other", SERVICE_ACCOUNT_GROUPS, "UPDATE");

    this.handler.handle(review);

    assertThat(review.getResponse().getAllowed()).isFalse();
    assertThat(review.getResponse().getStatus().getCode()).isEqualTo(403);
  }
}
