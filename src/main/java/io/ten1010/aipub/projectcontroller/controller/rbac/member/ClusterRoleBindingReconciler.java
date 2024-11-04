package io.ten1010.aipub.projectcontroller.controller.rbac.member;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.*;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.RoleUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ClusterRoleBindingReconciler extends AbstractReconciler {

    private final KeyResolver keyResolver;
    private final RoleNameResolver roleNameResolver;
    private final ReconciliationService reconciliationService;
    private final Indexer<V1ClusterRoleBinding> clusterRoleBindingIndexer;
    private final Indexer<V1alpha1Project> projectIndexer;
    private final RbacAuthorizationV1Api rbacAuthorizationV1Api;

    public ClusterRoleBindingReconciler(
            SharedInformerFactory sharedInformerFactory,
            K8sApiProvider k8sApiProvider,
            ReconciliationService reconciliationService) {
        this.keyResolver = new KeyResolver();
        this.roleNameResolver = new RoleNameResolver();
        this.reconciliationService = reconciliationService;
        this.clusterRoleBindingIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1ClusterRoleBinding.class)
                .getIndexer();
        this.projectIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1Project.class)
                .getIndexer();
        this.rbacAuthorizationV1Api = new RbacAuthorizationV1Api(k8sApiProvider.getApiClient());
    }

    @Override
    protected Result reconcileInternal(Request request) throws ApiException {
        Optional<ProjectNameAndRole> projNameOpt = this.roleNameResolver.resolveProjectName(request.getName());
        if (projNameOpt.isEmpty()) {
            return new Result(false);
        }
        String projName = projNameOpt.get().getProjectName();
        ProjectRoleEnum projRoleEnum = projNameOpt.get().getProjectRoleEnum();

        String roleBindingKey = new RequestHelper(this.keyResolver).resolveKey(request);
        Optional<V1ClusterRoleBinding> roleBindingOpt = Optional.ofNullable(this.clusterRoleBindingIndexer.getByKey(roleBindingKey));
        String projKey = this.keyResolver.resolveKey(projName);
        Optional<V1alpha1Project> projectOpt = Optional.ofNullable(this.projectIndexer.getByKey(projKey));

        if (projectOpt.isEmpty()) {
            if (roleBindingOpt.isPresent()) {
                V1ClusterRoleBinding roleBinding = roleBindingOpt.get();
                deleteRoleBinding(K8sObjectUtils.getName(roleBinding));
                return new Result(false);
            }
            return new Result(false);
        }

        List<V1OwnerReference> reconciledReferences = this.reconciliationService.reconcileOwnerReferences(roleBindingOpt.orElse(null), projectOpt.get());
        V1RoleRef reconciledRoleRef = this.reconciliationService.reconcileClusterRoleRef(projectOpt.get(), projRoleEnum);
        List<RbacV1Subject> subjects = this.reconciliationService.reconcileSubjects(projectOpt.get(), projRoleEnum);

        if (roleBindingOpt.isPresent()) {
            return reconcileExistingRoleBinding(roleBindingOpt.get(), reconciledReferences, reconciledRoleRef, subjects);
        }

        if (!K8sObjectUtils.isTerminating(projectOpt.get())) {
            return reconcileNoExistingRoleBinding(request.getName(), reconciledReferences, reconciledRoleRef, subjects);
        }

        return new Result(false);
    }

    private Result reconcileNoExistingRoleBinding(
            String objName,
            List<V1OwnerReference> reconciledReferences,
            V1RoleRef reconciledRoleRef,
            List<RbacV1Subject> subjects) throws ApiException {
        V1ClusterRoleBinding roleBinding = new V1ClusterRoleBindingBuilder()
                .withNewMetadata()
                .withName(objName)
                .withOwnerReferences(reconciledReferences)
                .endMetadata()
                .withRoleRef(reconciledRoleRef)
                .withSubjects(subjects)
                .build();
        createRoleBinding(roleBinding);
        return new Result(false);
    }

    private Result reconcileExistingRoleBinding(
            V1ClusterRoleBinding clusterRoleBinding,
            List<V1OwnerReference> reconciledReferences,
            V1RoleRef reconciledRoleRef,
            List<RbacV1Subject> reconciledSubjects) throws ApiException {
        if (Set.copyOf(K8sObjectUtils.getOwnerReferences(clusterRoleBinding)).equals(Set.copyOf(reconciledReferences)) &&
                clusterRoleBinding.getRoleRef().equals(reconciledRoleRef) &&
                Set.copyOf(RoleUtils.getSubjects(clusterRoleBinding)).equals(Set.copyOf(reconciledSubjects))) {
            return new Result(false);
        }
        V1ClusterRoleBinding edited = new V1ClusterRoleBindingBuilder(clusterRoleBinding)
                .editMetadata()
                .withOwnerReferences(reconciledReferences)
                .endMetadata()
                .withRoleRef(reconciledRoleRef)
                .withSubjects(reconciledSubjects)
                .build();
        updateRoleBinding(K8sObjectUtils.getName(clusterRoleBinding), edited);
        return new Result(false);
    }

    private void createRoleBinding(V1ClusterRoleBinding roleBinding) throws ApiException {
        this.rbacAuthorizationV1Api
                .createClusterRoleBinding(roleBinding)
                .execute();
    }

    private void updateRoleBinding(String objName, V1ClusterRoleBinding roleBinding) throws ApiException {
        this.rbacAuthorizationV1Api
                .replaceClusterRoleBinding(objName, roleBinding)
                .execute();
    }

    private void deleteRoleBinding(String objName) throws ApiException {
        this.rbacAuthorizationV1Api
                .deleteClusterRoleBinding(objName)
                .execute();
    }

}
