package io.ten1010.aipub.projectcontroller.controller.watch;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1ResourceQuota;
import io.kubernetes.client.openapi.models.V1Secret;
import io.ten1010.aipub.projectcontroller.controller.BoundObjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.AipubUserRoleNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ImageRegistrySecretNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ProjectRoleEnum;
import io.ten1010.aipub.projectcontroller.domain.k8s.ResourceQuotaNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.RoleNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1Workspace;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubVolume;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ChainJob;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageHub;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroup;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Operation;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ResourceSet;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1SftpServer;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.AipubUserUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.UsernameUtils;
import io.ten1010.aipub.projectcontroller.informer.IndexerConstants;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RequestBuilderFactory {

  private final SharedInformerFactory sharedInformerFactory;
  private final BoundObjectResolver boundObjectResolver;
  private final NamespaceNameResolver namespaceNameResolver;
  private final RoleNameResolver roleNameResolver;
  private final AipubUserRoleNameResolver aipubUserRoleNameResolver;
  private final ResourceQuotaNameResolver quotaNameResolver;
  private final ImageRegistrySecretNameResolver imageRegistrySecretNameResolver;

  public RequestBuilderFactory(SharedInformerFactory sharedInformerFactory) {
    this.sharedInformerFactory = sharedInformerFactory;
    this.boundObjectResolver = new BoundObjectResolver(sharedInformerFactory);
    this.namespaceNameResolver = new NamespaceNameResolver();
    this.roleNameResolver = new RoleNameResolver();
    this.aipubUserRoleNameResolver = new AipubUserRoleNameResolver();
    this.quotaNameResolver = new ResourceQuotaNameResolver();
    this.imageRegistrySecretNameResolver = new ImageRegistrySecretNameResolver();
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

  public Function<V1alpha1Project, List<Request>> projectToBoundImageHubs() {
    return project -> this.boundObjectResolver.getAllBoundImageHubs(project).stream()
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

  public Function<V1alpha1Project, List<Request>> projectToProjectRoles(boolean namespacedRole) {
    return project -> {
      String projName = K8sObjectUtils.getName(project);
      String adminRole = this.roleNameResolver.resolveRoleName(projName,
          ProjectRoleEnum.PROJECT_MANAGER);
      String developerRole = this.roleNameResolver.resolveRoleName(projName,
          ProjectRoleEnum.PROJECT_DEVELOPER);
      if (namespacedRole) {
        String namespace = this.namespaceNameResolver.resolveNamespaceName(projName);
        return List.of(new Request(namespace, adminRole), new Request(namespace, developerRole));
      }
      return List.of(new Request(adminRole), new Request(developerRole));
    };
  }

  public Function<V1alpha1Project, List<Request>> projectToAipubUserRoles() {
    return project -> {
      String projName = K8sObjectUtils.getName(project);
      String namespace = this.namespaceNameResolver.resolveNamespaceName(projName);
      return this.boundObjectResolver.getAllBoundAipubUsers(project)
          .stream()
          .map(K8sObjectUtils::getName)
          .map(this.aipubUserRoleNameResolver::resolveRoleName)
          .map(roleName -> new Request(projName, roleName))
          .toList();
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

  public Function<V1alpha1Project, List<Request>> projectToImageRegistrySecrets() {
    return project -> {
      String projName = K8sObjectUtils.getName(project);
      String namespace = this.namespaceNameResolver.resolveNamespaceName(projName);
      String secretName = this.imageRegistrySecretNameResolver.resolveSecretName(projName);
      return List.of(new Request(namespace, secretName));
    };
  }

  public Function<V1Secret, List<Request>> secretToImageRegistrySecrets() {
    return secret -> {
      Optional<String> opt = this.imageRegistrySecretNameResolver.resolveProjectName(
          K8sObjectUtils.getName(secret));
      if (opt.isPresent()) {
        return List.of(
            new Request(K8sObjectUtils.getNamespace(secret), K8sObjectUtils.getName(secret)));
      }
      return List.of();
    };
  }

  public Function<V1alpha1Project, List<Request>> projectToNamespacedObjects(
      Class<? extends KubernetesObject> objectClass) {
    Indexer<? extends KubernetesObject> objectIndexer = this.sharedInformerFactory.getExistingSharedIndexInformer(
        objectClass).getIndexer();
    return project -> {
      String namespace = this.namespaceNameResolver.resolveNamespaceName(
          K8sObjectUtils.getName(project));
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

  public Function<V1alpha1AipubUser, List<Request>> aipubUserToAipubUserRoles() {
    return user -> {
      String userName = K8sObjectUtils.getName(user);
      String roleName = this.aipubUserRoleNameResolver.resolveRoleName(userName);
      return AipubUserUtils.getAllBoundProjects(user)
          .stream()
          .map(projName -> new Request(projName, roleName))
          .toList();
    };
  }

  public Function<V1alpha1AipubUser, List<Request>> aipubUserToProjectRoles(
      boolean namespacedRole) {
    Function<V1alpha1Project, List<Request>> projectToRoles = projectToProjectRoles(namespacedRole);
    return user -> this.boundObjectResolver.getAllBoundProjects(user).stream()
        .flatMap(project -> projectToRoles.apply(project).stream())
        .toList();
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

  public Function<V1alpha1NodeGroup, List<Request>> nodeGroupToNamespacedObjects(
      Class<? extends KubernetesObject> objectClass) {
    Function<V1alpha1Project, List<Request>> projectToNamespacedObjects = projectToNamespacedObjects(
        objectClass);
    return nodeGroup -> {
      List<V1alpha1Project> projects = this.boundObjectResolver.getAllBoundProjects(nodeGroup);
      return projects.stream()
          .flatMap(e -> projectToNamespacedObjects.apply(e).stream())
          .toList();
    };
  }

  public Function<V1alpha1NodeGroup, List<Request>> nodeGroupToRoles(boolean namespacedRole) {
    Function<V1alpha1Project, List<Request>> projectToRoles = projectToProjectRoles(namespacedRole);
    return nodeGroup -> this.boundObjectResolver.getAllBoundProjects(nodeGroup).stream()
        .flatMap(project -> projectToRoles.apply(project).stream())
        .toList();
  }

  public Function<V1alpha1NodeGroup, List<Request>> nodeGroupToAllDaemonSet() {
    Indexer<? extends KubernetesObject> daemonSetIndexer = this.sharedInformerFactory.getExistingSharedIndexInformer(
        V1DaemonSet.class).getIndexer();
    return nodeGroup -> daemonSetIndexer.list().stream()
        .map(e -> new Request(K8sObjectUtils.getNamespace(e), K8sObjectUtils.getName(e)))
        .toList();
  }

  public Function<V1alpha1ResourceSet, List<Request>> resourceSetToRoles() {
    Function<V1alpha1Project, List<Request>> projectToRoles = projectToProjectRoles(false);
    return resourceSet -> {
      List<V1Node> allBoundNodes = this.boundObjectResolver.getAllBoundNodes(resourceSet);
      return allBoundNodes.stream()
          .flatMap(node ->
              this.boundObjectResolver.getAllBoundProjects(node).stream()
                  .flatMap(project -> projectToRoles.apply(project).stream())
          )
          .collect(Collectors.toList());
    };
  }

  public Function<V1alpha1ImageHub, List<Request>> imageHubToBoundProjects() {
    return imgHub -> this.boundObjectResolver.getAllBoundProjects(imgHub).stream()
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
    Function<V1alpha1Project, List<Request>> projectToRoles = projectToProjectRoles(namespacedRole);
    return node -> this.boundObjectResolver.getAllBoundProjects(node).stream()
        .flatMap(project -> projectToRoles.apply(project).stream())
        .toList();
  }

  public Function<V1Node, List<Request>> nodeToNamespacedObjects(
      Class<? extends KubernetesObject> objectClass) {
    Function<V1alpha1Project, List<Request>> projectToNamespacedObjects = projectToNamespacedObjects(
        objectClass);
    return node -> {
      List<V1alpha1Project> projects = this.boundObjectResolver.getAllBoundProjects(node);
      return projects.stream()
          .flatMap(e -> projectToNamespacedObjects.apply(e).stream())
          .toList();
    };
  }

  public Function<V1Node, List<Request>> nodeToBoundPods() {
    Indexer<V1Pod> podIndexer = this.sharedInformerFactory.getExistingSharedIndexInformer(
        V1Pod.class).getIndexer();
    return node -> {
      List<V1Pod> podsBoundToNode = podIndexer.byIndex(
          IndexerConstants.NODE_NAME_TO_POD_INDEXER_NAME, K8sObjectUtils.getName(node));
      return podsBoundToNode.stream()
          .map(pod -> new Request(K8sObjectUtils.getNamespace(pod), K8sObjectUtils.getName(pod)))
          .toList();
    };
  }

  public Function<V1Workspace, List<Request>> workspaceToAipubUserRoles() {
    return job -> {
      String projName = K8sObjectUtils.getNamespace(job);
      Optional<String> usernameOpt = UsernameUtils.getUsername(job);
      if (usernameOpt.isPresent()) {
        String roleName = this.aipubUserRoleNameResolver.resolveRoleName(usernameOpt.get());
        return List.of(new Request(projName, roleName));
      }
      return List.of();
    };
  }

  public Function<V1alpha1ChainJob, List<Request>> chainJobToAipubUserRoles() {
    return chainJob -> {
      String projName = K8sObjectUtils.getNamespace(chainJob);
      Optional<String> usernameOpt = UsernameUtils.getUsername(chainJob);

      if (usernameOpt.isPresent()) {
        String roleName = this.aipubUserRoleNameResolver.resolveRoleName(usernameOpt.get());
        return List.of(new Request(projName, roleName));
      }
      return List.of();
    };
  }

  public Function<V1Job, List<Request>> jobToAipubUserRoles() {
    return job -> {
      String projName = K8sObjectUtils.getNamespace(job);
      Optional<String> usernameOpt = UsernameUtils.getUsername(job);

      if (usernameOpt.isPresent()) {
        String roleName = this.aipubUserRoleNameResolver.resolveRoleName(usernameOpt.get());
        return List.of(new Request(projName, roleName));
      }
      return List.of();
    };
  }

  public Function<V1CronJob, List<Request>> cronJobToAipubUserRoles() {
    return cronJob -> {
      String projName = K8sObjectUtils.getNamespace(cronJob);
      Optional<String> usernameOpt = UsernameUtils.getUsername(cronJob);

      if (usernameOpt.isPresent()) {
        String roleName = this.aipubUserRoleNameResolver.resolveRoleName(usernameOpt.get());
        return List.of(new Request(projName, roleName));
      }
      return List.of();
    };
  }

  public Function<V1alpha1Operation, List<Request>> operationToAipubUserRoles() {
    return operation -> {
      String projName = K8sObjectUtils.getNamespace(operation);
      Optional<String> usernameOpt = UsernameUtils.getUsername(operation);

      if (usernameOpt.isPresent()) {
        String roleName = this.aipubUserRoleNameResolver.resolveRoleName(usernameOpt.get());
        return List.of(new Request(projName, roleName));
      }
      return List.of();
    };
  }

  public Function<V1alpha1AipubVolume, List<Request>> aipubVolumeToAipubUserRoles() {
    return aipubVolume -> {
      String projName = K8sObjectUtils.getNamespace(aipubVolume);
      Optional<String> usernameOpt = UsernameUtils.getUsername(aipubVolume);

      if (usernameOpt.isPresent()) {
        String roleName = this.aipubUserRoleNameResolver.resolveRoleName(usernameOpt.get());
        return List.of(new Request(projName, roleName));
      }
      return List.of();
    };
  }

  public Function<V1alpha1SftpServer, List<Request>> sftpServerToAipubUserRoles() {
    return sftpServer -> {
      String projName = K8sObjectUtils.getNamespace(sftpServer);
      Optional<String> usernameOpt = UsernameUtils.getUsername(sftpServer);

      if (usernameOpt.isPresent()) {
        String roleName = this.aipubUserRoleNameResolver.resolveRoleName(usernameOpt.get());
        return List.of(new Request(projName, roleName));
      }
      return List.of();
    };
  }

}
