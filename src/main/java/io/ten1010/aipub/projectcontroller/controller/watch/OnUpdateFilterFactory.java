package io.ten1010.aipub.projectcontroller.controller.watch;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1ResourceQuota;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.ten1010.aipub.projectcontroller.domain.k8s.AipubUserRoleNameResolver;
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
import io.ten1010.aipub.projectcontroller.domain.k8s.util.NodeUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.ProjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.RoleUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.WorkloadUtils;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;

public class OnUpdateFilterFactory {

  public <T extends KubernetesObject> BiPredicate<T, T> alwaysTrueFilter() {
    return (oldObj, newObj) -> true;
  }

  public <T extends KubernetesObject> BiPredicate<T, T> alwaysFalseFilter() {
    return (oldObj, newObj) -> false;
  }

  public <T extends KubernetesObject> BiPredicate<T, T> projectNamespaceFilter() {
    return (oldObj, newObj) ->
        !K8sObjectUtils.getOwnerReferences(oldObj).equals(K8sObjectUtils.getOwnerReferences(newObj))
            || !K8sObjectUtils.getLabels(oldObj).equals(K8sObjectUtils.getLabels(newObj));
  }

  public BiPredicate<V1alpha1Project, V1alpha1Project> projectSpecFieldFilter() {
    return (oldObj, newObj) -> !Objects.equals(oldObj.getSpec(), newObj.getSpec());
  }

  public BiPredicate<V1alpha1Project, V1alpha1Project> projectSpecQuotaFieldFilter() {
    return (oldObj, newObj) -> !ProjectUtils.getSpecQuota(oldObj)
        .equals(ProjectUtils.getSpecQuota(newObj));
  }

  public BiPredicate<V1alpha1Project, V1alpha1Project> projectSpecBindingImageHubsFieldFilter() {
    return (oldObj, newObj) -> !ProjectUtils.getSpecBindingImageHubs(oldObj)
        .equals(ProjectUtils.getSpecBindingImageHubs(newObj));
  }

  public BiPredicate<V1alpha1Project, V1alpha1Project> projectSpecBindingFieldFilter() {
    return (oldObj, newObj) -> !ProjectUtils.getSpecBinding(oldObj)
        .equals(ProjectUtils.getSpecBinding(newObj));
  }

  public BiPredicate<V1alpha1Project, V1alpha1Project> projectStatusFieldFilter() {
    return (oldObj, newObj) -> !Objects.equals(oldObj.getStatus(), newObj.getStatus());
  }

  public BiPredicate<V1alpha1Project, V1alpha1Project> projectStatusAllBoundAipubUsersFieldFilter() {
    return (oldObj, newObj) -> !ProjectUtils.getStatusAllBoundAipubUsers(oldObj)
        .equals(ProjectUtils.getStatusAllBoundAipubUsers(newObj));
  }

  public BiPredicate<V1alpha1Project, V1alpha1Project> projectStatusAllBoundImageHubsFieldFilter() {
    return (oldObj, newObj) -> !ProjectUtils.getStatusAllBoundImageHubs(oldObj)
        .equals(ProjectUtils.getStatusAllBoundImageHubs(newObj));
  }

  public BiPredicate<V1alpha1AipubUser, V1alpha1AipubUser> aipubUserSpecFieldFilter() {
    return (oldObj, newObj) -> !Objects.equals(oldObj.getSpec(), newObj.getSpec());
  }

  public BiPredicate<V1alpha1AipubUser, V1alpha1AipubUser> aipubUserStatusBoundProjectsFieldFilter() {
    return (oldObj, newObj) -> !AipubUserUtils.getAllBoundProjects(oldObj)
        .equals(AipubUserUtils.getAllBoundProjects(newObj));
  }

  public BiPredicate<V1alpha1NodeGroup, V1alpha1NodeGroup> nodeGroupSpecFieldFilter() {
    return (oldObj, newObj) -> !Objects.equals(oldObj.getSpec(), newObj.getSpec());
  }

  public BiPredicate<V1alpha1ResourceSet, V1alpha1ResourceSet> resourceSetSpecFieldFilter() {
    return (oldObj, newObj) -> !Objects.equals(oldObj.getSpec(), newObj.getSpec());
  }

