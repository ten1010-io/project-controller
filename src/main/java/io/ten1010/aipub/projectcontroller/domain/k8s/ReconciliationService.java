package io.ten1010.aipub.projectcontroller.domain.k8s;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.*;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.*;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class ReconciliationService {

    private static final String REQUESTS_STORAGE_QUOTA_RESOURCE_NAME = "requests.storage";

    private static List<V1OwnerReference> removeOwnerReferencesThatReferToProjectKind(List<V1OwnerReference> references) {
        return references.stream()
                .filter(e -> !(ProjectApiConstants.PROJECT_API_VERSION.equals(e.getApiVersion()) &&
                        ProjectApiConstants.PROJECT_RESOURCE_KIND.equals(e.getKind())))
                .toList();
    }

    private static List<V1OwnerReference> removeOwnerReferencesThatReferToAipubUserKind(List<V1OwnerReference> references) {
        return references.stream()
                .filter(e -> !(ProjectApiConstants.PROJECT_API_VERSION.equals(e.getApiVersion()) &&
                        ProjectApiConstants.AIPUB_USER_RESOURCE_KIND.equals(e.getKind())))
                .toList();
    }

    private static Map<String, String> removeAnnotationWithBoundProjectsKey(Map<String, String> annotations) {
        Map<String, String> filtered = new HashMap<>(annotations);
        filtered.remove(AnnotationConstants.BOUND_PROJECTS_KEY);

        return filtered;
    }

    private static Map<String, String> removeAnnotationWithBoundNodeGroupsKey(Map<String, String> annotations) {
        Map<String, String> filtered = new HashMap<>(annotations);
        filtered.remove(AnnotationConstants.BOUND_NODE_GROUPS_KEY);

        return filtered;
    }

    private static List<V1Taint> removeProjectManagedTaints(List<V1Taint> taints) {
        return taints.stream()
                .filter(e -> !TaintConstants.PROJECT_MANAGED_KEY.equals(e.getKey()))
                .toList();
    }

    private static boolean isAllKeyAllEffectToleration(V1Toleration toleration) {
        return (toleration.getKey() == null && toleration.getEffect() == null);
    }

    private static List<V1Toleration> replaceAllKeyAllEffectTolerations(List<V1Toleration> tolerations) {
        return tolerations.stream()
                .map(e -> {
                    if (isAllKeyAllEffectToleration(e)) {
                        return List.of(new V1TolerationBuilder()
                                        .withEffect(TaintConstants.NO_EXECUTE_EFFECT)
                                        .withKey(null)
                                        .withOperator(e.getOperator())
                                        .withTolerationSeconds(e.getTolerationSeconds())
                                        .withValue(e.getValue())
                                        .build(),
                                new V1TolerationBuilder()
                                        .withEffect(TaintConstants.NO_SCHEDULE_EFFECT)
                                        .withKey("node-role.kubernetes.io/control-plane")
                                        .withOperator("Exists")
                                        .withTolerationSeconds(null)
                                        .withValue(null)
                                        .build());
                    }
                    return List.of(e);
                })
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private static boolean isAllKeyNoScheduleEffectToleration(V1Toleration toleration) {
        return (toleration.getKey() == null && toleration.getEffect() != null && toleration.getEffect().equals(TaintConstants.NO_SCHEDULE_EFFECT));
    }

    private static List<V1Toleration> replaceAllKeyNoScheduleEffectTolerations(List<V1Toleration> tolerations) {
        return tolerations.stream()
                .map(e -> {
                    if (isAllKeyNoScheduleEffectToleration(e)) {
                        return List.of(new V1TolerationBuilder()
                                        .withEffect(TaintConstants.NO_SCHEDULE_EFFECT)
                                        .withKey("node-role.kubernetes.io/control-plane")
                                        .withOperator("Exists")
                                        .withTolerationSeconds(null)
                                        .withValue(null)
                                        .build(),
                                new V1TolerationBuilder()
                                        .withEffect(TaintConstants.NO_SCHEDULE_EFFECT)
                                        .withKey("node.kubernetes.io/not-ready")
                                        .withOperator("Exists")
                                        .withTolerationSeconds(null)
                                        .withValue(null)
                                        .build());
                    }
                    return List.of(e);
                })
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private static List<V1Toleration> removeProjectManagedTolerations(List<V1Toleration> tolerations) {
        return tolerations.stream()
                .filter(e -> !TaintConstants.PROJECT_MANAGED_KEY.equals(e.getKey()))
                .toList();
    }

    private static List<V1NodeSelectorTerm> removeProjectManagedNodeSelectorTerms(List<V1NodeSelectorTerm> nodeSelectorTerms) {
        return nodeSelectorTerms.stream()
                .filter(term -> WorkloadUtils.getMatchExpressions(term).stream()
                        .filter(expr -> expr.getKey().equals(LabelConstants.PROJECT_MANAGED_KEY))
                        .findAny()
                        .isEmpty())
                .toList();
    }

    private static List<V1Taint> buildProjectManagedTaints(String nodeName, boolean strictMode) {
        V1Taint noSchedule = new V1TaintBuilder()
                .withKey(TaintConstants.PROJECT_MANAGED_KEY)
                .withValue(nodeName)
                .withEffect(TaintConstants.NO_SCHEDULE_EFFECT)
                .build();
        V1Taint noExecute = new V1TaintBuilder()
                .withKey(TaintConstants.PROJECT_MANAGED_KEY)
                .withValue(nodeName)
                .withEffect(TaintConstants.NO_EXECUTE_EFFECT)
                .build();
        if (strictMode) {
            return List.of(noSchedule, noExecute);
        }
        return List.of(noSchedule);
    }

    private static List<V1Toleration> reconcileTolerations(List<V1Toleration> existing, List<V1Node> allowedProjectNodes) {
        List<V1Toleration> reconciled = replaceAllKeyAllEffectTolerations(existing);
        reconciled = replaceAllKeyNoScheduleEffectTolerations(reconciled);
        reconciled = removeProjectManagedTolerations(reconciled);
        reconciled = new ArrayList<>(reconciled);
        List<V1Toleration> projectManagedTolerations = allowedProjectNodes.stream()
                .map(K8sObjectUtils::getName)
                .flatMap(e -> buildProjectManagedTolerations(e).stream())
                .toList();
        reconciled.addAll(projectManagedTolerations);

        return reconciled;
    }

    private static List<V1Toleration> buildProjectManagedTolerations(String nodeName) {
        V1Toleration noSchedule = new V1TolerationBuilder()
                .withKey(TaintConstants.PROJECT_MANAGED_KEY)
                .withValue(nodeName)
                .withEffect(TaintConstants.NO_SCHEDULE_EFFECT)
                .withOperator("Equal")
                .build();
        V1Toleration noExecute = new V1TolerationBuilder()
                .withKey(TaintConstants.PROJECT_MANAGED_KEY)
                .withValue(nodeName)
                .withEffect(TaintConstants.NO_EXECUTE_EFFECT)
                .withOperator("Equal")
                .build();
        return List.of(noSchedule, noExecute);
    }

    private static V1NodeSelectorTerm buildProjectManagedNodeSelectorTerm() {
        return new V1NodeSelectorTermBuilder()
                .withMatchExpressions(buildProjectManagedNodeSelectorRequirement())
                .build();
    }

    private static V1NodeSelectorRequirement buildProjectManagedNodeSelectorRequirement() {
        return new V1NodeSelectorRequirementBuilder()
                .withKey(LabelConstants.PROJECT_MANAGED_KEY)
                .withOperator("In")
                .withValues(ProjectManagedValueEnum.TRUE.getStr())
                .build();
    }

    @Nullable
    private static V1alpha1ProjectStatusQuota buildProjectStatusQuota(@Nullable V1ResourceQuota boundQuota) {
        if (boundQuota == null) {
            return null;
        }

        Map<String, Quantity> statusHard = ResourceQuotaUtils.getStatusHard(boundQuota);
        Quantity hardQuantity = statusHard.get(REQUESTS_STORAGE_QUOTA_RESOURCE_NAME);
        String metricLimit = Optional.ofNullable(hardQuantity)
                .map(Quantity::toSuffixedString)
                .orElse(null);

        Map<String, Quantity> statusUsed = ResourceQuotaUtils.getStatusUsed(boundQuota);
        Quantity usedQuantity = statusUsed.get(REQUESTS_STORAGE_QUOTA_RESOURCE_NAME);
        String metricUsed = Optional.ofNullable(usedQuantity)
                .map(Quantity::toSuffixedString)
                .orElse(null);

        V1alpha1ProjectStatusQuotaMetric storageMetric = new V1alpha1ProjectStatusQuotaMetric();
        storageMetric.setLimit(metricLimit);
        storageMetric.setUsed(metricUsed);

        V1alpha1ProjectStatusQuota statusQuota = new V1alpha1ProjectStatusQuota();
        statusQuota.setPvcStorage(storageMetric);

        return statusQuota;
    }

    private final SubjectResolver subjectResolver;
    private final DockerConfigJsonResolver dockerConfigJsonResolver;
    private final RoleNameResolver roleNameResolver;
    private final AipubUserRoleNameResolver aipubUserRoleNameResolver;
    private final Gson gson;
    private final ObjectMapper mapper;

    public ReconciliationService(SubjectResolver subjectResolver, DockerConfigJsonResolver dockerConfigJsonResolver) {
        this.subjectResolver = subjectResolver;
        this.dockerConfigJsonResolver = dockerConfigJsonResolver;
        this.roleNameResolver = new RoleNameResolver();
        this.aipubUserRoleNameResolver = new AipubUserRoleNameResolver();
        this.gson = new Gson();
        this.mapper = new ObjectMapperFactory().createObjectMapper();
    }

    public List<V1OwnerReference> reconcileOwnerReferences(@Nullable KubernetesObject existing, @Nullable V1alpha1Project project) {
        List<V1OwnerReference> existingReferences = existing == null ? List.of() : K8sObjectUtils.getOwnerReferences(existing);
        List<V1OwnerReference> filtered = removeOwnerReferencesThatReferToProjectKind(existingReferences);

        if (project == null) {
            return filtered;
        }

        List<V1OwnerReference> reconciled = new ArrayList<>(filtered);
        reconciled.add(K8sObjectUtils.buildV1OwnerReference(project, true, true));
        return reconciled;
    }

    public List<V1OwnerReference> reconcileOwnerReferences(@Nullable KubernetesObject existing, @Nullable V1alpha1AipubUser user) {
        List<V1OwnerReference> existingReferences = existing == null ? List.of() : K8sObjectUtils.getOwnerReferences(existing);
        List<V1OwnerReference> filtered = removeOwnerReferencesThatReferToAipubUserKind(existingReferences);

        if (user == null) {
            return filtered;
        }

        List<V1OwnerReference> reconciled = new ArrayList<>(filtered);
        reconciled.add(K8sObjectUtils.buildV1OwnerReference(user, true, true));
        return reconciled;
    }

    public List<V1PolicyRule> reconcileClusterRoleRules(
            V1alpha1Project project,
            ProjectRoleEnum projectRoleEnum,
            List<V1alpha1NodeGroup> bindingNodeGroups,
            List<V1Node> bindingNodes,
            List<V1alpha1ResourceSet> bindingResourceSets) {
        V1PolicyRule projectApiRule = switch (projectRoleEnum) {
            case PROJECT_MANAGER -> new V1PolicyRuleBuilder()
                    .withApiGroups(ProjectApiConstants.PROJECT_GROUP)
                    .withResources(ProjectApiConstants.PROJECT_RESOURCE_PLURAL)
                    .withResourceNames(K8sObjectUtils.getName(project))
                    .withVerbs("get", "update", "patch")
                    .build();
            case PROJECT_DEVELOPER -> new V1PolicyRuleBuilder()
                    .withApiGroups(ProjectApiConstants.PROJECT_GROUP)
                    .withResources(ProjectApiConstants.PROJECT_RESOURCE_PLURAL)
                    .withResourceNames(K8sObjectUtils.getName(project))
                    .withVerbs("get")
                    .build();
        };

        V1PolicyRule namespaceApiRule = switch (projectRoleEnum) {
            case PROJECT_MANAGER, PROJECT_DEVELOPER -> new V1PolicyRuleBuilder()
                    .withApiGroups("")
                    .withResources("namespaces")
                    .withResourceNames(K8sObjectUtils.getName(project))
                    .withVerbs("get")
                    .build();
        };

        List<String> nodeGroups = bindingNodeGroups.stream()
                .map(K8sObjectUtils::getName)
                .toList();
        V1PolicyRule nodeGroupApiRule = switch (projectRoleEnum) {
            case PROJECT_MANAGER, PROJECT_DEVELOPER -> new V1PolicyRuleBuilder()
                    .withApiGroups(ProjectApiConstants.PROJECT_GROUP)
                    .withResources(ProjectApiConstants.NODE_GROUP_RESOURCE_PLURAL)
                    .withResourceNames(nodeGroups)
                    .withVerbs("get")
                    .build();
        };

        List<String> nodes = bindingNodes.stream()
                .map(NodeResourceStatusUtils::getName)
                .toList();
        V1PolicyRule nodeApiRule = switch (projectRoleEnum) {
            case PROJECT_MANAGER, PROJECT_DEVELOPER -> new V1PolicyRuleBuilder()
                    .withApiGroups("")
                    .withResources("nodes")
                    .withResourceNames(nodes)
                    .withVerbs("get")
                    .build();
        };

        //todo --
        V1PolicyRule nodeResourceStatusApiRule = switch (projectRoleEnum) {
            case PROJECT_MANAGER, PROJECT_DEVELOPER -> new V1PolicyRuleBuilder()
                    .withApiGroups(ProjectApiConstants.COASTER_GROUP)
                    .withResources(ProjectApiConstants.NODE_RESOURCE_STATUS_RESOURCE_PLURAL)
                    .withResourceNames(nodes)
                    .withVerbs("get")
                    .build();
        };

        List<String> resourceSetNames = bindingResourceSets.stream()
                .map(K8sObjectUtils::getName)
                .toList();
        V1PolicyRule resourceSetApiRule = switch (projectRoleEnum) {
            case PROJECT_MANAGER, PROJECT_DEVELOPER -> new V1PolicyRuleBuilder()
                    .withApiGroups(ProjectApiConstants.AIPUB_GROUP)
                    .withResources(ProjectApiConstants.RESOURCE_SET_RESOURCE_PLURAL)
                    .withResourceNames(resourceSetNames)
                    .withVerbs("get")
                    .build();
        };

        V1PolicyRule tcpPortValidatorsApiRule = switch (projectRoleEnum) {
            case PROJECT_MANAGER, PROJECT_DEVELOPER -> new V1PolicyRuleBuilder()
                    .withApiGroups(ProjectApiConstants.AIPUB_GROUP)
                    .withResources(ProjectApiConstants.TCP_PORT_VALIDATORS_RESOURCE_PLURAL)
                    .withVerbs("create")
                    .build();
        };
        //todo --

        return List.of(projectApiRule, namespaceApiRule, nodeGroupApiRule, nodeApiRule,
                resourceSetApiRule, nodeResourceStatusApiRule, tcpPortValidatorsApiRule);
    }

    public List<V1PolicyRule> reconcileClusterRoleRules(V1alpha1AipubUser aipubUser) {
        V1PolicyRule aipubUserApiRule = new V1PolicyRuleBuilder()
                .withApiGroups(ProjectApiConstants.PROJECT_GROUP)
                .withResources(ProjectApiConstants.AIPUB_USER_RESOURCE_PLURAL)
                .withResourceNames(K8sObjectUtils.getName(aipubUser))
                .withVerbs("get")
                .build();

        // todo --
        V1PolicyRule userResourceQuotasApiRule = new V1PolicyRuleBuilder()
                .withApiGroups(ProjectApiConstants.AIPUB_GROUP)
                .withResources(ProjectApiConstants.USER_RESOURCE_QUOTA_RESOURCE_PLURAL)
                .withResourceNames(K8sObjectUtils.getName(aipubUser))
                .withVerbs("get")
                .build();
        V1PolicyRule gpuQuotasApiRule = new V1PolicyRuleBuilder()
                .withApiGroups(ProjectApiConstants.AIPUB_GROUP)
                .withResources(ProjectApiConstants.GPU_QUOTA_RESOURCE_PLURAL)
                .withResourceNames(K8sObjectUtils.getName(aipubUser))
                .withVerbs("get")
                .build();
        V1PolicyRule userWorkspaceReclaims = new V1PolicyRuleBuilder()
                .withApiGroups(ProjectApiConstants.AIPUB_GROUP)
                .withResources(ProjectApiConstants.USER_WORKSPACE_RECLAIM_RESOURCE_PLURAL)
                .withResourceNames(K8sObjectUtils.getName(aipubUser))
                .withVerbs("get")
                .build();
        // todo --

        return List.of(aipubUserApiRule, userResourceQuotasApiRule, gpuQuotasApiRule, userWorkspaceReclaims);
    }

    @Nullable
    public V1AggregationRule reconcileClusterRoleAggregationRule(V1alpha1Project project, ProjectRoleEnum projectRoleEnum) {
        return null;
    }

    @Nullable
    public V1AggregationRule reconcileClusterRoleAggregationRule(V1alpha1AipubUser aipubUser) {
        return null;
    }

    public List<V1PolicyRule> reconcileRoleRules(
            V1alpha1Project project,
            ProjectRoleEnum projectRoleEnum) {
        return switch (projectRoleEnum) {
            case PROJECT_MANAGER, PROJECT_DEVELOPER -> {
                V1PolicyRule coreApiRule = new V1PolicyRuleBuilder().withApiGroups("")
                        .withResources(
                                "pods",
                                "services",
                                "configmaps",
                                "secrets",
                                "persistentvolumeclaims",
                                "serviceaccounts",
                                "limitranges",
                                "events",
                                "replicationcontrollers",
                                "endpoints")
                        .withVerbs("*")
                        .build();
                V1PolicyRule eventApiRule = new V1PolicyRuleBuilder().withApiGroups("events.k8s.io")
                        .withResources("events")
                        .withVerbs("*")
                        .build();
                V1PolicyRule batchApiRule = new V1PolicyRuleBuilder().withApiGroups("batch")
                        .withResources("jobs", "cronjobs")
                        .withVerbs("*")
                        .build();
                V1PolicyRule appsApiRule = new V1PolicyRuleBuilder().withApiGroups("apps")
                        .withResources("deployments", "statefulsets", "daemonsets", "replicasets")
                        .withVerbs("*")
                        .build();
                V1PolicyRule networkingApiRule = new V1PolicyRuleBuilder().withApiGroups("networking.k8s.io")
                        .withResources("ingresses")
                        .withVerbs("*")
                        .build();
                V1PolicyRule autoscalingApiRule = new V1PolicyRuleBuilder().withApiGroups("autoscaling")
                        .withResources("horizontalpodautoscalers")
                        .withVerbs("*")
                        .build();
                V1PolicyRule poddisruptionbudgetApiRule = new V1PolicyRuleBuilder().withApiGroups("policy")
                        .withResources("poddisruptionbudgets")
                        .withVerbs("*")
                        .build();

                //todo --
                V1PolicyRule aipubOperationApiRule = new V1PolicyRuleBuilder().withApiGroups("aipub.ten1010.io")
                        .withResources("operations")
                        .withVerbs("*")
                        .build();
                //todo --
                V1PolicyRule aipubWorkspaceApiRule = new V1PolicyRuleBuilder().withApiGroups("aipub.ten1010.io")
                        .withResources("workspaces")
                        .withVerbs("*")
                        .build();
                V1PolicyRule aipubJobsApiRule = new V1PolicyRuleBuilder().withApiGroups("aipub.ten1010.io")
                        .withResources("aipubjobs")
                        .withVerbs("*")
                        .build();
                V1PolicyRule aipubOperationRevisionApiRule = new V1PolicyRuleBuilder().withApiGroups("aipub.ten1010.io")
                        .withResources("operationrevisions")
                        .withVerbs("*")
                        .build();
                V1PolicyRule aipubFtpServerApiRule = new V1PolicyRuleBuilder().withApiGroups("aipub.ten1010.io")
                        .withResources("ftpservers")
                        .withVerbs("*")
                        .build();
                V1PolicyRule aipubVolumesApiRule = new V1PolicyRuleBuilder().withApiGroups("aipub.ten1010.io")
                        .withResources("aipubvolumes")
                        .withVerbs("*")
                        .build();
                //todo --

                yield List.of(
                        coreApiRule,
                        eventApiRule,
                        batchApiRule,
                        appsApiRule,
                        networkingApiRule,
                        autoscalingApiRule,
                        poddisruptionbudgetApiRule,
                        aipubOperationApiRule,
                        aipubOperationRevisionApiRule,
                        aipubWorkspaceApiRule,
                        aipubJobsApiRule,
                        aipubFtpServerApiRule,
                        aipubVolumesApiRule
                );
            }
        };
    }

    public V1RoleRef reconcileClusterRoleRef(V1alpha1Project project, ProjectRoleEnum projRoleEnum) {
        String roleName = this.roleNameResolver.resolveRoleName(K8sObjectUtils.getName(project), projRoleEnum);
        return new V1RoleRefBuilder()
                .withApiGroup("rbac.authorization.k8s.io")
                .withKind("ClusterRole")
                .withName(roleName)
                .build();
    }

    public V1RoleRef reconcileClusterRoleRef(V1alpha1AipubUser user) {
        String roleName = this.aipubUserRoleNameResolver.resolveRoleName(K8sObjectUtils.getName(user));
        return new V1RoleRefBuilder()
                .withApiGroup("rbac.authorization.k8s.io")
                .withKind("ClusterRole")
                .withName(roleName)
                .build();
    }

    public V1RoleRef reconcileRoleRef(V1alpha1Project project, ProjectRoleEnum projRoleEnum) {
        String roleName = this.roleNameResolver.resolveRoleName(K8sObjectUtils.getName(project), projRoleEnum);
        return new V1RoleRefBuilder()
                .withApiGroup("rbac.authorization.k8s.io")
                .withKind("Role")
                .withName(roleName)
                .build();
    }

    public List<RbacV1Subject> reconcileSubjects(V1alpha1Project project, ProjectRoleEnum projRoleEnum) {
        return ProjectUtils.getSpecMembers(project)
                .stream()
                .filter(e -> ProjectRoleEnum.memberHasRole(e, projRoleEnum))
                .map(this.subjectResolver::resolve)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    public List<RbacV1Subject> reconcileSubjects(V1alpha1AipubUser user) {
        return this.subjectResolver.resolve(user)
                .map(List::of)
                .orElse(List.of());
    }

    public V1ResourceQuotaSpec reconcileQuotaSpec(V1alpha1Project project) {
        V1ResourceQuotaSpecBuilder builder = new V1ResourceQuotaSpecBuilder();
        Optional<String> pvcStorageQuotaOpt = ProjectUtils.getSpecPvcStorageQuota(project);
        pvcStorageQuotaOpt.ifPresent(quota -> builder.addToHard(REQUESTS_STORAGE_QUOTA_RESOURCE_NAME, Quantity.fromString(quota)));

        return builder.build();
    }

    public String reconcileImagePullSecretType(V1alpha1Project project) {
        return "kubernetes.io/dockerconfigjson";
    }

    public Map<String, byte[]> reconcileImagePullSecretData(V1alpha1Project project) {
        try {
            Map<String, Object> dockerConfigJson = this.dockerConfigJsonResolver.resolve(project);
            String jsonStr = this.mapper.writeValueAsString(dockerConfigJson);
            return Map.of(".dockerconfigjson", jsonStr.getBytes());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, String> reconcileNodeLabels(V1Node existing) {
        Map<String, String> existingLabels = K8sObjectUtils.getLabels(existing);
        Map<String, String> reconciled = new HashMap<>(existingLabels);

        String projectManagedValue = reconciled.get(LabelConstants.PROJECT_MANAGED_KEY);
        String isolationModeValue = reconciled.get(LabelConstants.ISOLATION_MODE_KEY);

        if (projectManagedValue == null ||
                ProjectManagedValueEnum.getEnum(projectManagedValue).isEmpty() ||
                !ProjectManagedValueEnum.getEnum(projectManagedValue).get().equals(ProjectManagedValueEnum.TRUE)) {
            reconciled.put(LabelConstants.PROJECT_MANAGED_KEY, ProjectManagedValueEnum.FALSE.getStr());
            reconciled.remove(LabelConstants.ISOLATION_MODE_KEY);
        } else {
            if (isolationModeValue == null ||
                    IsolationModeValueEnum.getEnum(isolationModeValue).isEmpty()) {
                reconciled.put(LabelConstants.ISOLATION_MODE_KEY, IsolationModeValueEnum.LENIENT.getStr());
            }
        }

        return reconciled;
    }

    public V1alpha1ProjectStatus reconcileProjectStatus(
            V1alpha1Project existing,
            List<V1alpha1AipubUser> boundAipubUsers,
            @Nullable V1ResourceQuota boundQuota,
            List<V1alpha1NodeGroup> boundNodeGroups,
            List<V1Node> boundNodes,
            List<V1alpha1ImageHub> boundImageHubs) {
        V1alpha1ProjectStatus status = new V1alpha1ProjectStatus();
        status.setAllBoundAipubUsers(K8sObjectUtils.getNames(boundAipubUsers));
        status.setQuota(buildProjectStatusQuota(boundQuota));
        status.setAllBoundNodeGroups(K8sObjectUtils.getNames(boundNodeGroups));
        status.setAllBoundNodes(K8sObjectUtils.getNames(boundNodes));
        status.setAllBoundImageHubs(K8sObjectUtils.getNames(boundImageHubs));

        return status;
    }

    public V1alpha1AipubUserStatus reconcileAipubUserStatus(
            V1alpha1AipubUser existing,
            List<V1alpha1Project> boundProjects,
            List<V1alpha1ImageHub> boundImageHubs) {
        V1alpha1AipubUserStatus status = new V1alpha1AipubUserStatus();
        status.setAllBoundProjects(K8sObjectUtils.getNames(boundProjects));
        status.setAllBoundImageHubs(K8sObjectUtils.getNames(boundImageHubs));

        return status;
    }

    public V1alpha1NodeGroupStatus reconcileNodeGroupStatus(V1alpha1NodeGroup existing, List<V1alpha1Project> boundProjects, List<V1Node> boundNodes) {
        V1alpha1NodeGroupStatus status = new V1alpha1NodeGroupStatus();
        status.setAllBoundProjects(K8sObjectUtils.getNames(boundProjects));
        status.setAllBoundNodes(K8sObjectUtils.getNames(boundNodes));

        return status;
    }

    public V1alpha1ImageHubStatus reconcileImageHubStatus(
            V1alpha1ImageHub existing,
            List<V1alpha1Project> boundProjects,
            List<V1alpha1AipubUser> boundAipubUsers) {
        V1alpha1ImageHubStatus status = new V1alpha1ImageHubStatus();
        status.setAllBoundProjects(K8sObjectUtils.getNames(boundProjects));
        status.setAllBoundAipubUsers(K8sObjectUtils.getNames(boundAipubUsers));

        return status;
    }

    public Map<String, String> reconcileNodeAnnotations(V1Node existing, List<V1alpha1Project> boundProjects, List<V1alpha1NodeGroup> boundNodeGroups) {
        Map<String, String> reconciled = Optional.of(K8sObjectUtils.getAnnotations(existing))
                .map(ReconciliationService::removeAnnotationWithBoundProjectsKey)
                .map(ReconciliationService::removeAnnotationWithBoundNodeGroupsKey)
                .get();
        if (!NodeUtils.isProjectManaged(existing)) {
            return reconciled;
        }

        reconciled = new HashMap<>(reconciled);
        reconciled.putAll(buildBoundProjectsAnnotation(boundProjects));
        reconciled.putAll(buildBoundNodeGroupsAnnotation(boundNodeGroups));

        return reconciled;
    }

    public List<V1Taint> reconcileTaints(V1Node existing) {
        List<V1Taint> existingTaints = NodeUtils.getTaints(existing);
        List<V1Taint> reconciled = new ArrayList<>(removeProjectManagedTaints(existingTaints));
        if (!NodeUtils.isProjectManaged(existing)) {
            return reconciled;
        }

        List<V1Taint> projectManagedTaints = buildProjectManagedTaints(
                K8sObjectUtils.getName(existing),
                NodeUtils.isStrictIsolationMode(existing));
        reconciled.addAll(projectManagedTaints);

        return reconciled;
    }

    public List<V1Toleration> reconcileTolerations(V1Pod existing, List<V1Node> allowedProjectNodes) {
        List<V1Toleration> existingTolerations = WorkloadUtils.getTolerations(existing);
        return reconcileTolerations(existingTolerations, allowedProjectNodes);
    }

    public List<V1Toleration> reconcileTolerations(V1PodTemplateSpec existing, List<V1Node> allowedProjectNodes) {
        List<V1Toleration> existingTolerations = WorkloadUtils.getTolerations(existing);
        return reconcileTolerations(existingTolerations, allowedProjectNodes);
    }

    public List<V1NodeSelectorTerm> reconcileNodeSelectorTerms(V1Pod existing, @Nullable V1alpha1Project project) {
        List<V1NodeSelectorTerm> existingNodeSelectorTerms = WorkloadUtils.getNodeSelectorTerms(existing);
        List<V1NodeSelectorTerm> reconciled = new ArrayList<>(removeProjectManagedNodeSelectorTerms(existingNodeSelectorTerms));
        if (project != null) {
            reconciled.add(buildProjectManagedNodeSelectorTerm());
        }

        return reconciled;
    }

    public List<V1NodeSelectorTerm> reconcileNodeSelectorTerms(V1PodTemplateSpec existing, @Nullable V1alpha1Project project) {
        List<V1NodeSelectorTerm> existingNodeSelectorTerms = WorkloadUtils.getNodeSelectorTerms(existing);
        List<V1NodeSelectorTerm> reconciled = new ArrayList<>(removeProjectManagedNodeSelectorTerms(existingNodeSelectorTerms));
        if (project != null) {
            reconciled.add(buildProjectManagedNodeSelectorTerm());
        }

        return reconciled;
    }

    public List<V1LocalObjectReference> reconcileImagePullSecrets(V1Pod existing, @Nullable V1alpha1Project project) {
        if (project == null) {
            return WorkloadUtils.getImagePullSecrets(existing);
        }

        String secretName = new ImagePullSecretNameResolver().resolveSecretName(K8sObjectUtils.getName(project));
        List<V1LocalObjectReference> reconciled = WorkloadUtils.getImagePullSecrets(existing).stream()
                .filter(e -> Objects.nonNull(e.getName()))
                .filter(e -> !e.getName().equals(secretName))
                .toList();
        reconciled = new ArrayList<>(reconciled);
        V1LocalObjectReference ref = new V1LocalObjectReferenceBuilder()
                .withName(secretName)
                .build();
        reconciled.add(ref);

        return reconciled;
    }

    public List<V1LocalObjectReference> reconcileImagePullSecrets(V1PodTemplateSpec existing, @Nullable V1alpha1Project project) {
        if (project == null) {
            return WorkloadUtils.getImagePullSecrets(existing);
        }

        String secretName = new ImagePullSecretNameResolver().resolveSecretName(K8sObjectUtils.getName(project));
        List<V1LocalObjectReference> reconciled = WorkloadUtils.getImagePullSecrets(existing).stream()
                .filter(e -> Objects.nonNull(e.getName()))
                .filter(e -> !e.getName().equals(secretName))
                .toList();
        reconciled = new ArrayList<>(reconciled);
        V1LocalObjectReference ref = new V1LocalObjectReferenceBuilder()
                .withName(secretName)
                .build();
        reconciled.add(ref);

        return reconciled;
    }

    private Map<String, String> buildBoundProjectsAnnotation(List<V1alpha1Project> boundProjects) {
        List<String> names = boundProjects.stream()
                .map(K8sObjectUtils::getName)
                .toList();
        String namesJson = this.gson.toJson(names);
        return Map.of(AnnotationConstants.BOUND_PROJECTS_KEY, namesJson);
    }

    private Map<String, String> buildBoundNodeGroupsAnnotation(List<V1alpha1NodeGroup> boundNodeGroups) {
        List<String> names = boundNodeGroups.stream()
                .map(K8sObjectUtils::getName)
                .toList();
        String namesJson = this.gson.toJson(names);
        return Map.of(AnnotationConstants.BOUND_NODE_GROUPS_KEY, namesJson);
    }

}
