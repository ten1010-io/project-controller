package io.ten1010.aipub.projectcontroller.domain.k8s;

import static org.assertj.core.api.Assertions.assertThat;

import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.openapi.models.RbacV1Subject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
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

/** 멤버가 클러스터 전체 PV 를 열거하지 못하도록 list 없이 get + resourceNames 만 부여한다. */
class ReconciliationServicePersistentVolumeRulesTest {

  private ReconciliationService reconciliationService;
  private V1alpha1Project project;

  private static V1PersistentVolume persistentVolume(String name) {
    V1PersistentVolume pv = new V1PersistentVolume();
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName(name);
    pv.setMetadata(meta);
    return pv;
  }

  private static Optional<V1PolicyRule> findPersistentVolumeRule(List<V1PolicyRule> rules) {
    return rules.stream()
        .filter(rule -> rule.getResources() != null
            && rule.getResources().contains("persistentvolumes"))
        .findFirst();
  }

  private List<V1PolicyRule> reconcile(ProjectRoleEnum projectRoleEnum,
      List<V1PersistentVolume> readablePersistentVolumes) {
    return this.reconciliationService.reconcileClusterRoleRules(
        this.project, projectRoleEnum, List.of(), List.of(), List.of(), List.of(),
        readablePersistentVolumes);
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
  void reconcileClusterRoleRules_withReadablePersistentVolumes_grantsGetOnlyOnThoseNames() {
    for (ProjectRoleEnum projectRoleEnum : ProjectRoleEnum.values()) {
      List<V1PolicyRule> rules = reconcile(projectRoleEnum,
          List.of(persistentVolume("pv-b"), persistentVolume("pv-a")));

      V1PolicyRule rule = findPersistentVolumeRule(rules).orElseThrow();
      assertThat(rule.getApiGroups()).containsExactly("");
      // list 는 resourceNames 로 좁힐 수 없어 부여하지 않는다.
      assertThat(rule.getVerbs()).containsExactly("get");
      assertThat(rule.getResourceNames()).containsExactly("pv-a", "pv-b");
    }
  }

  @Test
  void reconcileClusterRoleRules_withoutReadablePersistentVolumes_omitsRule() {
    for (ProjectRoleEnum projectRoleEnum : ProjectRoleEnum.values()) {
      List<V1PolicyRule> rules = reconcile(projectRoleEnum, List.of());

      assertThat(findPersistentVolumeRule(rules)).isEmpty();
    }
  }

  @Test
  void reconcileClusterRoleRules_withUnsortedDuplicates_producesStableSortedNames() {
    // reconcileExistingRole 이 List.equals(순서 비교)로 판단하므로 입력 순서에 무관해야 한다.
    List<V1PolicyRule> first = reconcile(ProjectRoleEnum.PROJECT_DEVELOPER,
        List.of(persistentVolume("pv-c"), persistentVolume("pv-a"), persistentVolume("pv-c")));
    List<V1PolicyRule> second = reconcile(ProjectRoleEnum.PROJECT_DEVELOPER,
        List.of(persistentVolume("pv-a"), persistentVolume("pv-c")));

    assertThat(findPersistentVolumeRule(first).orElseThrow().getResourceNames())
        .containsExactly("pv-a", "pv-c");
    assertThat(first).isEqualTo(second);
  }

}
