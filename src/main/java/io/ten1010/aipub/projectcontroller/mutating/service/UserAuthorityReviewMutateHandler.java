package io.ten1010.aipub.projectcontroller.mutating.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ten1010.common.jsonpatch.JsonPatchBuilder;
import io.ten1010.common.jsonpatch.JsonPatchOperationBuilder;
import io.ten1010.common.jsonpatch.dto.JsonPatchOperation;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1PolicyRule;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sGroupConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectMember;
import io.ten1010.aipub.projectcontroller.mutating.V1AdmissionReviewUtils;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import io.ten1010.aipub.projectcontroller.informer.IndexerConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Port of Python mutate/user_authority_review.py (UserAuthorityReviewMutateService).
 * Computes RBAC authority status for UserAuthorityReview CREATE requests.
 */
@Slf4j
public class UserAuthorityReviewMutateHandler implements ReviewHandler {

  private static final String OPERATION_CREATE = "CREATE";
  private static final String KIND = "UserAuthorityReview";

  private final UserInfoAnalyzer userInfoAnalyzer;
  private final ApiResourceDiscovery apiResourceDiscovery;
  private final Indexer<V1Namespace> namespaceIndexer;
  private final Indexer<V1ClusterRoleBinding> clusterRoleBindingIndexer;
  private final Indexer<V1ClusterRole> clusterRoleIndexer;
  private final Indexer<V1RoleBinding> roleBindingIndexer;
  private final Indexer<V1Role> roleIndexer;
  private final Indexer<V1alpha1Project> projectIndexer;
  private final ObjectMapper mapper;

