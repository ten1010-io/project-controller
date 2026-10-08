package io.ten1010.aipub.projectcontroller.domain.k8s;

import static org.assertj.core.api.Assertions.assertThat;

import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.openapi.models.RbacV1Subject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PolicyRule;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectMember;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.WorkloadExclusionResolver;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Cueue 는 Cluster scope 라 list 를 주면 resourceNames 가 무시돼 전체 프로젝트 목록이 노출된다. */
class ReconciliationServiceCueueRulesTest {

  private ReconciliationService reconciliationService;
  private V1alpha1Project project;

  private static Optional<V1PolicyRule> findCueueRule(List<V1PolicyRule> rules) {
    return rules.stream()
        .filter(rule -> rule.getResources() != null
            && rule.getResources().contains(ProjectApiConstants.CUEUE_RESOURCE_PLURAL))
        .findFirst();
  }

  private List<V1PolicyRule> reconcile(ProjectRoleEnum projectRoleEnum) {
    return this.reconciliationService.reconcileClusterRoleRules(
        this.project, projectRoleEnum, List.of(), List.of(), List.of(), List.of(), List.of());
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

    this.project = new V1alpha1Project();
    V1ObjectMeta projectMeta = new V1ObjectMeta();
    projectMeta.setName("proj1");
    this.project.setMetadata(projectMeta);
  }

  @Test
  void managerRole_grantsGetPatchUpdateOnOwnCueue() {
    V1PolicyRule rule = findCueueRule(reconcile(ProjectRoleEnum.PROJECT_MANAGER)).orElseThrow();

    assertThat(rule.getApiGroups()).containsExactly(ProjectApiConstants.COASTER_GROUP);
    assertThat(rule.getResourceNames()).containsExactly("proj1");
    assertThat(rule.getVerbs()).containsExactly("get", "patch", "update");
  }

  @Test
  void developerRole_grantsGetOnlyOnOwnCueue() {
    V1PolicyRule rule = findCueueRule(reconcile(ProjectRoleEnum.PROJECT_DEVELOPER)).orElseThrow();

    assertThat(rule.getApiGroups()).containsExactly(ProjectApiConstants.COASTER_GROUP);
    assertThat(rule.getResourceNames()).containsExactly("proj1");
    assertThat(rule.getVerbs()).containsExactly("get");
  }

  @Test
  void cueueRule_neverGrantsList() {
    for (ProjectRoleEnum projectRoleEnum : ProjectRoleEnum.values()) {
      V1PolicyRule rule = findCueueRule(reconcile(projectRoleEnum)).orElseThrow();

      assertThat(rule.getVerbs()).doesNotContain("list", "watch", "deletecollection");
      assertThat(rule.getResourceNames()).isNotEmpty();
    }
  }

  @Test
  void repeatedReconcile_producesIdenticalRules() {
    for (ProjectRoleEnum projectRoleEnum : ProjectRoleEnum.values()) {
      assertThat(reconcile(projectRoleEnum)).isEqualTo(reconcile(projectRoleEnum));
    }
  }

}
