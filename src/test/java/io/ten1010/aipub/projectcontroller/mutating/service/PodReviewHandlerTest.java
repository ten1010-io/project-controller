package io.ten1010.aipub.projectcontroller.mutating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.ten1010.aipub.projectcontroller.controller.workload.PodNodesResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.DockerConfigJsonResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.SubjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.TaintConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.WorkloadExclusionResolver;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PodReviewHandlerTest {

  private static final String EXCLUSION_LABEL = "test.aipub/excluded";

  private PodReviewHandler handler;
  private PodNodesResolver mockPodNodesResolver;
  private ObjectMapper mapper;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    this.mockPodNodesResolver = mock(PodNodesResolver.class);
    SharedInformerFactory factory = mock(SharedInformerFactory.class);
    SharedIndexInformer<V1alpha1Project> projectInformer = mock(SharedIndexInformer.class);
    when(projectInformer.getIndexer()).thenReturn(mock(Indexer.class));
    when(factory.getExistingSharedIndexInformer(V1alpha1Project.class))
        .thenReturn(projectInformer);

    Cache<V1Namespace> namespaceCache = new Cache<>();
    namespaceCache.add(new V1Namespace().metadata(new V1ObjectMeta()
        .name("kubevirt")
        .labels(Map.of(LabelConstants.ALLOWLISTED_KEY, "true"))));

    ReconciliationService reconciliationService = new ReconciliationService(
        mock(SubjectResolver.class),
        mock(DockerConfigJsonResolver.class),
        List.of(),
        new WorkloadExclusionResolver(List.of(EXCLUSION_LABEL)),
        new NamespaceAllowlistResolver(namespaceCache));

    this.handler = new PodReviewHandler(this.mockPodNodesResolver, factory, reconciliationService);
    this.mapper = new ObjectMapperFactory().createObjectMapper();
  }

  private V1AdmissionReview createReview(String namespace, Map<String, String> podLabels) {
    V1Pod pod = new V1Pod()
        .metadata(new V1ObjectMeta().name("test-pod").namespace(namespace).labels(podLabels))
        .spec(new V1PodSpec());

    V1AdmissionReviewRequest request = new V1AdmissionReviewRequest();
    request.setUid("test-uid");
    request.setOperation("CREATE");
    request.setNamespace(namespace);
    request.setObject(this.mapper.valueToTree(pod));

    V1AdmissionReview review = new V1AdmissionReview();
    review.setApiVersion("admission.k8s.io/v1");
    review.setKind("AdmissionReview");
    review.setRequest(request);
    return review;
  }

  private static String decodePatch(V1AdmissionReview review) {
    return new String(Base64.getDecoder().decode(review.getResponse().getPatch()),
        StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("allowlist 네임스페이스의 파드에는 Exists toleration 쌍 패치를 주입한다")
  void handle_allowlistedNamespace_injectsExistsTolerationPatch() {
    V1AdmissionReview review = createReview("kubevirt", Map.of());

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNotNull();
    String patch = decodePatch(review);
    assertThat(patch).contains("/spec/tolerations");
    assertThat(patch).contains(TaintConstants.PROJECT_MANAGED_KEY);
    assertThat(patch).contains("Exists");
    // allowlist 분기가 선행되어 노드 조회 없이 반환되어야 한다
    verifyNoInteractions(this.mockPodNodesResolver);
  }

  @Test
  @DisplayName("제외 라벨이 붙은 파드라도 allowlist 네임스페이스면 toleration 패치를 주입한다(allowlist 우선)")
  void handle_excludedPodInAllowlistedNamespace_stillInjectsToleration() {
    V1AdmissionReview review = createReview("kubevirt", Map.of(EXCLUSION_LABEL, "true"));

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    // 제외 분기가 먼저였다면 무패치 허용이었을 것이다 — 패치 존재가 allowlist 선행의 증거다
    assertThat(review.getResponse().getPatch()).isNotNull();
    assertThat(decodePatch(review)).contains(TaintConstants.PROJECT_MANAGED_KEY);
  }

  @Test
  @DisplayName("allowlist가 아닌 네임스페이스에서 제외 라벨 파드는 무패치 허용한다")
  void handle_excludedPodInOrdinaryNamespace_allowsWithoutPatch() {
    V1AdmissionReview review = createReview("default", Map.of(EXCLUSION_LABEL, "true"));

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNull();
    verifyNoInteractions(this.mockPodNodesResolver);
  }

}