  public BiPredicate<V1alpha1ImageHub, V1alpha1ImageHub> imageHubSpecFieldFilter() {
    return (oldObj, newObj) -> !Objects.equals(oldObj.getSpec(), newObj.getSpec());
  }

  public BiPredicate<V1Node, V1Node> nodeFilter() {
    return (oldObj, newObj) ->
        !K8sObjectUtils.getLabels(oldObj).equals(K8sObjectUtils.getLabels(newObj)) ||
            !K8sObjectUtils.getAnnotations(oldObj).equals(K8sObjectUtils.getAnnotations(newObj)) ||
            !Set.copyOf(NodeUtils.getTaints(oldObj))
                .equals(Set.copyOf(NodeUtils.getTaints(newObj)));
  }

  public BiPredicate<V1ClusterRole, V1ClusterRole> clusterRoleFilter() {
    return (oldObj, newObj) -> !Set.copyOf(K8sObjectUtils.getOwnerReferences(oldObj))
        .equals(Set.copyOf(K8sObjectUtils.getOwnerReferences(newObj))) ||
        !Set.copyOf(RoleUtils.getRules(oldObj)).equals(Set.copyOf(RoleUtils.getRules(newObj))) ||
        !Objects.equals(oldObj.getAggregationRule(), newObj.getAggregationRule());
  }

  public BiPredicate<V1ClusterRoleBinding, V1ClusterRoleBinding> clusterRoleBindingFilter() {
    return (oldObj, newObj) -> !Set.copyOf(K8sObjectUtils.getOwnerReferences(oldObj))
        .equals(Set.copyOf(K8sObjectUtils.getOwnerReferences(newObj))) ||
        !oldObj.getRoleRef().equals(newObj.getRoleRef()) ||
        !Set.copyOf(RoleUtils.getSubjects(oldObj))
            .equals(Set.copyOf(RoleUtils.getSubjects(newObj)));
  }

  public BiPredicate<V1ClusterRole, V1ClusterRole> aipubUserClusterRoleFilter() {
    return (oldObj, newObj) -> {
      String oldName = K8sObjectUtils.getName(oldObj);
      String newName = K8sObjectUtils.getName(newObj);
      AipubUserRoleNameResolver resolver = new AipubUserRoleNameResolver();
      return (resolver.resolveAipubUserName(oldName).isPresent() ||
          resolver.resolveAipubUserName(newName).isPresent()) &&
          (!Set.copyOf(K8sObjectUtils.getOwnerReferences(oldObj))
              .equals(Set.copyOf(K8sObjectUtils.getOwnerReferences(newObj))) ||
              !Set.copyOf(RoleUtils.getRules(oldObj))
                  .equals(Set.copyOf(RoleUtils.getRules(newObj))));
    };
  }

  public BiPredicate<V1ClusterRoleBinding, V1ClusterRoleBinding> aipubUserClusterRoleBindingFilter() {
    return (oldObj, newObj) -> {
      String oldName = K8sObjectUtils.getName(oldObj);
      String newName = K8sObjectUtils.getName(newObj);
      AipubUserRoleNameResolver resolver = new AipubUserRoleNameResolver();
      return (resolver.resolveAipubUserName(oldName).isPresent() ||
          resolver.resolveAipubUserName(newName).isPresent()) &&
          (!Set.copyOf(K8sObjectUtils.getOwnerReferences(oldObj))
              .equals(Set.copyOf(K8sObjectUtils.getOwnerReferences(newObj))));
    };
  }

  public BiPredicate<V1Role, V1Role> roleFilter() {
    return (oldObj, newObj) -> !Set.copyOf(K8sObjectUtils.getOwnerReferences(oldObj))
        .equals(Set.copyOf(K8sObjectUtils.getOwnerReferences(newObj))) ||
        !Set.copyOf(RoleUtils.getRules(oldObj)).equals(Set.copyOf(RoleUtils.getRules(newObj)));
  }

  public BiPredicate<V1Role, V1Role> projectRoleFilter() {
    return (oldObj, newObj) -> {
      String oldName = K8sObjectUtils.getName(oldObj);
      String newName = K8sObjectUtils.getName(newObj);
      RoleNameResolver resolver = new RoleNameResolver();
      return (resolver.resolveProjectName(oldName).isPresent() ||
          resolver.resolveProjectName(newName).isPresent()) &&
          (!Set.copyOf(K8sObjectUtils.getOwnerReferences(oldObj))
              .equals(Set.copyOf(K8sObjectUtils.getOwnerReferences(newObj))) ||
              !Set.copyOf(RoleUtils.getRules(oldObj))
                  .equals(Set.copyOf(RoleUtils.getRules(newObj))));
    };
  }

