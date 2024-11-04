package io.ten1010.aipub.projectcontroller.controller.watch;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.aipub.projectcontroller.controller.BoundObjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.*;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageNamespace;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroup;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.informer.IndexerConstants;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class RequestBuilderFactory {

    private final SharedInformerFactory sharedInformerFactory;
    private final BoundObjectResolver boundObjectResolver;
    private final NamespaceNameResolver namespaceNameResolver;
    private final RoleNameResolver roleNameResolver;
    private final AipubUserRoleNameResolver aipubUserRoleNameResolver;
    private final ResourceQuotaNameResolver quotaNameResolver;
    private final ImagePullSecretNameResolver imagePullSecretNameResolver;

    public RequestBuilderFactory(SharedInformerFactory sharedInformerFactory) {
        this.sharedInformerFactory = sharedInformerFactory;
        this.boundObjectResolver = new BoundObjectResolver(sharedInformerFactory);
        this.namespaceNameResolver = new NamespaceNameResolver();
        this.roleNameResolver = new RoleNameResolver();
        this.aipubUserRoleNameResolver = new AipubUserRoleNameResolver();
        this.quotaNameResolver = new ResourceQuotaNameResolver();
        this.imagePullSecretNameResolver = new ImagePullSecretNameResolver();
    }

    public Function<V1alpha1Project, List<Request>> projectToNamespaces() {
        return project -> {
            String projName = K8sObjectUtils.getName(project);
            String nsName = this.namespaceNameResolver.resolveNamespaceName(projName);
            return List.of(new Request(nsName));
        };
    }

    public Function<V1Namespace, List<Request>> namespaceToProjects() {
        return namespace -> {
            String nsName = K8sObjectUtils.getName(namespace);
            String projName = this.namespaceNameResolver.resolveProjectName(nsName);
            return List.of(new Request(projName));
        };
    }

    public Function<V1alpha1Project, List<Request>> projectToBoundAipubUsers() {
        return project -> this.boundObjectResolver.getAllBoundAipubUsers(project).stream()
                .map(K8sObjectUtils::getName)
                .map(Request::new)
                .toList();
    }

    public Function<V1alpha1Project, List<Request>> projectToBoundNodeGroups() {
        return project -> this.boundObjectResolver.getAllBoundNodeGroups(project).stream()
                .map(K8sObjectUtils::getName)
                .map(Request::new)
                .toList();
    }

    public Function<V1alpha1Project, List<Request>> projectToBoundImageNamespaces() {
        return project -> this.boundObjectResolver.getAllBoundImageNamespaces(project).stream()
                .map(K8sObjectUtils::getName)
                .map(Request::new)
                .toList();
    }

    public Function<V1alpha1Project, List<Request>> projectToBoundNodes() {
        return project -> this.boundObjectResolver.getAllBoundNodes(project).stream()
                .map(K8sObjectUtils::getName)
                .map(Request::new)
                .toList();
    }

    public Function<V1alpha1Project, List<Request>> projectToRoles(boolean namespacedRole) {
        return project -> {
            String projName = K8sObjectUtils.getName(project);
            String adminRole = this.roleNameResolver.resolveRoleName(projName, ProjectRoleEnum.PROJECT_ADMIN);
            String developerRole = this.roleNameResolver.resolveRoleName(projName, ProjectRoleEnum.PROJECT_DEVELOPER);
            if (namespacedRole) {
                String namespace = this.namespaceNameResolver.resolveNamespaceName(projName);
                return List.of(new Request(namespace, adminRole), new Request(namespace, developerRole));
            }
            return List.of(new Request(adminRole), new Request(developerRole));
        };
    }

    public Function<V1alpha1Project, List<Request>> projectToResourceQuotas() {
        return project -> {
            String projName = K8sObjectUtils.getName(project);
            String namespace = this.namespaceNameResolver.resolveNamespaceName(projName);
            String quotaName = this.quotaNameResolver.resolveQuotaName(projName);
            return List.of(new Request(namespace, quotaName));
        };
    }

    public Function<V1alpha1Project, List<Request>> projectToImagePullSecrets() {
        return project -> {
            String projName = K8sObjectUtils.getName(project);
            String namespace = this.namespaceNameResolver.resolveNamespaceName(projName);
            String secretName = this.imagePullSecretNameResolver.resolveSecretName(projName);
            return List.of(new Request(namespace, secretName));
        };
    }

    public Function<V1Secret, List<Request>> secretToImagePullSecrets() {
        return secret -> {
            Optional<String> opt = this.imagePullSecretNameResolver.resolveProjectName(K8sObjectUtils.getName(secret));
            if (opt.isPresent()) {
                return List.of(new Request(K8sObjectUtils.getNamespace(secret), K8sObjectUtils.getName(secret)));
            }
            return List.of();
        };
    }

    public Function<V1alpha1Project, List<Request>> projectToNamespacedObjects(Class<? extends KubernetesObject> objectClass) {
        Indexer<? extends KubernetesObject> objectIndexer = this.sharedInformerFactory.getExistingSharedIndexInformer(objectClass).getIndexer();
        return project -> {
            String namespace = this.namespaceNameResolver.resolveNamespaceName(K8sObjectUtils.getName(project));
            List<? extends KubernetesObject> objects = objectIndexer.byIndex(
                    IndexerConstants.NAMESPACE_TO_OBJECTS_INDEXER_NAME,
                    namespace);
            return objects.stream()
                    .map(e -> new Request(K8sObjectUtils.getNamespace(e), K8sObjectUtils.getName(e)))
                    .toList();
        };
    }

    public Function<V1alpha1AipubUser, List<Request>> aipubUserToClusterRoles() {
        return user -> {
            String userName = K8sObjectUtils.getName(user);
            String roleName = this.aipubUserRoleNameResolver.resolveRoleName(userName);
            return List.of(new Request(roleName));
        };
    }

    public Function<V1alpha1AipubUser, List<Request>> aipubUserToBoundProjects() {
        return aipubUser -> this.boundObjectResolver.getAllBoundProjects(aipubUser).stream()
                .map(K8sObjectUtils::getName)
                .map(Request::new)
                .toList();
    }

    public Function<V1ResourceQuota, List<Request>> quotaToBoundProjects() {
        return quota -> {
            String quotaName = K8sObjectUtils.getName(quota);
            Optional<String> projNameOpt = this.quotaNameResolver.resolveProjectName(quotaName);
            return projNameOpt
                    .map(Request::new)
                    .map(List::of)
                    .orElse(List.of());
        };
    }

    public Function<V1alpha1NodeGroup, List<Request>> nodeGroupToBoundProjects() {
        return nodeGroup -> this.boundObjectResolver.getAllBoundProjects(nodeGroup).stream()
                .map(K8sObjectUtils::getName)
                .map(Request::new)
                .toList();
    }

    public Function<V1alpha1NodeGroup, List<Request>> nodeGroupToBoundNodes() {
        return nodeGroup -> this.boundObjectResolver.getAllBoundNodes(nodeGroup).stream()
                .map(K8sObjectUtils::getName)
                .map(Request::new)
                .toList();
    }

    public Function<V1alpha1NodeGroup, List<Request>> nodeGroupToNamespacedObjects(Class<? extends KubernetesObject> objectClass) {
        Function<V1alpha1Project, List<Request>> projectToNamespacedObjects = projectToNamespacedObjects(objectClass);
        return nodeGroup -> {
            List<V1alpha1Project> projects = this.boundObjectResolver.getAllBoundProjects(nodeGroup);
            return projects.stream()
                    .flatMap(e -> projectToNamespacedObjects.apply(e).stream())
                    .toList();
        };
    }

    public Function<V1alpha1NodeGroup, List<Request>> nodeGroupToRoles(boolean namespacedRole) {
        Function<V1alpha1Project, List<Request>> projectToRoles = projectToRoles(namespacedRole);
        return nodeGroup -> this.boundObjectResolver.getAllBoundProjects(nodeGroup).stream()
                .flatMap(project -> projectToRoles.apply(project).stream())
                .toList();
    }

    public Function<V1alpha1NodeGroup, List<Request>> nodeGroupToAllDaemonSet() {
        Indexer<? extends KubernetesObject> daemonSetIndexer = this.sharedInformerFactory.getExistingSharedIndexInformer(V1DaemonSet.class).getIndexer();
        return nodeGroup -> daemonSetIndexer.list().stream()
                .map(e -> new Request(K8sObjectUtils.getNamespace(e), K8sObjectUtils.getName(e)))
                .toList();
    }

    public Function<V1alpha1ImageNamespace, List<Request>> imageNamespaceToBoundProjects() {
        return imgNs -> this.boundObjectResolver.getAllBoundProjects(imgNs).stream()
                .map(K8sObjectUtils::getName)
                .map(Request::new)
                .toList();
    }

    public Function<V1Node, List<Request>> nodeToProjects() {
        return node -> this.boundObjectResolver.getAllBoundProjects(node).stream()
                .map(K8sObjectUtils::getName)
                .map(Request::new)
                .toList();
    }

    public Function<V1Node, List<Request>> nodeToBoundNodeGroups() {
        return node -> this.boundObjectResolver.getAllBoundNodeGroups(node).stream()
                .map(K8sObjectUtils::getName)
                .map(Request::new)
                .toList();
    }

    public Function<V1Node, List<Request>> nodeToRoles(boolean namespacedRole) {
        Function<V1alpha1Project, List<Request>> projectToRoles = projectToRoles(namespacedRole);
        return node -> this.boundObjectResolver.getAllBoundProjects(node).stream()
                .flatMap(project -> projectToRoles.apply(project).stream())
                .toList();
    }

    public Function<V1Node, List<Request>> nodeToNamespacedObjects(Class<? extends KubernetesObject> objectClass) {
        Function<V1alpha1Project, List<Request>> projectToNamespacedObjects = projectToNamespacedObjects(objectClass);
        return node -> {
            List<V1alpha1Project> projects = this.boundObjectResolver.getAllBoundProjects(node);
            return projects.stream()
                    .flatMap(e -> projectToNamespacedObjects.apply(e).stream())
                    .toList();
        };
    }

    public Function<V1Node, List<Request>> nodeToBoundPods() {
        Indexer<V1Pod> podIndexer = this.sharedInformerFactory.getExistingSharedIndexInformer(V1Pod.class).getIndexer();
        return node -> {
            List<V1Pod> podsBoundToNode = podIndexer.byIndex(IndexerConstants.NODE_NAME_TO_POD_INDEXER_NAME, K8sObjectUtils.getName(node));
            return podsBoundToNode.stream()
                    .map(pod -> new Request(K8sObjectUtils.getNamespace(pod), K8sObjectUtils.getName(pod)))
                    .toList();
        };
    }

}