  public UserAuthorityReviewMutateHandler(
      UserInfoAnalyzer userInfoAnalyzer,
      ApiResourceDiscovery apiResourceDiscovery,
      SharedInformerFactory sharedInformerFactory) {
    this.userInfoAnalyzer = userInfoAnalyzer;
    this.apiResourceDiscovery = apiResourceDiscovery;
    this.namespaceIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1Namespace.class).getIndexer();
    this.clusterRoleBindingIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1ClusterRoleBinding.class).getIndexer();
    this.clusterRoleIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1ClusterRole.class).getIndexer();
    this.roleBindingIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1RoleBinding.class).getIndexer();
    this.roleIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1Role.class).getIndexer();
    this.projectIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1Project.class).getIndexer();
    this.mapper = new ObjectMapperFactory().createObjectMapper();
  }

  @Override
  public boolean canHandle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());

    V1AdmissionReviewRequest request = review.getRequest();
    if (!OPERATION_CREATE.equals(request.getOperation())) {
      return false;
    }
    return request.getKind() != null && KIND.equals(request.getKind().getKind());
  }

  @Override
  public void handle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());

    V1AdmissionReviewRequest request = review.getRequest();
    Objects.requireNonNull(request.getUserInfo());
    Objects.requireNonNull(request.getObject());

    log.debug("UserAuthorityReview mutate: user={}", request.getUserInfo().getUsername());

    // Port of Python: aipub_user_name = self._aipub_user_service.get_aipub_user_name(request)
    UserInfoAnalysis analysis;
    try {
      analysis = this.userInfoAnalyzer.analyzeV2(request.getUserInfo());
    } catch (Exception e) {
      log.warn("Failed to analyze user info", e);
      V1AdmissionReviewUtils.reject(review, 500, e.getMessage());
      return;
    }

    if (!analysis.isAipubMember()) {
      V1AdmissionReviewUtils.reject(review, 400,
          "Not found aipub user name. request: " + analysis.getUsername());
      return;
    }

    if (analysis.getAipubUser().isEmpty()) {
      V1AdmissionReviewUtils.reject(review, 400,
          "Not found aipub user: " + analysis.getUsername());
      return;
    }

    String aipubUserName = analysis.getAipubUser().get().getMetadata().getName();

    // Parse spec.resources
    JsonNode specResources = request.getObject().path("spec").path("resources");
    if (!specResources.isArray()) {
      V1AdmissionReviewUtils.reject(review, 400, "spec.resources is required");
      return;
    }

    List<String> resources = new ArrayList<>();
    for (JsonNode r : specResources) {
      resources.add(r.textValue());
    }

    // Compute status
    StatusResult statusResult = computeStatus(request, aipubUserName, resources);
    if (statusResult.error != null) {
      V1AdmissionReviewUtils.reject(review, 400, statusResult.error);
      return;
    }

    // Build JSON Patch for status, ownerReferences
    JsonPatchBuilder patchBuilder = new JsonPatchBuilder();

    JsonPatchOperation statusOp = new JsonPatchOperationBuilder()
        .add()
        .setPath("/status")
        .setValue(buildStatusNode(statusResult))
        .build();
    patchBuilder.addToOperations(statusOp);

    JsonPatchOperation ownerRefsOp = new JsonPatchOperationBuilder()
        .add()
        .setPath("/metadata/ownerReferences")
        .setValue(buildDummyOwnerReferences())
        .build();
    patchBuilder.addToOperations(ownerRefsOp);

    V1AdmissionReviewUtils.allow(review, patchBuilder.build());
  }

  private StatusResult computeStatus(V1AdmissionReviewRequest request, String aipubUserName,
      List<String> requestResources) {
    Map<String, RBACAuthorityStatus> authorities = new LinkedHashMap<>();
    Set<String> targetGroups = new HashSet<>();
    Set<String> targetResourceNames = new HashSet<>();
    Map<String, Set<String>> namespacesDict = new HashMap<>();
    boolean isClusterAdmin = false;

    Set<String> allNamespaces = new HashSet<>();
    for (V1Namespace ns : this.namespaceIndexer.list()) {
      if (ns.getMetadata() != null && ns.getMetadata().getName() != null) {
        allNamespaces.add(ns.getMetadata().getName());
      }
    }

    // Parse and validate request resources
    for (String requestResource : new HashSet<>(requestResources)) {
      String[] parts = requestResource.split("/");
      if (parts.length < 2 || parts.length > 3) {
        return StatusResult.error(requestResource + " is invalid");
      }

      String group = parts[0];
      String resource = parts[1];
      String groupResource = group + "/" + resource;

      if (!this.apiResourceDiscovery.isExist(groupResource)) {
        return StatusResult.error("Not found group/resource: " + groupResource);
      }

      boolean isNamespaced;
      try {
        isNamespaced = this.apiResourceDiscovery.isNamespaced(groupResource);
      } catch (GroupResourceNotFoundException e) {
        return StatusResult.error("Not found group/resource: " + groupResource);
      }

      if (parts.length == 3) {
        if (!isNamespaced) {
          return StatusResult.error(requestResource + " is invalid");
        }
        String namespace = parts[2];
        if (!allNamespaces.contains(namespace)) {
          continue;
        }
        namespacesDict.computeIfAbsent(groupResource, k -> new HashSet<>()).add(namespace);
        authorities.put(requestResource, new RBACAuthorityStatus());
      } else {
        if (isNamespaced) {
          namespacesDict.computeIfAbsent(groupResource, k -> new HashSet<>()).addAll(allNamespaces);
          for (String namespace : allNamespaces) {
            authorities.put(groupResource + "/" + namespace, new RBACAuthorityStatus());
          }
        } else {
          authorities.put(requestResource, new RBACAuthorityStatus());
        }
      }

      targetGroups.add(group);
      targetResourceNames.add(resource);
    }

    String username = request.getUserInfo().getUsername();
    List<String> groups = request.getUserInfo().getGroups();

    // Process ClusterRoleBindings
    List<V1ClusterRoleBinding> crbList = getClusterRoleBindingsForUser(username, groups);
    for (V1ClusterRoleBinding crb : crbList) {
      if (crb.getRoleRef() == null) {
        continue;
      }
      String roleName = crb.getRoleRef().getName();
      V1ClusterRole role = this.clusterRoleIndexer.getByKey(roleName);
      if (role == null || role.getRules() == null) {
        continue;
      }
      for (V1PolicyRule rule : role.getRules()) {
        if (rule.getNonResourceURLs() != null && !rule.getNonResourceURLs().isEmpty()) {
          continue;
        }
        if (isClusterAdminRule(rule)) {
          isClusterAdmin = true;
        }
        Set<String> matchedGroupResources = getGroupResources(rule, targetGroups,
            targetResourceNames);
        List<String> matchedRequestResources = new ArrayList<>();
        for (String gr : matchedGroupResources) {
          if (namespacesDict.containsKey(gr)) {
            for (String ns : namespacesDict.get(gr)) {
              matchedRequestResources.add(gr + "/" + ns);
            }
          } else {
            matchedRequestResources.add(gr);
          }
        }
        addRuleToAuthorities(authorities, matchedRequestResources, rule);
      }
    }

    // Process RoleBindings
    List<V1RoleBinding> rbList = getRoleBindingsForUser(username, groups);
    for (V1RoleBinding rb : rbList) {
      if (rb.getRoleRef() == null || rb.getMetadata() == null) {
        continue;
      }
      String roleName = rb.getRoleRef().getName();
      String namespace = rb.getMetadata().getNamespace();
      List<V1PolicyRule> rules;
      if ("Role".equals(rb.getRoleRef().getKind())) {
        V1Role role = this.roleIndexer.getByKey(namespace + "/" + roleName);
        if (role == null || role.getRules() == null) {
          continue;
        }
        rules = role.getRules();
      } else {
        V1ClusterRole role = this.clusterRoleIndexer.getByKey(roleName);
        if (role == null || role.getRules() == null) {
          continue;
        }
        rules = role.getRules();
      }
      for (V1PolicyRule rule : rules) {
        if (rule.getNonResourceURLs() != null && !rule.getNonResourceURLs().isEmpty()) {
          continue;
        }
        Set<String> matchedGroupResources = getGroupResources(rule, targetGroups,
            targetResourceNames);
        List<String> matchedRequestResources = new ArrayList<>();
        for (String gr : matchedGroupResources) {
          boolean isNamespaced;
          try {
            isNamespaced = this.apiResourceDiscovery.isNamespaced(gr);
          } catch (GroupResourceNotFoundException e) {
            continue;
          }
          if (!isNamespaced) {
            continue;
          }
          matchedRequestResources.add(gr + "/" + namespace);
        }
        addRuleToAuthorities(authorities, matchedRequestResources, rule);
      }
    }

    // Convert asterisk get
    try {
      convertAsteriskGet(authorities);
    } catch (Exception e) {
      log.warn("Failed to convert asterisk get", e);
      return StatusResult.error(e.getMessage());
    }

    // Get aipub role
    boolean isAdmin = groups.contains(K8sGroupConstants.AIPUB_ADMIN_GROUP_NAME);
    List<Map<String, String>> projectRoles = getAipubProjectRoles(aipubUserName);

    return new StatusResult(isClusterAdmin, isAdmin, projectRoles, authorities, null);
  }

  private boolean isClusterAdminRule(V1PolicyRule rule) {
    if (rule.getApiGroups() == null || !rule.getApiGroups().contains("*")) {
      return false;
    }
    if (rule.getResources() == null || !rule.getResources().contains("*")) {
      return false;
    }
    return rule.getVerbs() != null && rule.getVerbs().contains("*");
  }

  private Set<String> getGroupResources(V1PolicyRule rule, Set<String> targetGroups,
      Set<String> targetResources) {
    Set<String> result = new HashSet<>();
    List<String> ruleApiGroups = rule.getApiGroups() != null ? rule.getApiGroups() : List.of();
    List<String> ruleResources = rule.getResources() != null ? rule.getResources() : List.of();

    Set<String> groups = ruleApiGroups.contains("*")
        ? targetGroups
        : intersection(targetGroups, new HashSet<>(ruleApiGroups));
    Set<String> resources = ruleResources.contains("*")
        ? targetResources
        : intersection(targetResources, new HashSet<>(ruleResources));

    for (String group : groups) {
      for (String resource : resources) {
        String groupResource = group + "/" + resource;
        if (this.apiResourceDiscovery.isExist(groupResource)) {
          result.add(groupResource);
        }
      }
    }
    return result;
  }

  private void addRuleToAuthorities(Map<String, RBACAuthorityStatus> authorities,
      List<String> requestResources, V1PolicyRule rule) {
    for (String requestResource : requestResources) {
      RBACAuthorityStatus authority = authorities.get(requestResource);
      if (authority == null) {
        continue;
      }
      List<String> resourceNames;
      if (rule.getResourceNames() != null && !rule.getResourceNames().isEmpty()) {
        resourceNames = rule.getResourceNames().contains("*")
            ? List.of("*")
            : rule.getResourceNames();
      } else {
        resourceNames = List.of("*");
      }

      List<String> verbs = rule.getVerbs() != null ? rule.getVerbs() : List.of();
      if (verbs.contains("*")) {
        authority.addAll(resourceNames);
      } else {
        for (String verb : verbs) {
          authority.add(verb, resourceNames);
        }
      }
    }
  }

  private void convertAsteriskGet(Map<String, RBACAuthorityStatus> authorities) {
    for (Map.Entry<String, RBACAuthorityStatus> entry : authorities.entrySet()) {
      RBACAuthorityStatus status = entry.getValue();
      if (!status.getGet().equals(List.of("*"))) {
        continue;
      }
      if (status.isList()) {
        continue;
      }
      String[] parts = entry.getKey().split("/");
      String groupResource = parts[0] + "/" + parts[1];
      String namespace = parts.length == 3 ? parts[2] : null;
      List<String> objectNames = this.apiResourceDiscovery.getAllObjectNames(
          groupResource, namespace);
      status.setGet(objectNames);
    }
  }

  private List<V1ClusterRoleBinding> getClusterRoleBindingsForUser(String username,
      List<String> groups) {
    Set<V1ClusterRoleBinding> result = new HashSet<>();
    result.addAll(this.clusterRoleBindingIndexer.byIndex(
        IndexerConstants.SUBJECT_USER_TO_BINDINGS_INDEXER_NAME, username));
    for (String group : groups) {
      result.addAll(this.clusterRoleBindingIndexer.byIndex(
          IndexerConstants.SUBJECT_GROUP_TO_BINDINGS_INDEXER_NAME, group));
    }
    return new ArrayList<>(result);
  }

  private List<V1RoleBinding> getRoleBindingsForUser(String username, List<String> groups) {
    Set<V1RoleBinding> result = new HashSet<>();
    result.addAll(this.roleBindingIndexer.byIndex(
        IndexerConstants.SUBJECT_USER_TO_BINDINGS_INDEXER_NAME, username));
    for (String group : groups) {
      result.addAll(this.roleBindingIndexer.byIndex(
          IndexerConstants.SUBJECT_GROUP_TO_BINDINGS_INDEXER_NAME, group));
    }
    return new ArrayList<>(result);
  }

  private List<Map<String, String>> getAipubProjectRoles(String aipubUserName) {
    List<Map<String, String>> projectRoles = new ArrayList<>();
    for (V1alpha1Project project : this.projectIndexer.list()) {
      if (project.getSpec() == null || project.getSpec().getMembers() == null) {
        continue;
      }
      if (project.getStatus() == null || project.getStatus().getAllBoundAipubUsers() == null) {
        continue;
      }
      if (!project.getStatus().getAllBoundAipubUsers().contains(aipubUserName)) {
        continue;
      }
      for (V1alpha1ProjectMember member : project.getSpec().getMembers()) {
        if (aipubUserName.equals(member.getAipubUser())) {
          Map<String, String> role = new LinkedHashMap<>();
          role.put("name", project.getMetadata().getName());
          role.put("role", member.getRole());
          projectRoles.add(role);
          break;
        }
      }
    }
    return projectRoles;
  }

  private ObjectNode buildStatusNode(StatusResult result) {
    ObjectNode statusNode = this.mapper.createObjectNode();
    statusNode.put("isClusterAdmin", result.isClusterAdmin);

    ObjectNode aipubRoleNode = this.mapper.createObjectNode();
    aipubRoleNode.put("isAdmin", result.isAdmin);
    ArrayNode projectsArray = this.mapper.createArrayNode();
    for (Map<String, String> pr : result.projectRoles) {
      ObjectNode prNode = this.mapper.createObjectNode();
      prNode.put("name", pr.get("name"));
      prNode.put("role", pr.get("role"));
      projectsArray.add(prNode);
    }
    aipubRoleNode.set("projects", projectsArray);
    statusNode.set("aipubRole", aipubRoleNode);

    ObjectNode authoritiesNode = this.mapper.createObjectNode();
    for (Map.Entry<String, RBACAuthorityStatus> entry : result.authorities.entrySet()) {
      authoritiesNode.set(entry.getKey(), buildAuthorityNode(entry.getValue()));
    }
    statusNode.set("authorities", authoritiesNode);

    return statusNode;
  }

  private ObjectNode buildAuthorityNode(RBACAuthorityStatus status) {
    ObjectNode node = this.mapper.createObjectNode();
    node.set("get", toJsonArray(status.getGet()));
    node.put("list", status.isList());
    node.set("watch", toJsonArray(status.getWatch()));
    node.set("patch", toJsonArray(status.getPatch()));
    node.set("update", toJsonArray(status.getUpdate()));
    node.put("create", status.isCreate());
    node.set("delete", toJsonArray(status.getDelete()));
    node.put("deletecollection", status.isDeletecollection());
    return node;
  }

  private ArrayNode toJsonArray(List<String> values) {
    ArrayNode array = this.mapper.createArrayNode();
    for (String v : values) {
      array.add(v);
    }
    return array;
  }

  private ArrayNode buildDummyOwnerReferences() {
    ObjectNode ownerRef = this.mapper.createObjectNode();
    ownerRef.put("apiVersion", "v1");
    ownerRef.put("controller", true);
    ownerRef.put("kind", "Node");
    ownerRef.put("name", "dummy");
    ownerRef.put("uid", "d-u-m-m-y");

    ArrayNode ownerRefs = this.mapper.createArrayNode();
    ownerRefs.add(ownerRef);
    return ownerRefs;
  }

  private static <T> Set<T> intersection(Set<T> a, Set<T> b) {
    Set<T> result = new HashSet<>(a);
    result.retainAll(b);
    return result;
  }

  record StatusResult(
      boolean isClusterAdmin,
      boolean isAdmin,
      List<Map<String, String>> projectRoles,
      Map<String, RBACAuthorityStatus> authorities,
      String error) {

    static StatusResult error(String message) {
      return new StatusResult(false, false, List.of(), Map.of(), message);
    }
  }

}
