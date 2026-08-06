package io.ten1010.aipub.projectcontroller.mutating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkloadLabelReviewHandlerTest {

  private WorkloadLabelReviewHandler handler;
  private ApiResourceDiscovery mockDiscovery;
  private ApiClient mockApiClient;
  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    this.mockDiscovery = mock(ApiResourceDiscovery.class);
    this.mockApiClient = mock(ApiClient.class);
    Cache<V1Namespace> namespaceCache = new Cache<>();
    namespaceCache.add(new V1Namespace().metadata(new V1ObjectMeta()
        .name("kubevirt")
        .labels(Map.of(LabelConstants.ALLOWLISTED_KEY, "true"))));
    this.handler = new WorkloadLabelReviewHandler(this.mockDiscovery, this.mockApiClient,
        new NamespaceAllowlistResolver(namespaceCache));
    this.mapper = new ObjectMapperFactory().createObjectMapper();
  }

  private V1AdmissionReview createReview(String namespace) {
    V1AdmissionReviewRequest request = new V1AdmissionReviewRequest();
    request.setUid("test-uid");
    request.setOperation("CREATE");
    request.setNamespace(namespace);
    request.setObject(this.mapper.createObjectNode().putObject("metadata").objectNode());

    V1AdmissionReview review = new V1AdmissionReview();
    review.setApiVersion("admission.k8s.io/v1");
    review.setKind("AdmissionReview");
    review.setRequest(request);

    return review;
  }

  @Test
  @DisplayName("allowlist 네임스페이스의 요청은 owner 조회 없이 무패치 허용한다")
  void handle_allowlistedNamespace_allowsWithoutPatch() {
    V1AdmissionReview review = createReview("kubevirt");

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNull();
    verifyNoInteractions(this.mockDiscovery, this.mockApiClient);
  }

  @Test
  @DisplayName("allowlist가 아닌 네임스페이스에서 owner가 없으면 무패치 허용한다")
  void handle_nonAllowlistedNamespaceWithoutOwner_allowsWithoutPatch() {
    V1AdmissionReview review = createReview("default");

    this.handler.handle(review);

    assertThat(review.getResponse()).isNotNull();
    assertThat(review.getResponse().getAllowed()).isTrue();
    assertThat(review.getResponse().getPatch()).isNull();
  }

}
