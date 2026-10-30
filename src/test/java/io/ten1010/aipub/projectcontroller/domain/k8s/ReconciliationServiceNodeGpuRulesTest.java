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

/** NodeGPU 는 Cluster scope 라 list 를 주면 resourceNames 가 무시돼 전 노드의 GPU 상태가 노출된다. */
class ReconciliationServiceNodeGpuRulesTest {

  private ReconciliationService reconciliationService;
  private V1alpha1Project project;

  private static Optional<V1PolicyRule> findNodeGpuRule(List<V1PolicyRule> rules) {
    return rules.stream()
        .filter(rule -> rule.getResources() != null
            && rule.getResources().contains(ProjectApiConstants.NODE_GPU_RESOURCE_PLURAL))
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
        .map(ReconciliationServiceNodeGpuRulesTest::node)
        .toList();
    return this.reconciliationService.reconcileClusterRoleRules(
        this.project, projectRoleEnum, List.of(), bindingNodes, List.of(), List.of(), List.of());
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
  void bothRoles_grantGetOnBindingNodeNodeGpus() {
    for (ProjectRoleEnum projectRoleEnum : ProjectRoleEnum.values()) {
      V1PolicyRule rule = findNodeGpuRule(reconcile(projectRoleEnum, "node1", "node2"))
          .orElseThrow();

      assertThat(rule.getApiGroups()).containsExactly(ProjectApiConstants.COASTER_GROUP);
      assertThat(rule.getResourceNames()).containsExactly("node1", "node2");
      assertThat(rule.getVerbs()).containsExactly("get");
    }
  }

  @Test
  void noBindingNode_omitsNodeGpuRule() {
    for (ProjectRoleEnum projectRoleEnum : ProjectRoleEnum.values()) {
      // 빈 resourceNames 는 RBAC 에서 전체 허용이라 규칙 자체를 넣지 않는다.
      assertThat(findNodeGpuRule(reconcile(projectRoleEnum))).isEmpty();
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
