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

public class RoleBindingReconciler extends AbstractReconciler {

    private final KeyResolver keyResolver;
    private final NamespaceNameResolver namespaceNameResolver;
    private final RoleNameResolver roleNameResolver;
    private final ReconciliationService reconciliationService;
    private final Indexer<V1RoleBinding> RoleBindingIndexer;
    private final Indexer<V1alpha1Project> projectIndexer;
    private final RbacAuthorizationV1Api rbacAuthorizationV1Api;

    public RoleBindingReconciler(
            SharedInformerFactory sharedInformerFactory,
            K8sApiProvider k8sApiProvider,
            ReconciliationService reconciliationService) {
        this.keyResolver = new KeyResolver();
        this.namespaceNameResolver = new NamespaceNameResolver();
        this.roleNameResolver = new RoleNameResolver();
        this.reconciliationService = reconciliationService;
        this.RoleBindingIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1RoleBinding.class)
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
        Optional<V1RoleBinding> roleBindingOpt = Optional.ofNullable(this.RoleBindingIndexer.getByKey(roleBindingKey));
        String projKey = this.keyResolver.resolveKey(projName);
        Optional<V1alpha1Project> projectOpt = Optional.ofNullable(this.projectIndexer.getByKey(projKey));

        if (projectOpt.isEmpty()) {
            if (roleBindingOpt.isPresent()) {
                V1RoleBinding roleBinding = roleBindingOpt.get();
                deleteRoleBinding(roleBinding);
                return new Result(false);
            }
            return new Result(false);
        }

        List<V1OwnerReference> reconciledReferences = this.reconciliationService.reconcileOwnerReferences(roleBindingOpt.orElse(null), projectOpt.get());
        V1RoleRef reconciledRoleRef = this.reconciliationService.reconcileRoleRef(projectOpt.get(), projRoleEnum);
        List<RbacV1Subject> subjects = this.reconciliationService.reconcileSubjects(projectOpt.get(), projRoleEnum);

        if (roleBindingOpt.isPresent()) {
            String projNameFromRoleName = K8sObjectUtils.getName(projectOpt.get());
            String roleBindingNamespace = K8sObjectUtils.getNamespace(roleBindingOpt.get());
            String projNameFromNamespace = this.namespaceNameResolver.resolveProjectName(roleBindingNamespace);
            if (!projNameFromRoleName.equals(projNameFromNamespace)) {
                deleteRoleBinding(roleBindingOpt.get());
                return new Result(false);
            }
            return reconcileExistingRoleBinding(roleBindingOpt.get(), reconciledReferences, reconciledRoleRef, subjects);
        }

        if (!K8sObjectUtils.isTerminating(projectOpt.get())) {
            return reconcileNoExistingRoleBinding(request.getNamespace(), request.getName(), reconciledReferences, reconciledRoleRef, subjects);
        }

        return new Result(false);
    }

    private Result reconcileNoExistingRoleBinding(
            String namespace,
            String objName,
            List<V1OwnerReference> reconciledReferences,
            V1RoleRef reconciledRoleRef,
            List<RbacV1Subject> subjects) throws ApiException {
        V1RoleBinding roleBinding = new V1RoleBindingBuilder()
                .withNewMetadata()
                .withName(objName)
                .withOwnerReferences(reconciledReferences)
                .endMetadata()
                .withRoleRef(reconciledRoleRef)
                .withSubjects(subjects)
                .build();
        createRoleBinding(namespace, roleBinding);
        return new Result(false);
    }

    private Result reconcileExistingRoleBinding(
            V1RoleBinding roleBinding,
            List<V1OwnerReference> reconciledReferences,
            V1RoleRef reconciledRoleRef,
            List<RbacV1Subject> reconciledSubjects) throws ApiException {
        if (Set.copyOf(K8sObjectUtils.getOwnerReferences(roleBinding)).equals(Set.copyOf(reconciledReferences)) &&
                roleBinding.getRoleRef().equals(reconciledRoleRef) &&
                Set.copyOf(RoleUtils.getSubjects(roleBinding)).equals(Set.copyOf(reconciledSubjects))) {
            return new Result(false);
        }
        V1RoleBinding edited = new V1RoleBindingBuilder(roleBinding)
                .editMetadata()
                .withOwnerReferences(reconciledReferences)
                .endMetadata()
                .withRoleRef(reconciledRoleRef)
                .withSubjects(reconciledSubjects)
                .build();
        updateRoleBinding(K8sObjectUtils.getNamespace(roleBinding), K8sObjectUtils.getName(roleBinding), edited);
        return new Result(false);
    }

    private void createRoleBinding(String namespace, V1RoleBinding roleBinding) throws ApiException {
        this.rbacAuthorizationV1Api
                .createNamespacedRoleBinding(namespace, roleBinding)
                .execute();
    }

    private void updateRoleBinding(String namespace, String objName, V1RoleBinding roleBinding) throws ApiException {
        this.rbacAuthorizationV1Api
                .replaceNamespacedRoleBinding(objName, namespace, roleBinding)
                .execute();
    }

    private void deleteRoleBinding(String namespace, String objName) throws ApiException {
        this.rbacAuthorizationV1Api
                .deleteNamespacedRoleBinding(objName, namespace)
                .execute();
    }

    private void deleteRoleBinding(V1RoleBinding roleBinding) throws ApiException {
        deleteRoleBinding(K8sObjectUtils.getNamespace(roleBinding), K8sObjectUtils.getName(roleBinding));
    }

}
