package io.ten1010.aipub.projectcontroller.domain.k8s;

import static org.assertj.core.api.Assertions.assertThat;

import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.openapi.models.RbacV1Subject;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectMember;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.WorkloadExclusionResolver;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Cueue 프로비저닝 전제조건(노드 라벨 / 네임스페이스 라벨·어노테이션) 리컨실. */
class ReconciliationServiceCueueProvisioningTest {

  private static final String AUTO_ENQUEUE_KEY = "cueue.coaster.ten1010.io/auto-enqueue";

  private ReconciliationService reconciliationService;

  private static V1alpha1Project project(String name) {
    V1alpha1Project project = new V1alpha1Project();
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName(name);
    project.setMetadata(meta);
    return project;
  }

  private static V1Node node(Map<String, String> labels) {
    V1Node node = new V1Node();
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName("node1");
    meta.setLabels(new HashMap<>(labels));
    node.setMetadata(meta);
    return node;
  }

  private static V1Node managedNode(Map<String, String> extraLabels) {
    Map<String, String> labels = new HashMap<>(extraLabels);
    labels.put(LabelConstants.PROJECT_MANAGED_KEY, ProjectManagedValueEnum.TRUE.getStr());
    labels.put(LabelConstants.ISOLATION_MODE_KEY, IsolationModeValueEnum.LENIENT.getStr());
    return node(labels);
  }

  private static V1Namespace namespace(Map<String, String> labels,
      Map<String, String> annotations) {
    V1Namespace namespace = new V1Namespace();
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName("proj1");
    meta.setLabels(new HashMap<>(labels));
    meta.setAnnotations(new HashMap<>(annotations));
    namespace.setMetadata(meta);
    return namespace;
  }

  private static String projectNameKey(String projectName) {
    return LabelConstants.PROJECT_NAME_KEY_PREFIX + projectName;
  }

  @BeforeEach
  void setUp() {
    SubjectResolver subjectResolver = new SubjectResolver() {

      @Override
      public Optional<RbacV1Subject> resolve(V1alpha1ProjectMember member) {
        return Optional.empty();
      }

      @Override
      public Optional<RbacV1Subject> resolve(V1alpha1AipubUser user) {
        return Optional.empty();
      }

    };
    DockerConfigJsonResolver dockerConfigJsonResolver = new DockerConfigJsonResolver() {

      @Override
      public Map<String, Object> resolve(V1alpha1Project project) {
        return Map.of();
      }

      @Override
      public Optional<String> resolveImageRegistryRobotId(V1alpha1Project project) {
        return Optional.empty();
      }

    };
    this.reconciliationService = new ReconciliationService(
        subjectResolver,
        dockerConfigJsonResolver,
        List.of(),
        new WorkloadExclusionResolver(List.of()),
        new NamespaceAllowlistResolver(new Cache<>()));
  }

  @Test
  void boundProjects_getProjectNameLabelsOnManagedNode() {
    V1Node node = managedNode(Map.of());

    Map<String, String> reconciled = this.reconciliationService.reconcileNodeLabels(
        node, List.of(project("proj1"), project("proj2")));

    assertThat(reconciled).containsEntry(projectNameKey("proj1"), "")
        .containsEntry(projectNameKey("proj2"), "");
  }

  @Test
  void unboundProject_removesItsProjectNameLabel() {
    V1Node node = managedNode(Map.of(projectNameKey("proj1"), "", projectNameKey("proj2"), ""));

    Map<String, String> reconciled = this.reconciliationService.reconcileNodeLabels(
        node, List.of(project("proj1")));

    assertThat(reconciled).containsEntry(projectNameKey("proj1"), "")
        .doesNotContainKey(projectNameKey("proj2"));
  }

  @Test
  void noBoundProjects_removesAllProjectNameLabels() {
    V1Node node = managedNode(Map.of(projectNameKey("proj1"), "", projectNameKey("proj2"), ""));

    Map<String, String> reconciled = this.reconciliationService.reconcileNodeLabels(
        node, List.of());

    assertThat(reconciled).doesNotContainKeys(projectNameKey("proj1"), projectNameKey("proj2"));
  }

  @Test
  void unmanagedNode_getsNoProjectNameLabel() {
    V1Node node = node(Map.of());

    Map<String, String> reconciled = this.reconciliationService.reconcileNodeLabels(
        node, List.of(project("proj1")));

    assertThat(reconciled).doesNotContainKey(projectNameKey("proj1"));
    assertThat(reconciled).containsEntry(LabelConstants.PROJECT_MANAGED_KEY,
        ProjectManagedValueEnum.FALSE.getStr());
  }

  @Test
  void nodeLeavingProjectManaged_losesProjectNameLabels() {
    V1Node node = node(Map.of(
        LabelConstants.PROJECT_MANAGED_KEY, ProjectManagedValueEnum.FALSE.getStr(),
        projectNameKey("proj1"), ""));

    Map<String, String> reconciled = this.reconciliationService.reconcileNodeLabels(
        node, List.of(project("proj1")));

    assertThat(reconciled).doesNotContainKey(projectNameKey("proj1"));
  }

