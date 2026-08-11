package io.ten1010.aipub.projectcontroller.domain.k8s;

import static org.assertj.core.api.Assertions.assertThat;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PolicyRule;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.WorkloadExclusionResolver;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReconciliationServiceOwnedRulesTest {

  private ReconciliationService reconciliationService;
  private V1alpha1AipubUser user;
  private V1alpha1Project project;

  @BeforeEach
  void setUp() {
    SubjectResolver subjectResolver = new SubjectResolver() {

      @Override
      public Optional<io.kubernetes.client.openapi.models.RbacV1Subject> resolve(
          io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectMember member) {
        return Optional.empty();
      }

      @Override
      public Optional<io.kubernetes.client.openapi.models.RbacV1Subject> resolve(
          V1alpha1AipubUser user) {
        return Optional.empty();
      }

    };
    this.reconciliationService = new ReconciliationService(
        subjectResolver,
        project -> java.util.Map.of(),
        List.of(),
        new WorkloadExclusionResolver(List.of()));

    this.user = new V1alpha1AipubUser();
    V1ObjectMeta userMeta = new V1ObjectMeta();
    userMeta.setName("alice");
    this.user.setMetadata(userMeta);

    this.project = new V1alpha1Project();
    V1ObjectMeta projectMeta = new V1ObjectMeta();
    projectMeta.setName("proj1");
    this.project.setMetadata(projectMeta);
  }

  @Test
  void reconcileAipubUserRoleRules_withOwnedObjects_appendsOwnedRules() {
    List<V1PolicyRule> rules = this.reconciliationService.reconcileAipubUserRoleRules(
        this.user, this.project, List.of(),
        List.of(new OwnedObject("", "configmaps", "my-config")));

    assertThat(rules).hasSize(1);
    V1PolicyRule rule = rules.get(0);
    assertThat(rule.getApiGroups()).containsExactly("");
    assertThat(rule.getResources()).containsExactly("configmaps");
    assertThat(rule.getResourceNames()).containsExactly("my-config");
    // 기존 워크로드 경로(buildUpdateDeleteRoleRule)와 동일한 verbs
    assertThat(rule.getVerbs()).containsExactly("update", "patch", "delete");
  }

  @Test
  void reconcileAipubUserRoleRules_withoutOwnedObjects_returnsSameAsLegacyOverload() {
    List<V1PolicyRule> legacy = this.reconciliationService.reconcileAipubUserRoleRules(
        this.user, this.project, List.of());
    List<V1PolicyRule> rules = this.reconciliationService.reconcileAipubUserRoleRules(
        this.user, this.project, List.of(), List.of());

    assertThat(rules).isEqualTo(legacy);
  }

  @Test
  void ownershipPolicy_targetsMatchUserFacingSixteen() {
    assertThat(OwnershipPolicy.OWNED_TARGETS).hasSize(16);
    assertThat(OwnershipPolicy.OWNED_TARGETS)
        .extracting(ResourceTarget::plural)
        .containsExactlyInAnyOrder(
            "pods", "configmaps", "secrets", "services", "endpoints", "serviceaccounts",
            "events", "persistentvolumeclaims", "limitranges",
            "deployments", "replicasets", "statefulsets", "daemonsets",
            "horizontalpodautoscalers", "poddisruptionbudgets", "ingresses");
  }

}