  public BiPredicate<V1Role, V1Role> aipubUserRoleFilter() {
    return (oldObj, newObj) -> {
      String oldName = K8sObjectUtils.getName(oldObj);
      String newName = K8sObjectUtils.getName(newObj);
      AipubUserRoleNameResolver resolver = new AipubUserRoleNameResolver();
      return (resolver.resolveAipubUserName(oldName).isPresent() ||
          resolver.resolveAipubUserName(newName).isPresent()) &&
          (!Set.copyOf(K8sObjectUtils.getOwnerReferences(oldObj))
              .equals(Set.copyOf(K8sObjectUtils.getOwnerReferences(newObj))) ||
              !Set.copyOf(RoleUtils.getRules(oldObj))
                  .equals(Set.copyOf(RoleUtils.getRules(newObj))));
    };
  }

  public BiPredicate<V1RoleBinding, V1RoleBinding> aipubUserRoleBindingFilter() {
    return (oldObj, newObj) -> {
      String oldName = K8sObjectUtils.getName(oldObj);
      String newName = K8sObjectUtils.getName(newObj);
      AipubUserRoleNameResolver resolver = new AipubUserRoleNameResolver();
      return (resolver.resolveAipubUserName(oldName).isPresent() ||
          resolver.resolveAipubUserName(newName).isPresent()) &&
          (!oldObj.getRoleRef().equals(newObj.getRoleRef()) ||
              !Set.copyOf(RoleUtils.getSubjects(oldObj))
                  .equals(Set.copyOf(RoleUtils.getSubjects(newObj))));
    };
  }

  public BiPredicate<V1RoleBinding, V1RoleBinding> roleBindingFilter() {
    return (oldObj, newObj) -> !Set.copyOf(K8sObjectUtils.getOwnerReferences(oldObj))
        .equals(Set.copyOf(K8sObjectUtils.getOwnerReferences(newObj))) ||
        !oldObj.getRoleRef().equals(newObj.getRoleRef()) ||
        !Set.copyOf(RoleUtils.getSubjects(oldObj))
            .equals(Set.copyOf(RoleUtils.getSubjects(newObj)));
  }

  public BiPredicate<V1ResourceQuota, V1ResourceQuota> resourceQuotaFilter() {
    return (oldObj, newObj) -> !Set.copyOf(K8sObjectUtils.getOwnerReferences(oldObj))
        .equals(Set.copyOf(K8sObjectUtils.getOwnerReferences(newObj))) ||
        !Objects.equals(oldObj.getSpec(), newObj.getSpec());
  }

  public BiPredicate<V1Secret, V1Secret> imageRegistrySecretFilter() {
    return (oldObj, newObj) -> !Set.copyOf(K8sObjectUtils.getOwnerReferences(oldObj))
        .equals(Set.copyOf(K8sObjectUtils.getOwnerReferences(newObj))) ||
        !Objects.equals(oldObj.getType(), newObj.getType()) ||
        !Objects.equals(oldObj.getData(), newObj.getData());
  }

  public BiPredicate<V1ResourceQuota, V1ResourceQuota> resourceQuotaStatusFieldFilter() {
    return (oldObj, newObj) -> !Objects.equals(oldObj.getStatus(), newObj.getStatus());
  }

  public BiPredicate<V1Pod, V1Pod> podNodeNameFieldFilter() {
    return (oldObj, newObj) -> !Set.copyOf(K8sObjectUtils.getOwnerReferences(oldObj))
        .equals(Set.copyOf(K8sObjectUtils.getOwnerReferences(newObj))) ||
        !WorkloadUtils.getNodeName(oldObj).equals(WorkloadUtils.getNodeName(newObj));
  }

  public BiPredicate<V1alpha1Operation, V1alpha1Operation> operationSpecFieldFilter() {
    return (oldObj, newObj) -> !Objects.equals(oldObj.getMetadata().getLabels(),
        newObj.getMetadata().getLabels());
  }

