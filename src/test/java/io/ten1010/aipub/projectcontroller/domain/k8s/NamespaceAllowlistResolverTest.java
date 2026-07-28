package io.ten1010.aipub.projectcontroller.domain.k8s;

import static org.assertj.core.api.Assertions.assertThat;

import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NamespaceAllowlistResolverTest {

  private static V1Namespace namespaceWithLabels(String name, Map<String, String> labels) {
    return new V1Namespace().metadata(new V1ObjectMeta().name(name).labels(labels));
  }

  private static NamespaceAllowlistResolver resolverWith(V1Namespace... namespaces) {
    Cache<V1Namespace> cache = new Cache<>();
    for (V1Namespace namespace : namespaces) {
      cache.add(namespace);
    }
    return new NamespaceAllowlistResolver(cache);
  }

  @Test
  @DisplayName("allowlisted 라벨 값이 true면 allowlist로 판정한다(대소문자 무관)")
  void givenAllowlistedLabelTrue_thenAllowlisted() {
    NamespaceAllowlistResolver resolver = resolverWith(
        namespaceWithLabels("kubevirt", Map.of(LabelConstants.ALLOWLISTED_KEY, "true")),
        namespaceWithLabels("monitoring", Map.of(LabelConstants.ALLOWLISTED_KEY, "TRUE")));

    assertThat(resolver.isAllowlisted("kubevirt")).isTrue();
    assertThat(resolver.isAllowlisted("monitoring")).isTrue();
  }

  @Test
  @DisplayName("allowlisted 라벨 값이 true가 아니면 allowlist가 아니다")
  void givenAllowlistedLabelNotTrue_thenNotAllowlisted() {
    NamespaceAllowlistResolver resolver = resolverWith(
        namespaceWithLabels("ns-false", Map.of(LabelConstants.ALLOWLISTED_KEY, "false")),
        namespaceWithLabels("ns-junk", Map.of(LabelConstants.ALLOWLISTED_KEY, "yes")));

    assertThat(resolver.isAllowlisted("ns-false")).isFalse();
    assertThat(resolver.isAllowlisted("ns-junk")).isFalse();
  }

  @Test
  @DisplayName("allowlisted 라벨이 없으면 allowlist가 아니다")
  void givenNoAllowlistedLabel_thenNotAllowlisted() {
    NamespaceAllowlistResolver resolver = resolverWith(
        namespaceWithLabels("plain", Map.of("other-label", "true")));

    assertThat(resolver.isAllowlisted("plain")).isFalse();
  }

  @Test
  @DisplayName("캐시에 없는 네임스페이스는 allowlist가 아니다(fail-closed)")
  void givenNamespaceNotInCache_thenNotAllowlisted() {
    NamespaceAllowlistResolver resolver = resolverWith();

    assertThat(resolver.isAllowlisted("unknown")).isFalse();
  }

  @Test
  @DisplayName("null이나 빈 이름은 allowlist가 아니다")
  void givenNullOrBlankName_thenNotAllowlisted() {
    NamespaceAllowlistResolver resolver = resolverWith(
        namespaceWithLabels("kubevirt", Map.of(LabelConstants.ALLOWLISTED_KEY, "true")));

    assertThat(resolver.isAllowlisted((String) null)).isFalse();
    assertThat(resolver.isAllowlisted("")).isFalse();
    assertThat(resolver.isAllowlisted("  ")).isFalse();
  }

  @Test
  @DisplayName("static 오버로드는 네임스페이스 오브젝트의 라벨만으로 판정한다")
  void givenNamespaceObject_thenStaticCheckWorks() {
    assertThat(NamespaceAllowlistResolver.isAllowlisted(
        namespaceWithLabels("a", Map.of(LabelConstants.ALLOWLISTED_KEY, "true")))).isTrue();
    assertThat(NamespaceAllowlistResolver.isAllowlisted(
        namespaceWithLabels("b", Map.of()))).isFalse();
  }

}
