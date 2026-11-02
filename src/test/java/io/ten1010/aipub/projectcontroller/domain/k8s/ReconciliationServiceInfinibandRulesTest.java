package io.ten1010.aipub.projectcontroller.domain.k8s;

import static org.assertj.core.api.Assertions.assertThat;

import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.openapi.models.RbacV1Subject;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PolicyRule;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectMember;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.WorkloadExclusionResolver;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * IBNetwork·NetworkNode 는 Cluster scope 라 list 를 주면 resourceNames 가 무시돼 전 네트워크와 전 노드의 장치 상태가
 * 노출된다. 이름으로 좁힌 get 만 준다 - IBNetwork 는 프로젝트 이름과 공용 네트워크(shared), NetworkNode 는 노드 이름이다.
 */
class ReconciliationServiceInfinibandRulesTest {

  private ReconciliationService reconciliationService;
  private V1alpha1Project project;

  private static Optional<V1PolicyRule> findRule(List<V1PolicyRule> rules, String resource) {
    return rules.stream()
        .filter(rule -> rule.getResources() != null && rule.getResources().contains(resource))
        .findFirst();
  }

  private static V1Node node(String name) {
    V1Node node = new V1Node();
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName(name);
    node.setMetadata(meta);
    return node;
  }

  private List<V1PolicyRule> reconcile(ProjectRoleEnum projectRoleEnum, String... nodeNames) {
    List<V1Node> bindingNodes = Arrays.stream(nodeNames)
        .map(ReconciliationServiceInfinibandRulesTest::node)
        .toList();
    return this.reconciliationService.reconcileClusterRoleRules(
        this.project,
        projectRoleEnum,
        List.of(),
        bindingNodes,
        List.of(),
        List.of(),
        List.of());
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
  void bothRoles_grantGetOnOwnAndSharedIbNetworks() {
    for (ProjectRoleEnum projectRoleEnum : ProjectRoleEnum.values()) {
      V1PolicyRule rule = findRule(
          reconcile(projectRoleEnum, "node1"),
          ProjectApiConstants.IB_NETWORK_RESOURCE_PLURAL).orElseThrow();

      assertThat(rule.getApiGroups()).containsExactly(ProjectApiConstants.AIPUB_GROUP);
      assertThat(rule.getResourceNames()).containsExactly("proj1", "shared");
      assertThat(rule.getVerbs()).containsExactly("get");
    }
  }

  @Test
  void bothRoles_grantGetOnBindingNodeNetworkNodes() {
    for (ProjectRoleEnum projectRoleEnum : ProjectRoleEnum.values()) {
      V1PolicyRule rule = findRule(
          reconcile(projectRoleEnum, "node1", "node2"),
          ProjectApiConstants.NETWORK_NODE_RESOURCE_PLURAL).orElseThrow();

      assertThat(rule.getApiGroups()).containsExactly(ProjectApiConstants.AIPUB_GROUP);
      assertThat(rule.getResourceNames()).containsExactly("node1", "node2");
      assertThat(rule.getVerbs()).containsExactly("get");
    }
  }

  @Test
  void infinibandRules_neverGrantListOrWrite() {
    for (ProjectRoleEnum projectRoleEnum : ProjectRoleEnum.values()) {
      List<V1PolicyRule> rules = reconcile(projectRoleEnum, "node1");

      for (String resource : List.of(
          ProjectApiConstants.IB_NETWORK_RESOURCE_PLURAL,
          ProjectApiConstants.NETWORK_NODE_RESOURCE_PLURAL)) {
        V1PolicyRule rule = findRule(rules, resource).orElseThrow();

        assertThat(rule.getVerbs()).containsExactly("get");
        assertThat(rule.getResourceNames()).isNotEmpty();
      }
    }
  }

  @Test
  void noBindingNode_omitsNetworkNodeRule() {
    for (ProjectRoleEnum projectRoleEnum : ProjectRoleEnum.values()) {
      List<V1PolicyRule> rules = reconcile(projectRoleEnum);

      // 빈 resourceNames 는 RBAC 에서 전체 허용이라 규칙 자체를 넣지 않는다.
      assertThat(findRule(rules, ProjectApiConstants.NETWORK_NODE_RESOURCE_PLURAL)).isEmpty();
      assertThat(findRule(rules, ProjectApiConstants.IB_NETWORK_RESOURCE_PLURAL)).isPresent();
    }
  }

  @Test
  void projectNamedShared_grantsSingleName() {
    this.project.getMetadata().setName("shared");
    for (ProjectRoleEnum projectRoleEnum : ProjectRoleEnum.values()) {
      V1PolicyRule rule = findRule(
          reconcile(projectRoleEnum, "node1"),
          ProjectApiConstants.IB_NETWORK_RESOURCE_PLURAL).orElseThrow();

      assertThat(rule.getResourceNames()).containsExactly("shared");
    }
  }

  @Test
  void neverGrantsRemovedSriovNodeStates() {
    for (ProjectRoleEnum projectRoleEnum : ProjectRoleEnum.values()) {
      assertThat(findRule(reconcile(projectRoleEnum, "node1"), "sriovnodestates")).isEmpty();
    }
  }

  @Test
  void repeatedReconcile_producesIdenticalRules() {
    for (ProjectRoleEnum projectRoleEnum : ProjectRoleEnum.values()) {
      assertThat(reconcile(projectRoleEnum, "node1", "node2"))
          .isEqualTo(reconcile(projectRoleEnum, "node1", "node2"));
    }
  }

}
