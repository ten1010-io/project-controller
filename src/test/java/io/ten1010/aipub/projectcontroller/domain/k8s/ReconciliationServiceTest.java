package io.ten1010.aipub.projectcontroller.domain.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.WorkloadExclusionResolver;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReconciliationServiceTest {

  private static ReconciliationService createService() {
    return new ReconciliationService(
        mock(SubjectResolver.class),
        mock(DockerConfigJsonResolver.class),
        List.of(),
        new WorkloadExclusionResolver(List.of()),
        new NamespaceAllowlistResolver(new Cache<>()));
  }

  private static V1Pod podWithTolerations(List<V1Toleration> tolerations) {
    return new V1Pod().spec(new V1PodSpec().tolerations(tolerations));
  }

  private static V1Toleration existsToleration(String effect) {
    return new V1Toleration()
        .key(TaintConstants.PROJECT_MANAGED_KEY)
        .operator("Exists")
        .effect(effect);
  }

  @Test
  @DisplayName("allowlist reconcile은 project managed 키의 Exists toleration 쌍을 추가한다")
  void givenNoTolerations_whenAllowlistReconcile_thenExistsPairAdded() {
    ReconciliationService service = createService();

    List<V1Toleration> reconciled = service.reconcileTolerationsForAllowlistedNamespace(
        podWithTolerations(null));

    assertThat(reconciled).containsExactly(
        existsToleration(TaintConstants.NO_SCHEDULE_EFFECT),
        existsToleration(TaintConstants.NO_EXECUTE_EFFECT));
  }

  @Test
  @DisplayName("allowlist reconcile은 기존 toleration을 재작성 없이 그대로 보존한다(catch-all 포함)")
  void givenExistingTolerations_whenAllowlistReconcile_thenPreservedVerbatim() {
    ReconciliationService service = createService();
    // 일반 경로의 replaceAllKeyAllEffectTolerations가 재작성해버릴 catch-all toleration(key/effect
    // 없음)이, allowlist 경로에서는 그대로 보존되어야 한다.
    V1Toleration catchAll = new V1Toleration().operator("Exists");
    V1Toleration custom = new V1Toleration()
        .key("kubevirt.io/drain").operator("Exists").effect(TaintConstants.NO_SCHEDULE_EFFECT);

    List<V1Toleration> reconciled = service.reconcileTolerationsForAllowlistedNamespace(
        podWithTolerations(new ArrayList<>(List.of(catchAll, custom))));

    assertThat(reconciled).containsExactly(
        catchAll,
        custom,
        existsToleration(TaintConstants.NO_SCHEDULE_EFFECT),
        existsToleration(TaintConstants.NO_EXECUTE_EFFECT));
  }

  @Test
  @DisplayName("allowlist reconcile은 기존 per-node Equal toleration을 제거하고 Exists 쌍으로 대체한다")
  void givenPerNodeEqualTolerations_whenAllowlistReconcile_thenReplacedByExistsPair() {
    ReconciliationService service = createService();
    V1Toleration perNodeEqual = new V1Toleration()
        .key(TaintConstants.PROJECT_MANAGED_KEY)
        .operator("Equal")
        .value("node-1")
        .effect(TaintConstants.NO_SCHEDULE_EFFECT);

    List<V1Toleration> reconciled = service.reconcileTolerationsForAllowlistedNamespace(
        podWithTolerations(new ArrayList<>(List.of(perNodeEqual))));

    assertThat(reconciled).containsExactly(
        existsToleration(TaintConstants.NO_SCHEDULE_EFFECT),
        existsToleration(TaintConstants.NO_EXECUTE_EFFECT));
  }

  @Test
  @DisplayName("allowlist reconcile은 멱등이다(두 번 적용해도 쌍이 중복되지 않는다)")
  void givenAlreadyReconciled_whenAllowlistReconcileAgain_thenIdempotent() {
    ReconciliationService service = createService();
    V1PodTemplateSpec templateSpec = new V1PodTemplateSpec().spec(new V1PodSpec());

    List<V1Toleration> once = service.reconcileTolerationsForAllowlistedNamespace(templateSpec);
    templateSpec.getSpec().tolerations(once);
    List<V1Toleration> twice = service.reconcileTolerationsForAllowlistedNamespace(templateSpec);

    assertThat(twice).isEqualTo(once);
  }

}
