package io.ten1010.aipub.projectcontroller.controller.watch;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.*;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.*;

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

    public <T extends KubernetesObject> BiPredicate<T, T> ownerReferencesFilter() {
        return (oldObj, newObj) -> !K8sObjectUtils.getOwnerReferences(oldObj).equals(K8sObjectUtils.getOwnerReferences(newObj));
    }

    public BiPredicate<V1alpha1Project, V1alpha1Project> projectSpecFieldFilter() {
        return (oldObj, newObj) -> !Objects.equals(oldObj.getSpec(), newObj.getSpec());
    }

    public BiPredicate<V1alpha1Project, V1alpha1Project> projectSpecQuotaFieldFilter() {
        return (oldObj, newObj) -> !ProjectUtils.getSpecQuota(oldObj).equals(ProjectUtils.getSpecQuota(newObj));
    }

    public BiPredicate<V1alpha1Project, V1alpha1Project> projectSpecBindingImageNamespacesFieldFilter() {
        return (oldObj, newObj) -> !ProjectUtils.getSpecBindingImageNamespaces(oldObj).equals(ProjectUtils.getSpecBindingImageNamespaces(newObj));
    }

    public BiPredicate<V1alpha1Project, V1alpha1Project> projectSpecBindingFieldFilter() {
        return (oldObj, newObj) -> !ProjectUtils.getSpecBinding(oldObj).equals(ProjectUtils.getSpecBinding(newObj));
    }

    public BiPredicate<V1alpha1Project, V1alpha1Project> projectStatusAllBoundAipubUsersFieldFilter() {
        return (oldObj, newObj) -> !ProjectUtils.getStatusAllBoundAipubUsers(oldObj).equals(ProjectUtils.getStatusAllBoundAipubUsers(newObj));
    }

    public BiPredicate<V1alpha1Project, V1alpha1Project> projectStatusAllBoundImageNamespacesFieldFilter() {
        return (oldObj, newObj) -> !ProjectUtils.getStatusAllBoundImageNamespaces(oldObj).equals(ProjectUtils.getStatusAllBoundImageNamespaces(newObj));
    }

    public BiPredicate<V1alpha1AipubUser, V1alpha1AipubUser> aipubUserSpecFieldFilter() {
        return (oldObj, newObj) -> !Objects.equals(oldObj.getSpec(), newObj.getSpec());
    }

    public BiPredicate<V1alpha1NodeGroup, V1alpha1NodeGroup> nodeGroupSpecFieldFilter() {
        return (oldObj, newObj) -> !Objects.equals(oldObj.getSpec(), newObj.getSpec());
    }

    public BiPredicate<V1alpha1ResourceSet, V1alpha1ResourceSet> resourceSetSpecFieldFilter() {
        return (oldObj, newObj) -> !Objects.equals(oldObj.getSpec(), newObj.getSpec());
    }

    public BiPredicate<V1alpha1ImageNamespace, V1alpha1ImageNamespace> imageNamespaceSpecFieldFilter() {
        return (oldObj, newObj) -> !Objects.equals(oldObj.getSpec(), newObj.getSpec());
    }

    public BiPredicate<V1Node, V1Node> nodeFilter() {
        return (oldObj, newObj) -> !K8sObjectUtils.getLabels(oldObj).equals(K8sObjectUtils.getLabels(newObj)) ||
                !K8sObjectUtils.getAnnotations(oldObj).equals(K8sObjectUtils.getAnnotations(newObj)) ||
                !Set.copyOf(NodeUtils.getTaints(oldObj)).equals(Set.copyOf(NodeUtils.getTaints(newObj)));
    }

    public BiPredicate<V1ClusterRole, V1ClusterRole> clusterRoleFilter() {
        return (oldObj, newObj) -> !Set.copyOf(K8sObjectUtils.getOwnerReferences(oldObj)).equals(Set.copyOf(K8sObjectUtils.getOwnerReferences(newObj))) ||
                !Set.copyOf(RoleUtils.getRules(oldObj)).equals(Set.copyOf(RoleUtils.getRules(newObj))) ||
                !Objects.equals(oldObj.getAggregationRule(), newObj.getAggregationRule());
    }

    public BiPredicate<V1ClusterRoleBinding, V1ClusterRoleBinding> clusterRoleBindingFilter() {
        return (oldObj, newObj) -> !Set.copyOf(K8sObjectUtils.getOwnerReferences(oldObj)).equals(Set.copyOf(K8sObjectUtils.getOwnerReferences(newObj))) ||
                !oldObj.getRoleRef().equals(newObj.getRoleRef()) ||
                !Set.copyOf(RoleUtils.getSubjects(oldObj)).equals(Set.copyOf(RoleUtils.getSubjects(newObj)));
    }

    public BiPredicate<V1Role, V1Role> roleFilter() {
        return (oldObj, newObj) -> !Set.copyOf(K8sObjectUtils.getOwnerReferences(oldObj)).equals(Set.copyOf(K8sObjectUtils.getOwnerReferences(newObj))) ||
                !Set.copyOf(RoleUtils.getRules(oldObj)).equals(Set.copyOf(RoleUtils.getRules(newObj)));
    }

    public BiPredicate<V1RoleBinding, V1RoleBinding> roleBindingFilter() {
        return (oldObj, newObj) -> !Set.copyOf(K8sObjectUtils.getOwnerReferences(oldObj)).equals(Set.copyOf(K8sObjectUtils.getOwnerReferences(newObj))) ||
                !oldObj.getRoleRef().equals(newObj.getRoleRef()) ||
                !Set.copyOf(RoleUtils.getSubjects(oldObj)).equals(Set.copyOf(RoleUtils.getSubjects(newObj)));
    }

    public BiPredicate<V1ResourceQuota, V1ResourceQuota> resourceQuotaFilter() {
        return (oldObj, newObj) -> !Set.copyOf(K8sObjectUtils.getOwnerReferences(oldObj)).equals(Set.copyOf(K8sObjectUtils.getOwnerReferences(newObj))) ||
                !Objects.equals(oldObj.getSpec(), newObj.getSpec());
    }

    public BiPredicate<V1Secret, V1Secret> imagePullSecretFilter() {
        return (oldObj, newObj) -> !Set.copyOf(K8sObjectUtils.getOwnerReferences(oldObj)).equals(Set.copyOf(K8sObjectUtils.getOwnerReferences(newObj))) ||
                !Objects.equals(oldObj.getType(), newObj.getType()) ||
                !Objects.equals(oldObj.getData(), newObj.getData());
    }

    public BiPredicate<V1ResourceQuota, V1ResourceQuota> resourceQuotaStatusFieldFilter() {
        return (oldObj, newObj) -> !Objects.equals(oldObj.getStatus(), newObj.getStatus());
    }

    public BiPredicate<V1Pod, V1Pod> podNodeNameFieldFilter() {
        return (oldObj, newObj) -> !Set.copyOf(K8sObjectUtils.getOwnerReferences(oldObj)).equals(Set.copyOf(K8sObjectUtils.getOwnerReferences(newObj))) ||
                !WorkloadUtils.getNodeName(oldObj).equals(WorkloadUtils.getNodeName(newObj));
    }

    public BiPredicate<V1CronJob, V1CronJob> cronJobFilter() {
        return (oldObj, newObj) -> !K8sObjectUtils.getOwnerReferences(oldObj).equals(K8sObjectUtils.getOwnerReferences(newObj)) ||
                !WorkloadUtils.getPodTemplateSpec(oldObj).equals(WorkloadUtils.getPodTemplateSpec(newObj));
    }

    public BiPredicate<V1DaemonSet, V1DaemonSet> daemonSetFilter() {
        return (oldObj, newObj) -> !K8sObjectUtils.getOwnerReferences(oldObj).equals(K8sObjectUtils.getOwnerReferences(newObj)) ||
                !WorkloadUtils.getPodTemplateSpec(oldObj).equals(WorkloadUtils.getPodTemplateSpec(newObj));
    }

    public BiPredicate<V1Deployment, V1Deployment> deploymentFilter() {
        return (oldObj, newObj) -> !K8sObjectUtils.getOwnerReferences(oldObj).equals(K8sObjectUtils.getOwnerReferences(newObj)) ||
                !WorkloadUtils.getPodTemplateSpec(oldObj).equals(WorkloadUtils.getPodTemplateSpec(newObj));
    }

    public BiPredicate<V1Job, V1Job> jobFilter() {
        return (oldObj, newObj) -> !K8sObjectUtils.getOwnerReferences(oldObj).equals(K8sObjectUtils.getOwnerReferences(newObj)) ||
                !WorkloadUtils.getPodTemplateSpec(oldObj).equals(WorkloadUtils.getPodTemplateSpec(newObj));
    }

    public BiPredicate<V1ReplicaSet, V1ReplicaSet> replicaSetFilter() {
        return (oldObj, newObj) -> !K8sObjectUtils.getOwnerReferences(oldObj).equals(K8sObjectUtils.getOwnerReferences(newObj)) ||
                !WorkloadUtils.getPodTemplateSpec(oldObj).equals(WorkloadUtils.getPodTemplateSpec(newObj));
    }

    public BiPredicate<V1StatefulSet, V1StatefulSet> statefulSetFilter() {
        return (oldObj, newObj) -> !K8sObjectUtils.getOwnerReferences(oldObj).equals(K8sObjectUtils.getOwnerReferences(newObj)) ||
                !WorkloadUtils.getPodTemplateSpec(oldObj).equals(WorkloadUtils.getPodTemplateSpec(newObj));
    }

}
