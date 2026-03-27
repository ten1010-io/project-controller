package io.ten1010.aipub.projectcontroller.mutating.userauthorityreview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1Kind;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1UserInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

class UserAuthorityReviewMutateServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TEST_USERNAME = "oidc:prefix:testuser";
    private static final List<String> TEST_GROUPS = List.of("oidc:aipub-member", "oidc:aipub-admin");
    private static final String TEST_AIPUB_USER = "testuser";

    ApiResourceLookup apiResourceLookup;
    RBACResourceLister rbacResourceLister;
    AIPubLookup aipubLookup;
    UserAuthorityReviewMutateService service;

    @BeforeEach
    void setUp() {
        this.apiResourceLookup = Mockito.mock(ApiResourceLookup.class);
        this.rbacResourceLister = Mockito.mock(RBACResourceLister.class);
        this.aipubLookup = Mockito.mock(AIPubLookup.class);
        this.service = new UserAuthorityReviewMutateService(
                this.apiResourceLookup, this.rbacResourceLister, this.aipubLookup);

        Mockito.doReturn(Collections.emptyList())
                .when(this.rbacResourceLister).getClusterRoleBindingsByUser(Mockito.anyString());
        Mockito.doReturn(Collections.emptyList())
                .when(this.rbacResourceLister).getClusterRoleBindingsByGroup(Mockito.anyString());
        Mockito.doReturn(Collections.emptyList())
                .when(this.rbacResourceLister).getRoleBindingsByUser(Mockito.anyString());
        Mockito.doReturn(Collections.emptyList())
                .when(this.rbacResourceLister).getRoleBindingsByGroup(Mockito.anyString());
        Mockito.doReturn(Set.of("default", "kube-system"))
                .when(this.rbacResourceLister).getAllNamespaceNames();
        Mockito.doReturn(new AIPubRole(false, List.of()))
                .when(this.aipubLookup).getAIPubRole(Mockito.anyList(), Mockito.anyString());
    }

    @Test
    void should_skip_non_create_operation() {
        V1AdmissionReviewRequest request = buildRequest("UPDATE", "UserAuthorityReview");
        setupAIPubUser();

        MutateResult result = this.service.mutate(request);

        Assertions.assertTrue(result.isAllowed());
        Assertions.assertFalse(result.hasStatus());
    }

    @Test
    void should_skip_non_matching_kind() {
        V1AdmissionReviewRequest request = buildRequest("CREATE", "Pod");
        setupAIPubUser();

        MutateResult result = this.service.mutate(request);

        Assertions.assertTrue(result.isAllowed());
        Assertions.assertFalse(result.hasStatus());
    }

    @Test
    void should_reject_when_user_not_resolved() {
        V1AdmissionReviewRequest request = buildRequestWithResources("CREATE", "UserAuthorityReview", List.of());
        Mockito.doReturn(null)
                .when(this.aipubLookup).resolveUserName(Mockito.anyString(), Mockito.anyList());

        MutateResult result = this.service.mutate(request);

        Assertions.assertFalse(result.isAllowed());
        Assertions.assertEquals(400, result.getStatusCode());
    }

    @Test
    void should_reject_when_user_does_not_exist() {
        V1AdmissionReviewRequest request = buildRequestWithResources("CREATE", "UserAuthorityReview", List.of());
        Mockito.doReturn(TEST_AIPUB_USER)
                .when(this.aipubLookup).resolveUserName(Mockito.anyString(), Mockito.anyList());
        Mockito.doReturn(false)
                .when(this.aipubLookup).userExists(TEST_AIPUB_USER);

        MutateResult result = this.service.mutate(request);

        Assertions.assertFalse(result.isAllowed());
        Assertions.assertEquals(400, result.getStatusCode());
    }

    @Test
    void should_reject_when_resource_does_not_exist() {
        Mockito.doReturn(false)
                .when(this.apiResourceLookup).exists("apps/nonexistent");

        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("apps/nonexistent"));

        Assertions.assertFalse(result.isAllowed());
        Assertions.assertEquals(400, result.getStatusCode());
        Assertions.assertTrue(result.getMessage().contains("Not found group/resource"));
    }

    @Test
    void should_reject_when_non_namespaced_resource_has_namespace() {
        Mockito.doReturn(true).when(this.apiResourceLookup).exists("/nodes");
        Mockito.doReturn(false).when(this.apiResourceLookup).isNamespaced("/nodes");

        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("/nodes/default"));

        Assertions.assertFalse(result.isAllowed());
        Assertions.assertEquals(400, result.getStatusCode());
    }

    @Test
    void should_skip_namespace_that_does_not_exist() {
        Mockito.doReturn(true).when(this.apiResourceLookup).exists("apps/deployments");
        Mockito.doReturn(true).when(this.apiResourceLookup).isNamespaced("apps/deployments");

        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("apps/deployments/nonexistent-ns"));

        Assertions.assertTrue(result.isAllowed());
        Assertions.assertTrue(result.hasStatus());
        Assertions.assertTrue(result.getStatus().getAuthorities().isEmpty());
    }

    @Test
    void should_expand_namespaced_resource_to_all_namespaces() {
        Mockito.doReturn(true).when(this.apiResourceLookup).exists("apps/deployments");
        Mockito.doReturn(true).when(this.apiResourceLookup).isNamespaced("apps/deployments");

        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("apps/deployments"));

        Assertions.assertTrue(result.isAllowed());
        Map<String, RBACAuthorityStatus> authorities = result.getStatus().getAuthorities();
        Assertions.assertTrue(authorities.containsKey("apps/deployments/default"));
        Assertions.assertTrue(authorities.containsKey("apps/deployments/kube-system"));
    }

    @Test
    void should_handle_non_namespaced_resource() {
        Mockito.doReturn(true).when(this.apiResourceLookup).exists("/nodes");
        Mockito.doReturn(false).when(this.apiResourceLookup).isNamespaced("/nodes");

        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("/nodes"));

        Assertions.assertTrue(result.isAllowed());
        Assertions.assertTrue(result.getStatus().getAuthorities().containsKey("/nodes"));
    }

    @Test
    void should_process_cluster_role_binding_with_get_and_list_verbs() {
        setupResourceExists("apps/deployments", true);

        V1ClusterRoleBinding crb = buildClusterRoleBinding("test-crb", "test-role");
        V1ClusterRole clusterRole = buildClusterRole("test-role",
                List.of(buildRule(List.of("apps"), List.of("deployments"),
                        List.of("get", "list"), null)));

        Mockito.doReturn(List.of(crb))
                .when(this.rbacResourceLister).getClusterRoleBindingsByUser(TEST_USERNAME);
        Mockito.doReturn(clusterRole)
                .when(this.rbacResourceLister).getClusterRole("test-role");

        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("apps/deployments/default"));

        Assertions.assertTrue(result.isAllowed());
        RBACAuthorityStatus authority = result.getStatus()
                .getAuthorities().get("apps/deployments/default");
        Assertions.assertNotNull(authority);
        Assertions.assertEquals(List.of("*"), authority.getGet());
        Assertions.assertTrue(authority.isList());
    }

    @Test
    void should_detect_cluster_admin() {
        setupResourceExists("/pods", true);

        V1ClusterRoleBinding crb = buildClusterRoleBinding("admin-crb", "cluster-admin");
        V1ClusterRole clusterRole = buildClusterRole("cluster-admin",
                List.of(buildRule(List.of("*"), List.of("*"), List.of("*"), null)));

        Mockito.doReturn(List.of(crb))
                .when(this.rbacResourceLister).getClusterRoleBindingsByUser(TEST_USERNAME);
        Mockito.doReturn(clusterRole)
                .when(this.rbacResourceLister).getClusterRole("cluster-admin");

        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("/pods/default"));

        Assertions.assertTrue(result.isAllowed());
        Assertions.assertTrue(result.getStatus().isClusterAdmin());
        RBACAuthorityStatus authority = result.getStatus()
                .getAuthorities().get("/pods/default");
        Assertions.assertEquals(List.of("*"), authority.getGet());
        Assertions.assertTrue(authority.isList());
        Assertions.assertTrue(authority.isCreate());
    }

    @Test
    void should_process_role_binding_with_role() {
        setupResourceExists("apps/deployments", true);

        V1RoleBinding rb = buildRoleBinding("test-rb", "default", "Role", "test-role");
        V1Role role = buildRole("test-role", "default",
                List.of(buildRule(List.of("apps"), List.of("deployments"),
                        List.of("get", "list"), null)));

        Mockito.doReturn(List.of(rb))
                .when(this.rbacResourceLister).getRoleBindingsByUser(TEST_USERNAME);
        Mockito.doReturn(role)
                .when(this.rbacResourceLister).getRole("test-role", "default");

        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("apps/deployments/default"));

        Assertions.assertTrue(result.isAllowed());
        RBACAuthorityStatus authority = result.getStatus()
                .getAuthorities().get("apps/deployments/default");
        Assertions.assertNotNull(authority);
        Assertions.assertEquals(List.of("*"), authority.getGet());
        Assertions.assertTrue(authority.isList());
    }

    @Test
    void should_process_role_binding_referencing_cluster_role() {
        setupResourceExists("apps/deployments", true);

        V1RoleBinding rb = buildRoleBinding("test-rb", "default",
                "ClusterRole", "shared-reader");
        V1ClusterRole clusterRole = buildClusterRole("shared-reader",
                List.of(buildRule(List.of("apps"), List.of("deployments"),
                        List.of("get", "list", "watch"), null)));

        Mockito.doReturn(List.of(rb))
                .when(this.rbacResourceLister).getRoleBindingsByUser(TEST_USERNAME);
        Mockito.doReturn(clusterRole)
                .when(this.rbacResourceLister).getClusterRole("shared-reader");

        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("apps/deployments/default"));

        Assertions.assertTrue(result.isAllowed());
        RBACAuthorityStatus authority = result.getStatus()
                .getAuthorities().get("apps/deployments/default");
        Assertions.assertTrue(authority.isList());
        Assertions.assertEquals(List.of("*"), authority.getWatch());
    }

    @Test
    void should_not_apply_role_binding_to_non_namespaced_resources() {
        setupResourceExists("/nodes", false);

        V1RoleBinding rb = buildRoleBinding("test-rb", "default", "ClusterRole", "node-reader");
        V1ClusterRole clusterRole = buildClusterRole("node-reader",
                List.of(buildRule(List.of(""), List.of("nodes"), List.of("get", "list"), null)));

        Mockito.doReturn(List.of(rb))
                .when(this.rbacResourceLister).getRoleBindingsByUser(TEST_USERNAME);
        Mockito.doReturn(clusterRole)
                .when(this.rbacResourceLister).getClusterRole("node-reader");

        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("/nodes"));

        Assertions.assertTrue(result.isAllowed());
        RBACAuthorityStatus authority = result.getStatus().getAuthorities().get("/nodes");
        Assertions.assertEquals(List.of(), authority.getGet());
        Assertions.assertFalse(authority.isList());
    }

    @Test
    void should_accumulate_permissions_from_group_bindings() {
        setupResourceExists("apps/deployments", true);

        V1ClusterRoleBinding crbByGroup = buildClusterRoleBinding("group-crb", "group-role");
        V1ClusterRole groupRole = buildClusterRole("group-role",
                List.of(buildRule(List.of("apps"), List.of("deployments"),
                        List.of("get", "list"), null)));

        Mockito.doReturn(List.of(crbByGroup))
                .when(this.rbacResourceLister).getClusterRoleBindingsByGroup("oidc:aipub-member");
        Mockito.doReturn(groupRole)
                .when(this.rbacResourceLister).getClusterRole("group-role");

        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("apps/deployments/default"));

        Assertions.assertTrue(result.isAllowed());
        RBACAuthorityStatus authority = result.getStatus()
                .getAuthorities().get("apps/deployments/default");
        Assertions.assertEquals(List.of("*"), authority.getGet());
        Assertions.assertTrue(authority.isList());
    }

    @Test
    void should_handle_specific_resource_names_in_rule() {
        setupResourceExists("apps/deployments", true);

        V1ClusterRoleBinding crb = buildClusterRoleBinding("test-crb", "test-role");
        V1ClusterRole clusterRole = buildClusterRole("test-role",
                List.of(buildRule(List.of("apps"), List.of("deployments"),
                        List.of("get", "update"),
                        List.of("my-deployment"))));

        Mockito.doReturn(List.of(crb))
                .when(this.rbacResourceLister).getClusterRoleBindingsByUser(TEST_USERNAME);
        Mockito.doReturn(clusterRole)
                .when(this.rbacResourceLister).getClusterRole("test-role");

        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("apps/deployments/default"));

        Assertions.assertTrue(result.isAllowed());
        RBACAuthorityStatus authority = result.getStatus()
                .getAuthorities().get("apps/deployments/default");
        Assertions.assertEquals(List.of("my-deployment"), authority.getGet());
        Assertions.assertEquals(List.of("my-deployment"), authority.getUpdate());
    }

    @Test
    void should_convert_wildcard_get_when_list_not_allowed() {
        setupResourceExists("apps/deployments", true);

        V1ClusterRoleBinding crb = buildClusterRoleBinding("test-crb", "test-role");
        V1ClusterRole clusterRole = buildClusterRole("test-role",
                List.of(buildRule(List.of("apps"), List.of("deployments"),
                        List.of("get"), null)));

        Mockito.doReturn(List.of(crb))
                .when(this.rbacResourceLister).getClusterRoleBindingsByUser(TEST_USERNAME);
        Mockito.doReturn(clusterRole)
                .when(this.rbacResourceLister).getClusterRole("test-role");
        Mockito.doReturn(List.of("deploy-a", "deploy-b"))
                .when(this.apiResourceLookup).getAllObjectNames("apps/deployments", "default");

        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("apps/deployments/default"));

        Assertions.assertTrue(result.isAllowed());
        RBACAuthorityStatus authority = result.getStatus()
                .getAuthorities().get("apps/deployments/default");
        Assertions.assertTrue(authority.getGet().containsAll(List.of("deploy-a", "deploy-b")));
        Assertions.assertEquals(2, authority.getGet().size());
        Assertions.assertFalse(authority.isList());
    }

    @Test
    void should_not_convert_wildcard_get_when_list_is_allowed() {
        setupResourceExists("apps/deployments", true);

        V1ClusterRoleBinding crb = buildClusterRoleBinding("test-crb", "test-role");
        V1ClusterRole clusterRole = buildClusterRole("test-role",
                List.of(buildRule(List.of("apps"), List.of("deployments"),
                        List.of("get", "list"), null)));

        Mockito.doReturn(List.of(crb))
                .when(this.rbacResourceLister).getClusterRoleBindingsByUser(TEST_USERNAME);
        Mockito.doReturn(clusterRole)
                .when(this.rbacResourceLister).getClusterRole("test-role");

        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("apps/deployments/default"));

        Assertions.assertTrue(result.isAllowed());
        RBACAuthorityStatus authority = result.getStatus()
                .getAuthorities().get("apps/deployments/default");
        Assertions.assertEquals(List.of("*"), authority.getGet());
        Assertions.assertTrue(authority.isList());
    }

    @Test
    void should_skip_rules_with_non_resource_urls() {
        setupResourceExists("apps/deployments", true);

        V1PolicyRule nonResourceRule = new V1PolicyRule();
        nonResourceRule.setNonResourceURLs(List.of("/healthz"));
        nonResourceRule.setVerbs(List.of("get"));

        V1ClusterRoleBinding crb = buildClusterRoleBinding("test-crb", "test-role");
        V1ClusterRole clusterRole = buildClusterRole("test-role",
                List.of(nonResourceRule));

        Mockito.doReturn(List.of(crb))
                .when(this.rbacResourceLister).getClusterRoleBindingsByUser(TEST_USERNAME);
        Mockito.doReturn(clusterRole)
                .when(this.rbacResourceLister).getClusterRole("test-role");

        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("apps/deployments/default"));

        Assertions.assertTrue(result.isAllowed());
        RBACAuthorityStatus authority = result.getStatus()
                .getAuthorities().get("apps/deployments/default");
        Assertions.assertEquals(List.of(), authority.getGet());
    }

    @Test
    void should_skip_missing_cluster_role() {
        setupResourceExists("apps/deployments", true);

        V1ClusterRoleBinding crb = buildClusterRoleBinding("test-crb", "missing-role");

        Mockito.doReturn(List.of(crb))
                .when(this.rbacResourceLister).getClusterRoleBindingsByUser(TEST_USERNAME);
        Mockito.doReturn(null)
                .when(this.rbacResourceLister).getClusterRole("missing-role");

        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("apps/deployments/default"));

        Assertions.assertTrue(result.isAllowed());
        RBACAuthorityStatus authority = result.getStatus()
                .getAuthorities().get("apps/deployments/default");
        Assertions.assertEquals(List.of(), authority.getGet());
    }

    @Test
    void should_handle_wildcard_api_groups_in_rule() {
        setupResourceExists("apps/deployments", true);

        V1ClusterRoleBinding crb = buildClusterRoleBinding("test-crb", "test-role");
        V1ClusterRole clusterRole = buildClusterRole("test-role",
                List.of(buildRule(List.of("*"), List.of("deployments"),
                        List.of("get", "list"), null)));

        Mockito.doReturn(List.of(crb))
                .when(this.rbacResourceLister).getClusterRoleBindingsByUser(TEST_USERNAME);
        Mockito.doReturn(clusterRole)
                .when(this.rbacResourceLister).getClusterRole("test-role");

        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("apps/deployments/default"));

        RBACAuthorityStatus authority = result.getStatus()
                .getAuthorities().get("apps/deployments/default");
        Assertions.assertEquals(List.of("*"), authority.getGet());
        Assertions.assertTrue(authority.isList());
    }

    @Test
    void should_handle_wildcard_resources_in_rule() {
        setupResourceExists("apps/deployments", true);

        V1ClusterRoleBinding crb = buildClusterRoleBinding("test-crb", "test-role");
        V1ClusterRole clusterRole = buildClusterRole("test-role",
                List.of(buildRule(List.of("apps"), List.of("*"),
                        List.of("get", "list"), null)));

        Mockito.doReturn(List.of(crb))
                .when(this.rbacResourceLister).getClusterRoleBindingsByUser(TEST_USERNAME);
        Mockito.doReturn(clusterRole)
                .when(this.rbacResourceLister).getClusterRole("test-role");

        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("apps/deployments/default"));

        RBACAuthorityStatus authority = result.getStatus()
                .getAuthorities().get("apps/deployments/default");
        Assertions.assertEquals(List.of("*"), authority.getGet());
        Assertions.assertTrue(authority.isList());
    }

    @Test
    void should_reject_invalid_resource_format() {
        MutateResult result = this.service.computeStatus(
                TEST_USERNAME, TEST_GROUPS, TEST_AIPUB_USER,
                List.of("invalid"));

        Assertions.assertFalse(result.isAllowed());
        Assertions.assertEquals(400, result.getStatusCode());
    }

    // --- Helper methods ---

    private void setupAIPubUser() {
        Mockito.doReturn(TEST_AIPUB_USER)
                .when(this.aipubLookup).resolveUserName(Mockito.anyString(), Mockito.anyList());
        Mockito.doReturn(true)
                .when(this.aipubLookup).userExists(TEST_AIPUB_USER);
    }

    private void setupResourceExists(String groupResource, boolean namespaced) {
        Mockito.doReturn(true).when(this.apiResourceLookup).exists(groupResource);
        Mockito.doReturn(namespaced).when(this.apiResourceLookup).isNamespaced(groupResource);
    }

    private V1AdmissionReviewRequest buildRequest(String operation, String kindName) {
        return buildRequestWithResources(operation, kindName, List.of());
    }

    private V1AdmissionReviewRequest buildRequestWithResources(String operation,
                                                                String kindName,
                                                                List<String> resources) {
        V1AdmissionReviewRequest request = new V1AdmissionReviewRequest();
        request.setUid("test-uid");
        request.setOperation(operation);

        V1Kind kind = new V1Kind();
        kind.setKind(kindName);
        request.setKind(kind);

        V1UserInfo userInfo = new V1UserInfo();
        userInfo.setUsername(TEST_USERNAME);
        userInfo.setGroups(new ArrayList<>(TEST_GROUPS));
        request.setUserInfo(userInfo);

        ObjectNode obj = MAPPER.createObjectNode();
        ObjectNode spec = obj.putObject("spec");
        ArrayNode resourcesNode = spec.putArray("resources");
        for (String r : resources) {
            resourcesNode.add(r);
        }
        request.setObject(obj);

        return request;
    }

    private V1ClusterRoleBinding buildClusterRoleBinding(String name, String roleName) {
        V1ClusterRoleBinding crb = new V1ClusterRoleBinding();
        V1ObjectMeta meta = new V1ObjectMeta();
        meta.setName(name);
        crb.setMetadata(meta);
        V1RoleRef roleRef = new V1RoleRef();
        roleRef.setKind("ClusterRole");
        roleRef.setName(roleName);
        roleRef.setApiGroup("rbac.authorization.k8s.io");
        crb.setRoleRef(roleRef);
        return crb;
    }

    private V1ClusterRole buildClusterRole(String name, List<V1PolicyRule> rules) {
        V1ClusterRole role = new V1ClusterRole();
        V1ObjectMeta meta = new V1ObjectMeta();
        meta.setName(name);
        role.setMetadata(meta);
        role.setRules(rules);
        return role;
    }

    private V1RoleBinding buildRoleBinding(String name, String namespace,
                                           String roleKind, String roleName) {
        V1RoleBinding rb = new V1RoleBinding();
        V1ObjectMeta meta = new V1ObjectMeta();
        meta.setName(name);
        meta.setNamespace(namespace);
        rb.setMetadata(meta);
        V1RoleRef roleRef = new V1RoleRef();
        roleRef.setKind(roleKind);
        roleRef.setName(roleName);
        roleRef.setApiGroup("rbac.authorization.k8s.io");
        rb.setRoleRef(roleRef);
        return rb;
    }

    private V1Role buildRole(String name, String namespace, List<V1PolicyRule> rules) {
        V1Role role = new V1Role();
        V1ObjectMeta meta = new V1ObjectMeta();
        meta.setName(name);
        meta.setNamespace(namespace);
        role.setMetadata(meta);
        role.setRules(rules);
        return role;
    }

    private V1PolicyRule buildRule(List<String> apiGroups,
                                  List<String> resources,
                                  List<String> verbs,
                                  List<String> resourceNames) {
        V1PolicyRule rule = new V1PolicyRule();
        rule.setApiGroups(apiGroups);
        rule.setResources(resources);
        rule.setVerbs(verbs);
        rule.setResourceNames(resourceNames);
        return rule;
    }

}