  @Test
  void nodeLabels_preserveUnknownLabels() {
    V1Node node = managedNode(Map.of("cueue.coaster.ten1010.io/managed", "true",
        "kubernetes.io/hostname", "node1"));

    Map<String, String> reconciled = this.reconciliationService.reconcileNodeLabels(
        node, List.of(project("proj1")));

    assertThat(reconciled).containsEntry("cueue.coaster.ten1010.io/managed", "true")
        .containsEntry("kubernetes.io/hostname", "node1");
  }

  @Test
  void nodeLabels_withReorderedBoundProjects_areIdempotent() {
    V1Node node = managedNode(Map.of());

    Map<String, String> once = this.reconciliationService.reconcileNodeLabels(
        node, List.of(project("proj1"), project("proj2")));
    node.getMetadata().setLabels(new HashMap<>(once));
    Map<String, String> twice = this.reconciliationService.reconcileNodeLabels(
        node, List.of(project("proj2"), project("proj1")));

    assertThat(twice).isEqualTo(once);
  }

  @Test
  void projectNamespace_getsProvisioningEnabledLabel() {
    V1Namespace namespace = namespace(Map.of(), Map.of());

    Map<String, String> reconciled = this.reconciliationService.reconcileNamespaceLabels(
        namespace, project("proj1"));

    assertThat(reconciled).containsEntry(LabelConstants.CUEUE_PROVISIONING_ENABLED_KEY, "true")
        .containsEntry(LabelConstants.PROJECT_LABEL_KEY, "proj1");
  }

  @Test
  void projectNamespace_getsNodeSelectorAnnotation() {
    V1Namespace namespace = namespace(Map.of(), Map.of());

    Map<String, String> reconciled = this.reconciliationService.reconcileNamespaceAnnotations(
        namespace, project("proj1"));

    assertThat(reconciled).containsEntry(AnnotationConstants.CUEUE_NODE_SELECTOR_KEY,
        projectNameKey("proj1"));
  }

  @Test
  void noProject_removesCueueLabelAndAnnotation() {
    V1Namespace namespace = namespace(
        Map.of(LabelConstants.CUEUE_PROVISIONING_ENABLED_KEY, "true",
            LabelConstants.PROJECT_LABEL_KEY, "proj1"),
        Map.of(AnnotationConstants.CUEUE_NODE_SELECTOR_KEY, projectNameKey("proj1")));

    Map<String, String> reconciledLabels = this.reconciliationService.reconcileNamespaceLabels(
        namespace, null);
    Map<String, String> reconciledAnnotations =
        this.reconciliationService.reconcileNamespaceAnnotations(namespace, null);

    assertThat(reconciledLabels).doesNotContainKey(LabelConstants.CUEUE_PROVISIONING_ENABLED_KEY);
    assertThat(reconciledAnnotations)
        .doesNotContainKey(AnnotationConstants.CUEUE_NODE_SELECTOR_KEY);
  }

  @Test
  void externalAutoEnqueueLabel_isPreserved() {
    V1Namespace namespace = namespace(
        Map.of(AUTO_ENQUEUE_KEY, "true", "some.other/label", "kept"), Map.of());

    Map<String, String> reconciled = this.reconciliationService.reconcileNamespaceLabels(
        namespace, project("proj1"));

    assertThat(reconciled).containsEntry(AUTO_ENQUEUE_KEY, "true")
        .containsEntry("some.other/label", "kept");
  }

  @Test
  void namespaceAnnotations_preserveUnknownAnnotations() {
    V1Namespace namespace = namespace(Map.of(), Map.of("some.other/annotation", "kept"));

    Map<String, String> reconciled = this.reconciliationService.reconcileNamespaceAnnotations(
        namespace, project("proj1"));

    assertThat(reconciled).containsEntry("some.other/annotation", "kept");
  }

  @Test
  void namespaceLabelsAndAnnotations_areIdempotent() {
    V1Namespace namespace = namespace(Map.of(AUTO_ENQUEUE_KEY, "true"), Map.of());
    V1alpha1Project project = project("proj1");

    Map<String, String> labelsOnce = this.reconciliationService.reconcileNamespaceLabels(
        namespace, project);
    Map<String, String> annotationsOnce =
        this.reconciliationService.reconcileNamespaceAnnotations(namespace, project);
    namespace.getMetadata().setLabels(new HashMap<>(labelsOnce));
    namespace.getMetadata().setAnnotations(new HashMap<>(annotationsOnce));

    assertThat(this.reconciliationService.reconcileNamespaceLabels(namespace, project))
        .isEqualTo(labelsOnce);
    assertThat(this.reconciliationService.reconcileNamespaceAnnotations(namespace, project))
        .isEqualTo(annotationsOnce);
  }

  @Test
  void namespaceWithoutMetadataMaps_getsCueueLabelAndAnnotation() {
    V1Namespace namespace = new V1Namespace();
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName("proj1");
    namespace.setMetadata(meta);
    V1alpha1Project project = project("proj1");

    assertThat(this.reconciliationService.reconcileNamespaceLabels(namespace, project))
        .containsEntry(LabelConstants.CUEUE_PROVISIONING_ENABLED_KEY, "true");
    assertThat(this.reconciliationService.reconcileNamespaceAnnotations(namespace, project))
        .containsEntry(AnnotationConstants.CUEUE_NODE_SELECTOR_KEY, projectNameKey("proj1"));
  }

}
