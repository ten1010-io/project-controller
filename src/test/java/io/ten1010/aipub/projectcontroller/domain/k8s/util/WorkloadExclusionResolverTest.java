package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkloadExclusionResolverTest {

  private static V1DaemonSet daemonSetWithLabels(Map<String, String> labels) {
    return new V1DaemonSet().metadata(new V1ObjectMeta().labels(labels));
  }

  @Test
  @DisplayName("key=value 셀렉터는 라벨 값이 정확히 일치할 때만 제외한다")
  void givenKeyValueSelector_whenLabelMatches_thenExcluded() {
    WorkloadExclusionResolver resolver = new WorkloadExclusionResolver(
        List.of("app.kubernetes.io/part-of=cilium"));

    assertThat(resolver.isExcluded(
        daemonSetWithLabels(Map.of("app.kubernetes.io/part-of", "cilium")))).isTrue();
    assertThat(resolver.isExcluded(
        daemonSetWithLabels(Map.of("app.kubernetes.io/part-of", "calico")))).isFalse();
  }

  @Test
  @DisplayName("key 셀렉터(값 생략)는 라벨 키가 존재하면 값과 무관하게 제외한다")
  void givenKeyOnlySelector_whenLabelKeyExists_thenExcluded() {
    WorkloadExclusionResolver resolver = new WorkloadExclusionResolver(List.of("kubevirt.io"));

    assertThat(resolver.isExcluded(
        daemonSetWithLabels(Map.of("kubevirt.io", "virt-handler")))).isTrue();
    assertThat(resolver.isExcluded(
        daemonSetWithLabels(Map.of("kubevirt.io", "")))).isTrue();
    assertThat(resolver.isExcluded(
        daemonSetWithLabels(Map.of("other.io", "x")))).isFalse();
  }

  @Test
  @DisplayName("여러 셀렉터 중 하나라도 매칭되면 제외한다(OR)")
  void givenMultipleSelectors_whenAnyMatches_thenExcluded() {
    WorkloadExclusionResolver resolver = new WorkloadExclusionResolver(
        List.of("app.kubernetes.io/part-of=cilium", "kubevirt.io"));

    assertThat(resolver.isExcluded(
        daemonSetWithLabels(Map.of("kubevirt.io", "virt-handler")))).isTrue();
    assertThat(resolver.isExcluded(
        daemonSetWithLabels(Map.of("app.kubernetes.io/part-of", "cilium")))).isTrue();
    assertThat(resolver.isExcluded(
        daemonSetWithLabels(Map.of("app.kubernetes.io/name", "coredns")))).isFalse();
  }

  @Test
  @DisplayName("공백/빈 항목/null 셀렉터는 무시한다")
  void givenBlankOrNullSelectors_thenIgnored() {
    WorkloadExclusionResolver resolver = new WorkloadExclusionResolver(
        java.util.Arrays.asList("  ", "", "  kubevirt.io  ", null));

    assertThat(resolver.isExcluded(
        daemonSetWithLabels(Map.of("kubevirt.io", "virt-api")))).isTrue();
    assertThat(resolver.isExcluded(
        daemonSetWithLabels(Map.of("unrelated", "x")))).isFalse();
  }

  @Test
  @DisplayName("셀렉터 목록이 비어 있으면 어떤 워크로드도 제외하지 않는다")
  void givenEmptySelectors_thenNothingExcluded() {
    WorkloadExclusionResolver resolver = new WorkloadExclusionResolver(List.of());

    assertThat(resolver.isExcluded(
        daemonSetWithLabels(Map.of("kubevirt.io", "virt-handler")))).isFalse();
  }

  @Test
  @DisplayName("metadata나 labels가 없으면 제외하지 않는다")
  void givenNoMetadataOrLabels_thenNotExcluded() {
    WorkloadExclusionResolver resolver = new WorkloadExclusionResolver(List.of("kubevirt.io"));

    assertThat(resolver.isExcluded(new V1DaemonSet())).isFalse();
    assertThat(resolver.isExcluded(
        new V1DaemonSet().metadata(new V1ObjectMeta()))).isFalse();
  }

  @Test
  @DisplayName("null 셀렉터 목록이 주입돼도 안전하게 동작한다")
  void givenNullSelectorList_thenNothingExcluded() {
    WorkloadExclusionResolver resolver = new WorkloadExclusionResolver(null);

    assertThat(resolver.isExcluded(
        daemonSetWithLabels(Map.of("kubevirt.io", "virt-handler")))).isFalse();
  }
}
