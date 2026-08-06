package io.ten1010.aipub.projectcontroller.mutating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.ten1010.aipub.projectcontroller.configuration.AipubProperties;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.SubjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1UserInfo;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NamespaceReviewHandlerTest {

  private NamespaceReviewHandler handler;
  private Indexer<V1Namespace> mockNamespaceIndexer;
  private Indexer<V1alpha1Project> mockProjectIndexer;
  private ObjectMapper mapper;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    this.mockNamespaceIndexer = mock(Indexer.class);
    this.mockProjectIndexer = mock(Indexer.class);

    SharedInformerFactory factory = mock(SharedInformerFactory.class);
    mockInformer(factory, V1Namespace.class, this.mockNamespaceIndexer);
    mockInformer(factory, V1alpha1Project.class, this.mockProjectIndexer);

    AipubProperties aipubProperties = new AipubProperties();
    this.handler = new NamespaceReviewHandler(
        aipubProperties, mock(SubjectResolver.class), factory);
    this.mapper = new ObjectMapperFactory().createObjectMapper();
  }

  @SuppressWarnings("unchecked")
  private <T extends KubernetesObject> void mockInformer(SharedInformerFactory factory,
      Class<T> clazz, Indexer<T> indexer) {
    SharedIndexInformer<T> informer = mock(SharedIndexInformer.class);
    when(informer.getIndexer()).thenReturn(indexer);
    when(factory.getExistingSharedIndexInformer(clazz)).thenReturn(informer);
  }

  private V1Namespace namespace(String name, boolean allowlisted) {
    V1ObjectMeta meta = new V1ObjectMeta().name(name);
    if (allowlisted) {
      meta.labels(Map.of(LabelConstants.ALLOWLISTED_KEY, "true"));
    }
    return new V1Namespace().metadata(meta);
  }

  private V1alpha1Project project(String name, boolean terminating) {
    V1ObjectMeta meta = new V1ObjectMeta().name(name);
    if (terminating) {
      meta.deletionTimestamp(OffsetDateTime.now());
    }
    V1alpha1Project project = new V1alpha1Project();
    project.setMetadata(meta);
    return project;
  }

  private V1AdmissionReview createReview(String operation, V1Namespace desired) {
    V1AdmissionReviewRequest request = new V1AdmissionReviewRequest();
    request.setUid("test-uid");
    request.setOperation(operation);
    request.setName(desired.getMetadata().getName());
    request.setUserInfo(new V1UserInfo());
    request.setObject(this.mapper.valueToTree(desired));

    V1AdmissionReview review = new V1AdmissionReview();
    review.setApiVersion("admission.k8s.io/v1");
    review.setKind("AdmissionReview");
    review.setRequest(request);
    return review;
  }

  @Test
  @DisplayName("동명의 활성 project가 있으면 allowlist 라벨 부착(UPDATE)을 거부한다")
  void update_allowlistLabelWithActiveProject_rejectsConflict() {
    when(this.mockProjectIndexer.getByKey(anyString())).thenReturn(project("proj1", false));
    V1AdmissionReview review = createReview("UPDATE", namespace("proj1", true));

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isFalse();
    assertThat(review.getResponse().getStatus().getCode()).isEqualTo(409);
  }

  @Test
  @DisplayName("동명 project가 없으면 allowlist 라벨 부착을 허용한다")
  void update_allowlistLabelWithoutProject_allows() {
    V1AdmissionReview review = createReview("UPDATE", namespace("kubevirt", true));

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNull();
  }

  @Test
  @DisplayName("동명 project가 종료 중이어도 allowlist 라벨 부착을 거부한다")
  void update_allowlistLabelWithTerminatingProject_rejectsConflict() {
    when(this.mockProjectIndexer.getByKey(anyString())).thenReturn(project("proj1", true));
    V1AdmissionReview review = createReview("UPDATE", namespace("proj1", true));

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isFalse();
    assertThat(review.getResponse().getStatus().getCode()).isEqualTo(409);
  }

  @Test
  @DisplayName("allowlist 라벨이 없으면 동명 project가 있어도 허용한다")
  void update_withoutAllowlistLabel_allows() {
    when(this.mockProjectIndexer.getByKey(anyString())).thenReturn(project("proj1", false));
    V1AdmissionReview review = createReview("UPDATE", namespace("proj1", false));

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
  }

  @Test
  @DisplayName("CREATE로 allowlist 라벨이 붙은 채 동명 활성 project가 있으면 거부한다")
  void create_allowlistLabelWithActiveProject_rejectsConflict() {
    when(this.mockProjectIndexer.getByKey(anyString())).thenReturn(project("proj1", false));
    V1AdmissionReview review = createReview("CREATE", namespace("proj1", true));

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isFalse();
    assertThat(review.getResponse().getStatus().getCode()).isEqualTo(409);
  }

}
