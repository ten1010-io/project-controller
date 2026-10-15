package io.ten1010.aipub.projectcontroller.domain.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PolicyRule;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1WorkloadGroup;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.WorkloadExclusionResolver;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * WorkloadGroup(namespaced, aipub.ten1010.io/v1alpha1) 의 소유자 기반 권한 경로 검증.
 * 프로젝트 Role 은 조회·생성만 주고, 수정/삭제는 생성자 본인의 AipubUser Role 에
 * resourceName 단위로만 붙는다 — FileServer 와 동일한 계약이다.
 */
class ReconciliationServiceWorkloadGroupRulesTest {

  private ReconciliationService reconciliationService;
  private V1alpha1AipubUser user;
  private V1alpha1Project project;

  private static V1alpha1WorkloadGroup workloadGroup(String name, String ownerUsername) {
    V1alpha1WorkloadGroup workloadGroup = new V1alpha1WorkloadGroup();
    workloadGroup.setApiVersion(ProjectApiConstants.AIPUB_API_VERSION);
    workloadGroup.setKind(ProjectApiConstants.WORKLOAD_GROUP_RESOURCE_KIND);
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName(name);
    meta.setNamespace("proj1");
    meta.setLabels(Map.of(LabelConstants.OBJECT_OWN_USERNAME_KEY, ownerUsername));
    workloadGroup.setMetadata(meta);
    return workloadGroup;
  }

  private static V1PolicyRule findRule(List<V1PolicyRule> rules, String resource) {
    return rules.stream()
        .filter(rule -> rule.getResources() != null && rule.getResources().contains(resource))
        .findAny()
        .orElseThrow(() -> new AssertionError("no rule for " + resource));
  }

  @BeforeEach
  void setUp() {
    this.reconciliationService = new ReconciliationService(
        mock(SubjectResolver.class),
        mock(DockerConfigJsonResolver.class),
        List.of(),
        new WorkloadExclusionResolver(List.of()),
        new NamespaceAllowlistResolver(new Cache<>()));

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
  @DisplayName("developer Role 은 workloadgroups 에 조회·생성만 부여한다")
  void developerRole_grantsBasicVerbsOnWorkloadGroups() {
    List<V1PolicyRule> rules = this.reconciliationService.reconcileProjectRoleRules(
        this.project, ProjectRoleEnum.PROJECT_DEVELOPER);

    V1PolicyRule rule = findRule(rules, ProjectApiConstants.WORKLOAD_GROUP_RESOURCE_PLURAL);
    assertThat(rule.getApiGroups()).containsExactly(ProjectApiConstants.AIPUB_GROUP);
    assertThat(rule.getVerbs()).containsExactlyInAnyOrder("create", "get", "watch", "list");
    assertThat(rule.getVerbs()).doesNotContain("update", "patch", "delete");
    assertThat(rule.getResourceNames()).isNullOrEmpty();
  }

  @Test
  @DisplayName("manager Role 은 workloadgroups 에 전권을 부여한다 (fileservers 와 동일)")
  void managerRole_grantsAllVerbsOnWorkloadGroups() {
    List<V1PolicyRule> rules = this.reconciliationService.reconcileProjectRoleRules(
        this.project, ProjectRoleEnum.PROJECT_MANAGER);

    V1PolicyRule workloadGroupRule = findRule(rules,
        ProjectApiConstants.WORKLOAD_GROUP_RESOURCE_PLURAL);
    V1PolicyRule fileServerRule = findRule(rules,
        ProjectApiConstants.FILE_SERVER_RESOURCE_PLURAL);
    assertThat(workloadGroupRule.getApiGroups()).containsExactly(ProjectApiConstants.AIPUB_GROUP);
    assertThat(workloadGroupRule.getVerbs()).isEqualTo(fileServerRule.getVerbs());
    assertThat(workloadGroupRule.getVerbs()).containsExactly("*");
  }

  @Test
  @DisplayName("본인이 만든 WorkloadGroup 에는 update/patch/delete 가 resourceName 단위로 붙는다")
  void ownedWorkloadGroup_getsUpdateDeleteRuleScopedToName() {
    List<KubernetesObject> workloads = List.of(workloadGroup("alice-wg", "alice"));

    List<V1PolicyRule> rules = this.reconciliationService.reconcileAipubUserRoleRules(
        this.user, this.project, workloads);

    assertThat(rules).hasSize(1);
    V1PolicyRule rule = rules.get(0);
    assertThat(rule.getApiGroups()).containsExactly(ProjectApiConstants.AIPUB_GROUP);
    assertThat(rule.getResources())
        .containsExactly(ProjectApiConstants.WORKLOAD_GROUP_RESOURCE_PLURAL);
    assertThat(rule.getResourceNames()).containsExactly("alice-wg");
    assertThat(rule.getVerbs()).containsExactly("update", "patch", "delete");
  }

  @Test
  @DisplayName("다른 사용자가 만든 WorkloadGroup 에는 아무 권한도 붙지 않는다")
  void otherUsersWorkloadGroup_getsNoRule() {
    List<KubernetesObject> workloads = List.of(workloadGroup("bob-wg", "bob"));

    List<V1PolicyRule> rules = this.reconciliationService.reconcileAipubUserRoleRules(
        this.user, this.project, workloads);

    assertThat(rules).isEmpty();
  }

  @Test
  @DisplayName("소유자 라벨이 없는 WorkloadGroup 에는 아무 권한도 붙지 않는다")
  void unlabeledWorkloadGroup_getsNoRule() {
    V1alpha1WorkloadGroup unlabeled = new V1alpha1WorkloadGroup();
    unlabeled.setKind(ProjectApiConstants.WORKLOAD_GROUP_RESOURCE_KIND);
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName("orphan-wg");
    meta.setNamespace("proj1");
    unlabeled.setMetadata(meta);

    List<V1PolicyRule> rules = this.reconciliationService.reconcileAipubUserRoleRules(
        this.user, this.project, List.of(unlabeled));

    assertThat(rules).isEmpty();
  }

  @Test
  @DisplayName("kind 가 비어도 instanceof 폴백으로 workloadgroups 로 해석된다")
  void resourceResolver_fallsBackToTypeWhenKindMissing() {
    WorkloadResourceResolver resolver = new WorkloadResourceResolver();
    V1alpha1WorkloadGroup withKind = workloadGroup("alice-wg", "alice");
    V1alpha1WorkloadGroup withoutKind = workloadGroup("alice-wg", "alice");
    withoutKind.setKind(null);

    assertThat(resolver.resolveResource(withKind))
        .contains(ProjectApiConstants.WORKLOAD_GROUP_RESOURCE_PLURAL);
    assertThat(resolver.resolveResource(withoutKind))
        .contains(ProjectApiConstants.WORKLOAD_GROUP_RESOURCE_PLURAL);
    assertThat(resolver.resolveGroup(withKind)).isEqualTo(ProjectApiConstants.AIPUB_GROUP);
  }

}
