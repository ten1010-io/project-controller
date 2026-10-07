package io.ten1010.aipub.projectcontroller.controller.watch;

import static org.assertj.core.api.Assertions.assertThat;

import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.ten1010.aipub.projectcontroller.domain.k8s.AnnotationConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

/**
 * 네임스페이스 watch 의 onUpdate 필터가 cueue 프로비저닝용 annotation 변경을 통과시키는지 검증한다.
 * informer resync period 가 0 이라 주기적 재리컨실이 없어서, 이 필터가 annotation 을 비교하지
 * 않으면 누가 node-selector annotation 을 지워도 Project ADD/DELETE 나 재기동 전까지 복구되지 않는다.
 */
class ProjectNamespaceFilterTest {

  private static final String PROJECT = "ten-test-project-01";

  private final BiPredicate<V1Namespace, V1Namespace> filter =
      new OnUpdateFilterFactory().projectNamespaceFilter();

  private static V1Namespace namespace(
      Map<String, String> labels, Map<String, String> annotations, String ownerUid) {
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName(PROJECT);
    meta.setLabels(labels);
    meta.setAnnotations(annotations);
    if (ownerUid != null) {
      V1OwnerReference owner = new V1OwnerReference();
      owner.setName(PROJECT);
      owner.setUid(ownerUid);
      owner.setKind("Project");
      owner.setApiVersion("project.aipub.ten1010.io/v1alpha1");
      meta.setOwnerReferences(List.of(owner));
    }
    V1Namespace namespace = new V1Namespace();
    namespace.setMetadata(meta);
    return namespace;
  }

  private static V1Namespace reconciled() {
    return namespace(
        Map.of(
            LabelConstants.PROJECT_LABEL_KEY, PROJECT,
            LabelConstants.CUEUE_PROVISIONING_ENABLED_KEY,
            LabelConstants.CUEUE_PROVISIONING_ENABLED_VALUE),
        Map.of(
            AnnotationConstants.CUEUE_NODE_SELECTOR_KEY,
            LabelConstants.PROJECT_NAME_KEY_PREFIX + PROJECT),
        "7d1c3e9a-4f2b-4c8e-9a1d-2b3c4d5e6f70");
  }

  @Test
  void nodeSelectorAnnotationRemoved_passesFilter() {
    V1Namespace stripped = namespace(
        reconciled().getMetadata().getLabels(),
        Map.of(),
        "7d1c3e9a-4f2b-4c8e-9a1d-2b3c4d5e6f70");

    assertThat(this.filter.test(reconciled(), stripped)).isTrue();
  }

  @Test
  void nodeSelectorAnnotationValueChanged_passesFilter() {
    V1Namespace tampered = namespace(
        reconciled().getMetadata().getLabels(),
        Map.of(AnnotationConstants.CUEUE_NODE_SELECTOR_KEY,
            LabelConstants.PROJECT_NAME_KEY_PREFIX + "other-project"),
        "7d1c3e9a-4f2b-4c8e-9a1d-2b3c4d5e6f70");

    assertThat(this.filter.test(reconciled(), tampered)).isTrue();
  }

  @Test
  void provisioningEnabledLabelRemoved_passesFilter() {
    V1Namespace stripped = namespace(
        Map.of(LabelConstants.PROJECT_LABEL_KEY, PROJECT),
        reconciled().getMetadata().getAnnotations(),
        "7d1c3e9a-4f2b-4c8e-9a1d-2b3c4d5e6f70");

    assertThat(this.filter.test(reconciled(), stripped)).isTrue();
  }

  @Test
  void ownerReferenceRemoved_passesFilter() {
    V1Namespace orphaned = namespace(
        reconciled().getMetadata().getLabels(),
        reconciled().getMetadata().getAnnotations(),
        null);

    assertThat(this.filter.test(reconciled(), orphaned)).isTrue();
  }

  @Test
  void nothingChanged_blocksFilter() {
    assertThat(this.filter.test(reconciled(), reconciled())).isFalse();
  }

  @Test
  void unrelatedAnnotationAdded_passesFilter() {
    V1Namespace annotated = namespace(
        reconciled().getMetadata().getLabels(),
        Map.of(
            AnnotationConstants.CUEUE_NODE_SELECTOR_KEY,
            LabelConstants.PROJECT_NAME_KEY_PREFIX + PROJECT,
            "kubectl.kubernetes.io/last-applied-configuration", "{}"),
        "7d1c3e9a-4f2b-4c8e-9a1d-2b3c4d5e6f70");

    assertThat(this.filter.test(reconciled(), annotated)).isTrue();
  }

}
