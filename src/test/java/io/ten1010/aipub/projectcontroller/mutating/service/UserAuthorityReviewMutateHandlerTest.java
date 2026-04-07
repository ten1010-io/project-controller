package io.ten1010.aipub.projectcontroller.mutating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.ten1010.aipub.projectcontroller.informer.IndexerConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PolicyRule;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1RoleRef;
import io.kubernetes.client.openapi.models.RbacV1Subject;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectMember;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectSpec;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectStatus;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1Kind;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1UserInfo;
import io.kubernetes.client.common.KubernetesObject;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserAuthorityReviewMutateHandlerTest {

  private UserAuthorityReviewMutateHandler handler;
  private UserInfoAnalyzer mockAnalyzer;
  private ApiResourceDiscovery mockDiscovery;
  private Indexer<V1Namespace> mockNamespaceIndexer;
  private Indexer<V1ClusterRoleBinding> mockCrbIndexer;
  private Indexer<V1ClusterRole> mockCrIndexer;
  private Indexer<V1RoleBinding> mockRbIndexer;
  private Indexer<V1Role> mockRoleIndexer;
  private Indexer<V1alpha1Project> mockProjectIndexer;
  private ObjectMapper mapper;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    this.mockAnalyzer = mock(UserInfoAnalyzer.class);
    this.mockDiscovery = mock(ApiResourceDiscovery.class);
    this.mockNamespaceIndexer = mock(Indexer.class);
    this.mockCrbIndexer = mock(Indexer.class);
    this.mockCrIndexer = mock(Indexer.class);
    this.mockRbIndexer = mock(Indexer.class);
    this.mockRoleIndexer = mock(Indexer.class);
    this.mockProjectIndexer = mock(Indexer.class);

    SharedInformerFactory factory = mock(SharedInformerFactory.class);
    mockInformer(factory, V1Namespace.class, this.mockNamespaceIndexer);
    mockInformer(factory, V1ClusterRoleBinding.class, this.mockCrbIndexer);
    mockInformer(factory, V1ClusterRole.class, this.mockCrIndexer);
    mockInformer(factory, V1RoleBinding.class, this.mockRbIndexer);
    mockInformer(factory, V1Role.class, this.mockRoleIndexer);
    mockInformer(factory, V1alpha1Project.class, this.mockProjectIndexer);

    this.handler = new UserAuthorityReviewMutateHandler(
        this.mockAnalyzer, this.mockDiscovery, factory);
    this.mapper = new ObjectMapperFactory().createObjectMapper();

    // Default: empty informers
    when(this.mockNamespaceIndexer.list()).thenReturn(List.of());
    when(this.mockCrbIndexer.list()).thenReturn(List.of());
    when(this.mockCrbIndexer.byIndex(any(), any())).thenReturn(List.of());
    when(this.mockRbIndexer.list()).thenReturn(List.of());
    when(this.mockRbIndexer.byIndex(any(), any())).thenReturn(List.of());
    when(this.mockProjectIndexer.list()).thenReturn(List.of());
  }

  @SuppressWarnings("unchecked")
  private <T extends KubernetesObject> void mockInformer(SharedInformerFactory factory,
      Class<T> clazz, Indexer<T> indexer) {
    SharedIndexInformer<T> informer = mock(SharedIndexInformer.class);
    when(informer.getIndexer()).thenReturn(indexer);
    when(factory.getExistingSharedIndexInformer(clazz)).thenReturn(informer);
  }

  private V1AdmissionReview createReview(String operation, String kind,
      List<String> resources) {
    V1Kind v1Kind = new V1Kind();
    v1Kind.setGroup("aipub.ten1010.io");
    v1Kind.setVersion("v1alpha1");
    v1Kind.setKind(kind);

    V1UserInfo userInfo = new V1UserInfo();
    userInfo.setUsername("oidc:testuser");
    userInfo.setGroups(List.of("oidc:aipub-member", "system:authenticated"));

    ObjectNode objNode = this.mapper.createObjectNode();
    ObjectNode metadata = objNode.putObject("metadata");
    metadata.put("name", "test-uar");
    ObjectNode spec = objNode.putObject("spec");
    ArrayNode resourcesArray = spec.putArray("resources");
    for (String r : resources) {
      resourcesArray.add(r);
    }

    V1AdmissionReviewRequest request = new V1AdmissionReviewRequest();
    request.setUid("test-uid");
    request.setOperation(operation);
    request.setKind(v1Kind);
    request.setUserInfo(userInfo);
    request.setObject(objNode);

    V1AdmissionReview review = new V1AdmissionReview();
    review.setApiVersion("admission.k8s.io/v1");
    review.setKind("AdmissionReview");
    review.setRequest(request);

    return review;
  }

  private UserInfoAnalysis memberAnalysis(String aipubUserName) {
    V1alpha1AipubUser user = new V1alpha1AipubUser();
    user.setApiVersion("project.aipub.ten1010.io/v1alpha1");
    user.setKind("AipubUser");
    V1ObjectMeta meta = new V1ObjectMeta();
    meta.setName(aipubUserName);
    meta.setUid("uid-" + aipubUserName);
    user.setMetadata(meta);
    return new UserInfoAnalysis("oidc:" + aipubUserName,
        List.of("oidc:aipub-member", "system:authenticated"), user);
  }

  private V1Namespace namespace(String name) {
    V1Namespace ns = new V1Namespace();
    ns.setMetadata(new V1ObjectMeta());
    ns.getMetadata().setName(name);
    return ns;
  }

  private V1ClusterRoleBinding crb(String roleName, String subjectKind, String subjectName) {
    V1ClusterRoleBinding binding = new V1ClusterRoleBinding();
    binding.setMetadata(new V1ObjectMeta());
    binding.getMetadata().setName("crb-" + roleName);
    V1RoleRef ref = new V1RoleRef();
    ref.setKind("ClusterRole");
    ref.setName(roleName);
    binding.setRoleRef(ref);
    RbacV1Subject subject = new RbacV1Subject();
    subject.setKind(subjectKind);
    subject.setName(subjectName);
    binding.setSubjects(List.of(subject));
    return binding;
  }

  private V1RoleBinding rb(String namespace, String roleKind, String roleName,
      String subjectKind, String subjectName) {
    V1RoleBinding binding = new V1RoleBinding();
    binding.setMetadata(new V1ObjectMeta());
    binding.getMetadata().setName("rb-" + roleName);
    binding.getMetadata().setNamespace(namespace);
    V1RoleRef ref = new V1RoleRef();
    ref.setKind(roleKind);
    ref.setName(roleName);
    binding.setRoleRef(ref);
    RbacV1Subject subject = new RbacV1Subject();
    subject.setKind(subjectKind);
    subject.setName(subjectName);
    binding.setSubjects(List.of(subject));
    return binding;
  }

  private V1ClusterRole clusterRole(String name, List<V1PolicyRule> rules) {
    V1ClusterRole role = new V1ClusterRole();
    role.setMetadata(new V1ObjectMeta());
    role.getMetadata().setName(name);
    role.setRules(rules);
    return role;
  }

  private V1Role role(String name, String namespace, List<V1PolicyRule> rules) {
    V1Role role = new V1Role();
    role.setMetadata(new V1ObjectMeta());
    role.getMetadata().setName(name);
    role.getMetadata().setNamespace(namespace);
    role.setRules(rules);
    return role;
  }

  private V1PolicyRule rule(List<String> apiGroups, List<String> resources, List<String> verbs) {
    V1PolicyRule rule = new V1PolicyRule();
    rule.setApiGroups(apiGroups);
    rule.setResources(resources);
    rule.setVerbs(verbs);
    return rule;
  }

  private JsonNode decodePatch(V1AdmissionReview review) throws Exception {
    String patchBase64 = review.getResponse().getPatch();
    byte[] decoded = Base64.getDecoder().decode(patchBase64);
    return this.mapper.readTree(decoded);
  }

  // === canHandle ===

  @Nested
  class CanHandle {

    @Test
    void create_userAuthorityReview_returnsTrue() {
      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview", List.of());
      assertThat(handler.canHandle(review)).isTrue();
    }

    @Test
    void update_returnsFalse() {
      V1AdmissionReview review = createReview("UPDATE", "UserAuthorityReview", List.of());
      assertThat(handler.canHandle(review)).isFalse();
    }

    @Test
    void delete_returnsFalse() {
      V1AdmissionReview review = createReview("DELETE", "UserAuthorityReview", List.of());
      assertThat(handler.canHandle(review)).isFalse();
    }

    @Test
    void differentKind_returnsFalse() {
      V1AdmissionReview review = createReview("CREATE", "Deployment", List.of());
      assertThat(handler.canHandle(review)).isFalse();
    }
  }

  // === handle — reject cases (Python parity) ===

  @Nested
  class HandleReject {

    // Python: aipub_user_name is None → 400
    @Test
    void nonMember_rejects400() {
      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("apps/deployments"));
      UserInfoAnalysis analysis = new UserInfoAnalysis(
          "anonymous", List.of("system:authenticated"), null);
      when(mockAnalyzer.analyzeV2(any())).thenReturn(analysis);

      handler.handle(review);

      assertThat(review.getResponse().getAllowed()).isFalse();
      assertThat(review.getResponse().getStatus().getCode()).isEqualTo(400);
      assertThat(review.getResponse().getStatus().getMessage()).contains("Not found aipub user name");
    }

    // Python: user_informer.get(name=aipub_user_name) is None → 400
    @Test
    void memberButNoAipubUser_rejects400() {
      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("apps/deployments"));
      UserInfoAnalysis analysis = new UserInfoAnalysis(
          "oidc:testuser", List.of("oidc:aipub-member", "system:authenticated"), null);
      when(mockAnalyzer.analyzeV2(any())).thenReturn(analysis);

      handler.handle(review);

      assertThat(review.getResponse().getAllowed()).isFalse();
      assertThat(review.getResponse().getStatus().getCode()).isEqualTo(400);
      assertThat(review.getResponse().getStatus().getMessage()).contains("Not found aipub user");
    }

    // Python: analyzer throws → 500
    @Test
    void analyzerThrows_rejects500() {
      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("apps/deployments"));
      when(mockAnalyzer.analyzeV2(any())).thenThrow(new RuntimeException("analyzer error"));

      handler.handle(review);

      assertThat(review.getResponse().getAllowed()).isFalse();
      assertThat(review.getResponse().getStatus().getCode()).isEqualTo(500);
    }

    // Python: group_resource not exist → 400
    @Test
    void nonExistentResource_rejects400() {
      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("nonexistent/resources"));
      when(mockAnalyzer.analyzeV2(any())).thenReturn(memberAnalysis("testuser"));
      when(mockDiscovery.isExist("nonexistent/resources")).thenReturn(false);

      handler.handle(review);

      assertThat(review.getResponse().getAllowed()).isFalse();
      assertThat(review.getResponse().getStatus().getCode()).isEqualTo(400);
      assertThat(review.getResponse().getStatus().getMessage()).contains("Not found group/resource");
    }

    // Python: invalid format (1 part) → 400
    @Test
    void invalidResourceFormat_rejects400() {
      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("invalidformat"));
      when(mockAnalyzer.analyzeV2(any())).thenReturn(memberAnalysis("testuser"));

      handler.handle(review);

      assertThat(review.getResponse().getAllowed()).isFalse();
      assertThat(review.getResponse().getStatus().getCode()).isEqualTo(400);
      assertThat(review.getResponse().getStatus().getMessage()).contains("is invalid");
    }

    // Python: group/resource/namespace where resource is not namespaced → 400
    @Test
    void namespacedFormatOnClusterScoped_rejects400() {
      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("rbac.authorization.k8s.io/clusterroles/default"));
      when(mockAnalyzer.analyzeV2(any())).thenReturn(memberAnalysis("testuser"));
      when(mockDiscovery.isExist("rbac.authorization.k8s.io/clusterroles")).thenReturn(true);
      when(mockDiscovery.isNamespaced("rbac.authorization.k8s.io/clusterroles")).thenReturn(false);

      handler.handle(review);

      assertThat(review.getResponse().getAllowed()).isFalse();
      assertThat(review.getResponse().getStatus().getCode()).isEqualTo(400);
    }

    // Python: spec.resources missing → 400
    @Test
    void missingSpecResources_rejects400() {
      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview", List.of());
      when(mockAnalyzer.analyzeV2(any())).thenReturn(memberAnalysis("testuser"));
      // Remove spec.resources from object
      ObjectNode obj = (ObjectNode) review.getRequest().getObject();
      obj.remove("spec");

      handler.handle(review);

      assertThat(review.getResponse().getAllowed()).isFalse();
      assertThat(review.getResponse().getStatus().getCode()).isEqualTo(400);
    }
  }

  // === handle — success cases ===

  @Nested
  class HandleSuccess {

    // Python: cluster-scoped resource, no bindings → empty authorities, allowed
    @Test
    void clusterScopedResource_noBindings_allowsWithEmptyAuthorities() throws Exception {
      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("/nodes"));
      when(mockAnalyzer.analyzeV2(any())).thenReturn(memberAnalysis("testuser"));
      when(mockDiscovery.isExist("/nodes")).thenReturn(true);
      when(mockDiscovery.isNamespaced("/nodes")).thenReturn(false);

      handler.handle(review);

      assertThat(review.getResponse().getAllowed()).isTrue();
      assertThat(review.getResponse().getPatch()).isNotNull();

      JsonNode patch = decodePatch(review);
      // /status and /metadata/ownerReferences
      assertThat(patch.size()).isEqualTo(2);

      // status.authorities should have "/nodes" key with empty permissions
      JsonNode statusPatch = patch.get(0);
      assertThat(statusPatch.get("path").asText()).isEqualTo("/status");
      JsonNode authorities = statusPatch.get("value").get("authorities");
      assertThat(authorities.has("/nodes")).isTrue();

      // ownerReferences should be dummy
      JsonNode ownerRefPatch = patch.get(1);
      assertThat(ownerRefPatch.get("path").asText()).isEqualTo("/metadata/ownerReferences");
      assertThat(ownerRefPatch.get("value").get(0).get("name").asText()).isEqualTo("dummy");
    }

    // Python: namespaced resource "apps/deployments" expands to all namespaces
    @Test
    void namespacedResource_expandsToAllNamespaces() throws Exception {
      when(mockNamespaceIndexer.list()).thenReturn(
          List.of(namespace("ns1"), namespace("ns2")));

      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("apps/deployments"));
      when(mockAnalyzer.analyzeV2(any())).thenReturn(memberAnalysis("testuser"));
      when(mockDiscovery.isExist("apps/deployments")).thenReturn(true);
      when(mockDiscovery.isNamespaced("apps/deployments")).thenReturn(true);

      handler.handle(review);

      assertThat(review.getResponse().getAllowed()).isTrue();
      JsonNode patch = decodePatch(review);
      JsonNode authorities = patch.get(0).get("value").get("authorities");
      assertThat(authorities.has("apps/deployments/ns1")).isTrue();
      assertThat(authorities.has("apps/deployments/ns2")).isTrue();
    }

    // Python: "apps/deployments/ns1" specific namespace
    @Test
    void specificNamespace_onlyThatNamespace() throws Exception {
      when(mockNamespaceIndexer.list()).thenReturn(
          List.of(namespace("ns1"), namespace("ns2")));

      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("apps/deployments/ns1"));
      when(mockAnalyzer.analyzeV2(any())).thenReturn(memberAnalysis("testuser"));
      when(mockDiscovery.isExist("apps/deployments")).thenReturn(true);
      when(mockDiscovery.isNamespaced("apps/deployments")).thenReturn(true);

      handler.handle(review);

      assertThat(review.getResponse().getAllowed()).isTrue();
      JsonNode patch = decodePatch(review);
      JsonNode authorities = patch.get(0).get("value").get("authorities");
      assertThat(authorities.has("apps/deployments/ns1")).isTrue();
      assertThat(authorities.has("apps/deployments/ns2")).isFalse();
    }

    // Python: namespace not found → silently skipped (no error, no entry)
    @Test
    void nonExistentNamespace_silentlySkipped() throws Exception {
      when(mockNamespaceIndexer.list()).thenReturn(List.of(namespace("ns1")));

      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("apps/deployments/nonexistent"));
      when(mockAnalyzer.analyzeV2(any())).thenReturn(memberAnalysis("testuser"));
      when(mockDiscovery.isExist("apps/deployments")).thenReturn(true);
      when(mockDiscovery.isNamespaced("apps/deployments")).thenReturn(true);

      handler.handle(review);

      assertThat(review.getResponse().getAllowed()).isTrue();
      JsonNode patch = decodePatch(review);
      JsonNode authorities = patch.get(0).get("value").get("authorities");
      assertThat(authorities.has("apps/deployments/nonexistent")).isFalse();
    }
  }

  // === RBAC rule application ===

  @Nested
  class RBACRules {

    // Python: ClusterRoleBinding → ClusterRole with verbs → applied to all namespaces
    @Test
    void clusterRoleBinding_appliesVerbsToAllNamespaces() throws Exception {
      when(mockNamespaceIndexer.list()).thenReturn(List.of(namespace("ns1")));

      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("apps/deployments"));
      when(mockAnalyzer.analyzeV2(any())).thenReturn(memberAnalysis("testuser"));
      when(mockDiscovery.isExist("apps/deployments")).thenReturn(true);
      when(mockDiscovery.isNamespaced("apps/deployments")).thenReturn(true);

      V1ClusterRole cr = clusterRole("viewer", List.of(
          rule(List.of("apps"), List.of("deployments"), List.of("get", "list"))));
      when(mockCrIndexer.getByKey("viewer")).thenReturn(cr);
      when(mockCrbIndexer.byIndex(
          eq(IndexerConstants.SUBJECT_USER_TO_BINDINGS_INDEXER_NAME), eq("oidc:testuser")))
          .thenReturn(List.of(crb("viewer", "User", "oidc:testuser")));

      handler.handle(review);

      assertThat(review.getResponse().getAllowed()).isTrue();
      JsonNode patch = decodePatch(review);
      JsonNode auth = patch.get(0).get("value").get("authorities").get("apps/deployments/ns1");
      assertThat(auth.get("get")).isNotNull();
      assertThat(auth.get("list").asBoolean()).isTrue();
    }

    // Python: RoleBinding → Role → applied only to that namespace
    @Test
    void roleBinding_appliesOnlyToBindingNamespace() throws Exception {
      when(mockNamespaceIndexer.list()).thenReturn(
          List.of(namespace("ns1"), namespace("ns2")));

      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("apps/deployments"));
      when(mockAnalyzer.analyzeV2(any())).thenReturn(memberAnalysis("testuser"));
      when(mockDiscovery.isExist("apps/deployments")).thenReturn(true);
      when(mockDiscovery.isNamespaced("apps/deployments")).thenReturn(true);

      V1Role r = role("editor", "ns1", List.of(
          rule(List.of("apps"), List.of("deployments"), List.of("get", "list", "create"))));
      when(mockRoleIndexer.getByKey("ns1/editor")).thenReturn(r);
      when(mockRbIndexer.byIndex(
          eq(IndexerConstants.SUBJECT_USER_TO_BINDINGS_INDEXER_NAME), eq("oidc:testuser")))
          .thenReturn(List.of(rb("ns1", "Role", "editor", "User", "oidc:testuser")));

      handler.handle(review);

      assertThat(review.getResponse().getAllowed()).isTrue();
      JsonNode patch = decodePatch(review);
      JsonNode authorities = patch.get(0).get("value").get("authorities");

      // ns1 should have permissions
      JsonNode ns1Auth = authorities.get("apps/deployments/ns1");
      assertThat(ns1Auth.get("create").asBoolean()).isTrue();

      // ns2 should have no permissions
      JsonNode ns2Auth = authorities.get("apps/deployments/ns2");
      assertThat(ns2Auth.get("create").asBoolean()).isFalse();
    }

    // Python: _is_cluster_admin → apiGroups/resources/verbs all "*"
    @Test
    void clusterAdminRule_setsIsClusterAdminTrue() throws Exception {
      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("/nodes"));
      when(mockAnalyzer.analyzeV2(any())).thenReturn(memberAnalysis("testuser"));
      when(mockDiscovery.isExist("/nodes")).thenReturn(true);
      when(mockDiscovery.isNamespaced("/nodes")).thenReturn(false);

      V1ClusterRole cr = clusterRole("cluster-admin", List.of(
          rule(List.of("*"), List.of("*"), List.of("*"))));
      when(mockCrIndexer.getByKey("cluster-admin")).thenReturn(cr);
      when(mockCrbIndexer.byIndex(
          eq(IndexerConstants.SUBJECT_USER_TO_BINDINGS_INDEXER_NAME), eq("oidc:testuser")))
          .thenReturn(List.of(crb("cluster-admin", "User", "oidc:testuser")));

      handler.handle(review);

      assertThat(review.getResponse().getAllowed()).isTrue();
      JsonNode patch = decodePatch(review);
      JsonNode status = patch.get(0).get("value");
      assertThat(status.get("isClusterAdmin").asBoolean()).isTrue();
    }

    // Python: wildcard verbs ["*"] → addAll
    @Test
    void wildcardVerbs_addsAllPermissions() throws Exception {
      when(mockNamespaceIndexer.list()).thenReturn(List.of(namespace("ns1")));

      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("apps/deployments/ns1"));
      when(mockAnalyzer.analyzeV2(any())).thenReturn(memberAnalysis("testuser"));
      when(mockDiscovery.isExist("apps/deployments")).thenReturn(true);
      when(mockDiscovery.isNamespaced("apps/deployments")).thenReturn(true);

      V1ClusterRole cr = clusterRole("admin", List.of(
          rule(List.of("apps"), List.of("deployments"), List.of("*"))));
      when(mockCrIndexer.getByKey("admin")).thenReturn(cr);
      when(mockCrbIndexer.byIndex(
          eq(IndexerConstants.SUBJECT_USER_TO_BINDINGS_INDEXER_NAME), eq("oidc:testuser")))
          .thenReturn(List.of(crb("admin", "User", "oidc:testuser")));

      handler.handle(review);

      JsonNode patch = decodePatch(review);
      JsonNode auth = patch.get(0).get("value").get("authorities").get("apps/deployments/ns1");
      assertThat(auth.get("list").booleanValue()).isTrue();
      assertThat(auth.get("create").booleanValue()).isTrue();
      assertThat(auth.get("deletecollection").booleanValue()).isTrue();
      assertThat(auth.get("get").get(0).textValue()).isEqualTo("*");
    }

    // Python: Group subject matching
    @Test
    void groupSubject_matchesBinding() throws Exception {
      when(mockNamespaceIndexer.list()).thenReturn(List.of(namespace("ns1")));

      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("apps/deployments/ns1"));
      when(mockAnalyzer.analyzeV2(any())).thenReturn(memberAnalysis("testuser"));
      when(mockDiscovery.isExist("apps/deployments")).thenReturn(true);
      when(mockDiscovery.isNamespaced("apps/deployments")).thenReturn(true);

      V1ClusterRole cr = clusterRole("viewer", List.of(
          rule(List.of("apps"), List.of("deployments"), List.of("get", "list"))));
      when(mockCrIndexer.getByKey("viewer")).thenReturn(cr);
      // Binding by group, not user
      when(mockCrbIndexer.byIndex(
          eq(IndexerConstants.SUBJECT_GROUP_TO_BINDINGS_INDEXER_NAME), eq("oidc:aipub-member")))
          .thenReturn(List.of(crb("viewer", "Group", "oidc:aipub-member")));

      handler.handle(review);

      assertThat(review.getResponse().getAllowed()).isTrue();
      JsonNode patch = decodePatch(review);
      JsonNode auth = patch.get(0).get("value").get("authorities").get("apps/deployments/ns1");
      assertThat(auth.get("list").asBoolean()).isTrue();
    }

    // Python: nonResourceURLs rule → skipped
    @Test
    void nonResourceUrlRule_isSkipped() throws Exception {
      when(mockNamespaceIndexer.list()).thenReturn(List.of(namespace("ns1")));

      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("apps/deployments/ns1"));
      when(mockAnalyzer.analyzeV2(any())).thenReturn(memberAnalysis("testuser"));
      when(mockDiscovery.isExist("apps/deployments")).thenReturn(true);
      when(mockDiscovery.isNamespaced("apps/deployments")).thenReturn(true);

      V1PolicyRule nonResourceRule = new V1PolicyRule();
      nonResourceRule.setNonResourceURLs(List.of("/healthz"));
      nonResourceRule.setVerbs(List.of("get"));
      V1ClusterRole cr = clusterRole("health-checker", List.of(nonResourceRule));
      when(mockCrIndexer.getByKey("health-checker")).thenReturn(cr);
      when(mockCrbIndexer.byIndex(
          eq(IndexerConstants.SUBJECT_USER_TO_BINDINGS_INDEXER_NAME), eq("oidc:testuser")))
          .thenReturn(List.of(crb("health-checker", "User", "oidc:testuser")));

      handler.handle(review);

      assertThat(review.getResponse().getAllowed()).isTrue();
      JsonNode patch = decodePatch(review);
      JsonNode auth = patch.get(0).get("value").get("authorities").get("apps/deployments/ns1");
      // No permissions added
      assertThat(auth.get("list").asBoolean()).isFalse();
      assertThat(auth.get("create").asBoolean()).isFalse();
    }

    // Python: RoleBinding referencing ClusterRole
    @Test
    void roleBinding_referencingClusterRole() throws Exception {
      when(mockNamespaceIndexer.list()).thenReturn(List.of(namespace("ns1")));

      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("apps/deployments/ns1"));
      when(mockAnalyzer.analyzeV2(any())).thenReturn(memberAnalysis("testuser"));
      when(mockDiscovery.isExist("apps/deployments")).thenReturn(true);
      when(mockDiscovery.isNamespaced("apps/deployments")).thenReturn(true);

      V1ClusterRole cr = clusterRole("admin", List.of(
          rule(List.of("apps"), List.of("deployments"), List.of("*"))));
      when(mockCrIndexer.getByKey("admin")).thenReturn(cr);
      // RoleBinding in ns1 referencing ClusterRole
      when(mockRbIndexer.byIndex(
          eq(IndexerConstants.SUBJECT_USER_TO_BINDINGS_INDEXER_NAME), eq("oidc:testuser")))
          .thenReturn(List.of(rb("ns1", "ClusterRole", "admin", "User", "oidc:testuser")));

      handler.handle(review);

      JsonNode patch = decodePatch(review);
      JsonNode auth = patch.get(0).get("value").get("authorities").get("apps/deployments/ns1");
      assertThat(auth.get("create").asBoolean()).isTrue();
    }
  }

  // === AIPub Role ===

  @Nested
  class AipubRole {

    @Test
    void adminGroup_setsIsAdminTrue() throws Exception {
      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("/nodes"));
      when(mockDiscovery.isExist("/nodes")).thenReturn(true);
      when(mockDiscovery.isNamespaced("/nodes")).thenReturn(false);

      // Add admin group
      V1UserInfo userInfo = review.getRequest().getUserInfo();
      userInfo.setGroups(List.of("oidc:aipub-member", "oidc:aipub-admin", "system:authenticated"));
      when(mockAnalyzer.analyzeV2(any())).thenReturn(memberAnalysis("testuser"));

      handler.handle(review);

      JsonNode patch = decodePatch(review);
      JsonNode aipubRole = patch.get(0).get("value").get("aipubRole");
      assertThat(aipubRole.get("isAdmin").asBoolean()).isTrue();
    }

    @Test
    void projectMember_includedInProjects() throws Exception {
      V1AdmissionReview review = createReview("CREATE", "UserAuthorityReview",
          List.of("/nodes"));
      when(mockAnalyzer.analyzeV2(any())).thenReturn(memberAnalysis("testuser"));
      when(mockDiscovery.isExist("/nodes")).thenReturn(true);
      when(mockDiscovery.isNamespaced("/nodes")).thenReturn(false);

      V1alpha1Project project = new V1alpha1Project();
      project.setMetadata(new V1ObjectMeta());
      project.getMetadata().setName("my-project");
      V1alpha1ProjectSpec spec = new V1alpha1ProjectSpec();
      V1alpha1ProjectMember member = new V1alpha1ProjectMember();
      member.setAipubUser("testuser");
      member.setRole("admin");
      spec.setMembers(List.of(member));
      project.setSpec(spec);
      V1alpha1ProjectStatus status = new V1alpha1ProjectStatus();
      status.setAllBoundAipubUsers(List.of("testuser"));
      project.setStatus(status);
      when(mockProjectIndexer.list()).thenReturn(List.of(project));

      handler.handle(review);

      JsonNode patch = decodePatch(review);
      JsonNode projects = patch.get(0).get("value").get("aipubRole").get("projects");
      assertThat(projects.size()).isEqualTo(1);
      assertThat(projects.get(0).get("name").asText()).isEqualTo("my-project");
      assertThat(projects.get(0).get("role").asText()).isEqualTo("admin");
    }
  }
}