  public BiPredicate<V1CronJob, V1CronJob> cronJobFilter() {
    return (oldObj, newObj) ->
        !K8sObjectUtils.getOwnerReferences(oldObj).equals(K8sObjectUtils.getOwnerReferences(newObj))
            ||
            !WorkloadUtils.getPodTemplateSpec(oldObj)
                .equals(WorkloadUtils.getPodTemplateSpec(newObj));
  }

  public BiPredicate<V1DaemonSet, V1DaemonSet> daemonSetFilter() {
    return (oldObj, newObj) ->
        !K8sObjectUtils.getOwnerReferences(oldObj).equals(K8sObjectUtils.getOwnerReferences(newObj))
            ||
            !WorkloadUtils.getPodTemplateSpec(oldObj)
                .equals(WorkloadUtils.getPodTemplateSpec(newObj));
  }

  public BiPredicate<V1Deployment, V1Deployment> deploymentFilter() {
    return (oldObj, newObj) ->
        !K8sObjectUtils.getOwnerReferences(oldObj).equals(K8sObjectUtils.getOwnerReferences(newObj))
            ||
            !WorkloadUtils.getPodTemplateSpec(oldObj)
                .equals(WorkloadUtils.getPodTemplateSpec(newObj));
  }

  public BiPredicate<V1Job, V1Job> jobFilter() {
    return (oldObj, newObj) ->
        !K8sObjectUtils.getOwnerReferences(oldObj).equals(K8sObjectUtils.getOwnerReferences(newObj))
            ||
            !WorkloadUtils.getPodTemplateSpec(oldObj)
                .equals(WorkloadUtils.getPodTemplateSpec(newObj));
  }

  public BiPredicate<V1ReplicaSet, V1ReplicaSet> replicaSetFilter() {
    return (oldObj, newObj) ->
        !K8sObjectUtils.getOwnerReferences(oldObj).equals(K8sObjectUtils.getOwnerReferences(newObj))
            ||
            !WorkloadUtils.getPodTemplateSpec(oldObj)
                .equals(WorkloadUtils.getPodTemplateSpec(newObj));
  }

  public BiPredicate<V1StatefulSet, V1StatefulSet> statefulSetFilter() {
    return (oldObj, newObj) ->
        !K8sObjectUtils.getOwnerReferences(oldObj).equals(K8sObjectUtils.getOwnerReferences(newObj))
            ||
            !WorkloadUtils.getPodTemplateSpec(oldObj)
                .equals(WorkloadUtils.getPodTemplateSpec(newObj));
  }

  public BiPredicate<V1Workspace, V1Workspace> workspaceFilter() {
    return (oldObj, newObj) ->
        !K8sObjectUtils.getOwnerReferences(oldObj).equals(K8sObjectUtils.getOwnerReferences(newObj))
            ||
            !WorkloadUtils.getPodTemplateSpec(oldObj)
                .equals(WorkloadUtils.getPodTemplateSpec(newObj));
  }

  public BiPredicate<V1alpha1ChainJob, V1alpha1ChainJob> chainJobFilter() {
    return (oldObj, newObj) ->
        !K8sObjectUtils.getOwnerReferences(oldObj)
            .equals(K8sObjectUtils.getOwnerReferences(newObj));
  }

  public BiPredicate<V1alpha1Operation, V1alpha1Operation> operationFilter() {
    return (oldObj, newObj) ->
        !K8sObjectUtils.getOwnerReferences(oldObj).equals(K8sObjectUtils.getOwnerReferences(newObj))
            ||
            !WorkloadUtils.getPodTemplateSpec(oldObj)
                .equals(WorkloadUtils.getPodTemplateSpec(newObj));
  }

  public BiPredicate<V1alpha1AipubVolume, V1alpha1AipubVolume> aipubVolumeFilter() {
    return (oldObj, newObj) ->
        !K8sObjectUtils.getOwnerReferences(oldObj)
            .equals(K8sObjectUtils.getOwnerReferences(newObj));
  }

  public BiPredicate<V1alpha1SftpServer, V1alpha1SftpServer> sftpServerFilter() {
    return (oldObj, newObj) ->
        !K8sObjectUtils.getOwnerReferences(oldObj).equals(K8sObjectUtils.getOwnerReferences(newObj))
            ||
            !WorkloadUtils.getPodTemplateSpec(oldObj)
                .equals(WorkloadUtils.getPodTemplateSpec(newObj));
  }

}
