package io.ten1010.aipub.projectcontroller.mutating.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1UserInfo;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectReviewHandlerTest {

  private ProjectReviewHandler handler;
  private ObjectMapper mapper;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    Indexer<V1alpha1Project> mockProjectIndexer = mock(Indexer.class);
    SharedInformerFactory factory = mock(SharedInformerFactory.class);
    mockInformer(factory, V1alpha1Project.class, mockProjectIndexer);

    Cache<V1Namespace> namespaceCache = new Cache<>();
    namespaceCache.add(new V1Namespace().metadata(new V1ObjectMeta()
        .name("kubevirt")
        .labels(Map.of(LabelConstants.ALLOWLISTED_KEY, "true"))));

    AipubProperties aipubProperties = new AipubProperties();
    aipubProperties.setReservedNamespace(List.of("aipub"));

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
    V1alpha1Project project = new V1alpha1Project();
    project.setMetadata(new V1ObjectMeta().name(projectName));

    V1UserInfo userInfo = new V1UserInfo();
    userInfo.setUsername("admin");
    userInfo.setGroups(groups);

    V1AdmissionReviewRequest request = new V1AdmissionReviewRequest();
    request.setUid("test-uid");
    request.setOperation("CREATE");
    request.setUserInfo(userInfo);
    request.setObject(this.mapper.valueToTree(project));

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
  @DisplayName("reserved/allowlist가 아닌 이름은 system admin에게 허용한다")
  void ordinaryName_systemAdmin_allows() {
    V1AdmissionReview review = createReview("team-alpha",
        List.of(K8sGroupConstants.SYSTEM_MASTERS_GROUP_NAME));

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
  }

}
