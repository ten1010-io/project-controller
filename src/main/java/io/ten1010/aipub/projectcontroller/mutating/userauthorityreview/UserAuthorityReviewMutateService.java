package io.ten1010.aipub.projectcontroller.mutating.userauthorityreview;

import com.fasterxml.jackson.databind.JsonNode;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReviewRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class UserAuthorityReviewMutateService {

    static final String REVIEW_KIND = "UserAuthorityReview";

    private final ApiResourceLookup apiResourceLookup;
    private final RBACResourceLister rbacResourceLister;
    private final AIPubLookup aipubLookup;

    public UserAuthorityReviewMutateService(ApiResourceLookup apiResourceLookup,
                                            RBACResourceLister rbacResourceLister,
                                            AIPubLookup aipubLookup) {
        this.apiResourceLookup = Objects.requireNonNull(apiResourceLookup);
        this.rbacResourceLister = Objects.requireNonNull(rbacResourceLister);
        this.aipubLookup = Objects.requireNonNull(aipubLookup);
    }

    public MutateResult mutate(V1AdmissionReviewRequest request) {
        if (!"CREATE".equals(request.getOperation())) {
            return MutateResult.skip();
        }
        Objects.requireNonNull(request.getKind());
        if (!REVIEW_KIND.equals(request.getKind().getKind())) {
            return MutateResult.skip();
        }

        Objects.requireNonNull(request.getUserInfo());
        String username = request.getUserInfo().getUsername();
        List<String> groups = request.getUserInfo().getGroups();

        String aipubUserName = this.aipubLookup.resolveUserName(username, groups);
        if (aipubUserName == null) {
            return MutateResult.rejected(400,
                    "Not found aipub user name. request: " + username);
        }
        if (!this.aipubLookup.userExists(aipubUserName)) {
            return MutateResult.rejected(400,
                    "Not found aipub user: " + aipubUserName);
        }

        List<String> specResources = parseSpecResources(request.getObject());
        return computeStatus(username, groups, aipubUserName, specResources);
    }

    MutateResult computeStatus(String username,
                               List<String> groups,
                               String aipubUserName,
                               List<String> specResources) {
        Map<String, RBACAuthorityStatus> authorities = new LinkedHashMap<>();
        Set<String> targetGroups = new HashSet<>();
        Set<String> targetResources = new HashSet<>();
        Map<String, Set<String>> namespacesDict = new HashMap<>();
        boolean isClusterAdmin = false;
        Set<String> allNamespaces = this.rbacResourceLister.getAllNamespaceNames();

        // Step 1: Parse requested resources
        for (String requestResource : new LinkedHashSet<>(specResources)) {
            String[] parts = requestResource.split("/", -1);
            if (parts.length < 2 || parts.length > 3) {
                return MutateResult.rejected(400, requestResource + " is invalid");
            }
            String group = parts[0];
            String resource = parts[1];
            String groupResource = group + "/" + resource;

            if (!this.apiResourceLookup.exists(groupResource)) {
                return MutateResult.rejected(400,
                        "Not found group/resource: " + groupResource);
            }

            boolean isNamespaced = this.apiResourceLookup.isNamespaced(groupResource);

            if (parts.length == 3) {
                if (!isNamespaced) {
                    return MutateResult.rejected(400, requestResource + " is invalid");
                }
                String namespace = parts[2];
                if (!allNamespaces.contains(namespace)) {
                    continue;
                }
                namespacesDict.computeIfAbsent(groupResource, k -> new HashSet<>())
                        .add(namespace);
                authorities.put(requestResource, new RBACAuthorityStatus());
            } else {
                if (isNamespaced) {
                    namespacesDict.computeIfAbsent(groupResource, k -> new HashSet<>())
                            .addAll(allNamespaces);
                    for (String ns : allNamespaces) {
                        authorities.put(groupResource + "/" + ns, new RBACAuthorityStatus());
                    }
                } else {
                    authorities.put(requestResource, new RBACAuthorityStatus());
                }
            }
            targetGroups.add(group);
            targetResources.add(resource);
        }

        // Step 2: ClusterRoleBinding analysis
        List<V1ClusterRoleBinding> crbList = new ArrayList<>(
                this.rbacResourceLister.getClusterRoleBindingsByUser(username));
        for (String group : groups) {
            crbList.addAll(this.rbacResourceLister.getClusterRoleBindingsByGroup(group));
        }

        for (V1ClusterRoleBinding binding : crbList) {
            String roleName = binding.getRoleRef().getName();
            V1ClusterRole clusterRole = this.rbacResourceLister.getClusterRole(roleName);
            if (clusterRole == null) {
                continue;
            }
            if (clusterRole.getRules() == null) {
                continue;
            }
            for (V1PolicyRule rule : clusterRole.getRules()) {
                if (rule.getNonResourceURLs() != null && !rule.getNonResourceURLs().isEmpty()) {
                    continue;
                }
                if (isClusterAdminRule(rule)) {
                    isClusterAdmin = true;
                }
                Set<String> matchingGroupResources = getMatchingGroupResources(
                        rule, targetGroups, targetResources);
                List<String> matchingRequestResources = expandToRequestResources(
                        matchingGroupResources, namespacesDict);
                addRuleToAuthorities(authorities, matchingRequestResources, rule);
            }
        }

        // Step 3: RoleBinding analysis
        List<V1RoleBinding> rbList = new ArrayList<>(
                this.rbacResourceLister.getRoleBindingsByUser(username));
        for (String group : groups) {
            rbList.addAll(this.rbacResourceLister.getRoleBindingsByGroup(group));
        }

        for (V1RoleBinding binding : rbList) {
            String bindingNamespace = binding.getMetadata().getNamespace();
            List<V1PolicyRule> rules = resolveRoleBindingRules(binding);
            if (rules == null) {
                continue;
            }

            for (V1PolicyRule rule : rules) {
                if (rule.getNonResourceURLs() != null && !rule.getNonResourceURLs().isEmpty()) {
                    continue;
                }
                Set<String> matchingGroupResources = getMatchingGroupResources(
                        rule, targetGroups, targetResources);
                List<String> matchingRequestResources = new ArrayList<>();
                for (String gr : matchingGroupResources) {
                    if (!this.apiResourceLookup.isNamespaced(gr)) {
                        continue;
                    }
                    matchingRequestResources.add(gr + "/" + bindingNamespace);
                }
                addRuleToAuthorities(authorities, matchingRequestResources, rule);
            }
        }

        // Step 4: Convert wildcard GET where list is not allowed
        convertAsteriskGet(authorities);

        // Step 5: AIPub role
        AIPubRole aipubRole = this.aipubLookup.getAIPubRole(groups, aipubUserName);

        // Step 6: Assemble result
        UserAuthorityReviewStatus status = new UserAuthorityReviewStatus(
                isClusterAdmin, aipubRole, authorities);
        return MutateResult.allowed(status);
    }

    private List<String> parseSpecResources(JsonNode object) {
        JsonNode specNode = object.path("spec").path("resources");
        if (specNode.isMissingNode() || !specNode.isArray()) {
            return Collections.emptyList();
        }
        List<String> resources = new ArrayList<>();
        for (JsonNode node : specNode) {
            resources.add(node.asText());
        }
        return resources;
    }

    private boolean isClusterAdminRule(V1PolicyRule rule) {
        List<String> apiGroups = rule.getApiGroups();
        List<String> resources = rule.getResources();
        List<String> verbs = rule.getVerbs();
        if (apiGroups == null || !apiGroups.contains("*")) {
            return false;
        }
        if (resources == null || !resources.contains("*")) {
            return false;
        }
        return verbs != null && verbs.contains("*");
    }

    private Set<String> getMatchingGroupResources(V1PolicyRule rule,
                                                   Set<String> targetGroups,
                                                   Set<String> targetResources) {
        Set<String> result = new HashSet<>();
        List<String> ruleApiGroups = rule.getApiGroups() != null
                ? rule.getApiGroups() : Collections.emptyList();
        List<String> ruleResources = rule.getResources() != null
                ? rule.getResources() : Collections.emptyList();

        Set<String> matchingGroups;
        if (ruleApiGroups.contains("*")) {
            matchingGroups = targetGroups;
        } else {
            matchingGroups = new HashSet<>(targetGroups);
            matchingGroups.retainAll(ruleApiGroups);
        }

        Set<String> matchingResources;
        if (ruleResources.contains("*")) {
            matchingResources = targetResources;
        } else {
            matchingResources = new HashSet<>(targetResources);
            matchingResources.retainAll(ruleResources);
        }

        for (String group : matchingGroups) {
            for (String resource : matchingResources) {
                String groupResource = group + "/" + resource;
                if (this.apiResourceLookup.exists(groupResource)) {
                    result.add(groupResource);
                }
            }
        }
        return result;
    }

    private List<String> expandToRequestResources(Set<String> groupResources,
                                                   Map<String, Set<String>> namespacesDict) {
        List<String> requestResources = new ArrayList<>();
        for (String gr : groupResources) {
            if (namespacesDict.containsKey(gr)) {
                for (String ns : namespacesDict.get(gr)) {
                    requestResources.add(gr + "/" + ns);
                }
            } else {
                requestResources.add(gr);
            }
        }
        return requestResources;
    }

    private void addRuleToAuthorities(Map<String, RBACAuthorityStatus> authorities,
                                      List<String> requestResources,
                                      V1PolicyRule rule) {
        for (String requestResource : requestResources) {
            RBACAuthorityStatus authority = authorities.get(requestResource);
            if (authority == null) {
                continue;
            }

            List<String> resourceNames;
            List<String> ruleResourceNames = rule.getResourceNames();
            if (ruleResourceNames != null && !ruleResourceNames.isEmpty()) {
                if (ruleResourceNames.contains("*")) {
                    resourceNames = List.of("*");
                } else {
                    resourceNames = ruleResourceNames;
                }
            } else {
                resourceNames = List.of("*");
            }

            List<String> ruleVerbs = rule.getVerbs() != null
                    ? rule.getVerbs() : Collections.emptyList();
            if (ruleVerbs.contains("*")) {
                authority.addAll(resourceNames);
            } else {
                for (String verb : ruleVerbs) {
                    authority.add(verb, resourceNames);
                }
            }
        }
    }

    private List<V1PolicyRule> resolveRoleBindingRules(V1RoleBinding binding) {
        String roleName = binding.getRoleRef().getName();
        String bindingNamespace = binding.getMetadata().getNamespace();
        if ("Role".equals(binding.getRoleRef().getKind())) {
            V1Role role = this.rbacResourceLister.getRole(roleName, bindingNamespace);
            if (role == null) {
                return null;
            }
            return role.getRules();
        } else {
            V1ClusterRole clusterRole = this.rbacResourceLister.getClusterRole(roleName);
            if (clusterRole == null) {
                return null;
            }
            return clusterRole.getRules();
        }
    }

    private void convertAsteriskGet(Map<String, RBACAuthorityStatus> authorities) {
        for (Map.Entry<String, RBACAuthorityStatus> entry : authorities.entrySet()) {
            RBACAuthorityStatus authority = entry.getValue();
            if (!authority.getGet().equals(List.of("*"))) {
                continue;
            }
            if (authority.isList()) {
                continue;
            }
            String requestResource = entry.getKey();
            String[] parts = requestResource.split("/", -1);
            String groupResource = parts[0] + "/" + parts[1];
            String namespace = parts.length == 3 ? parts[2] : null;

            List<String> allObjectNames = this.apiResourceLookup.getAllObjectNames(
                    groupResource, namespace);
            authority.setGet(allObjectNames);
        }
    }

}
