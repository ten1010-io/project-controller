package io.ten1010.aipub.projectcontroller.controller.rbac.member;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1PolicyRule;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBuilder;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.*;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.RoleUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RoleReconciler extends AbstractReconciler {

    private final KeyResolver keyResolver;
    private final NamespaceNameResolver namespaceNameResolver;
    private final RoleNameResolver roleNameResolver;
    private final ReconciliationService reconciliationService;
    private final Indexer<V1Role> roleIndexer;
    private final Indexer<V1alpha1Project> projectIndexer;
    private final RbacAuthorizationV1Api rbacAuthorizationV1Api;

    public RoleReconciler(
            SharedInformerFactory sharedInformerFactory,
            K8sApiProvider k8sApiProvider,
            ReconciliationService reconciliationService) {
        this.keyResolver = new KeyResolver();
        this.namespaceNameResolver = new NamespaceNameResolver();
        this.roleNameResolver = new RoleNameResolver();
        this.reconciliationService = reconciliationService;
        this.roleIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Role.class)
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

        String roleKey = new RequestHelper(this.keyResolver).resolveKey(request);
        Optional<V1Role> roleOpt = Optional.ofNullable(this.roleIndexer.getByKey(roleKey));
        String projKey = this.keyResolver.resolveKey(projName);
        Optional<V1alpha1Project> projectOpt = Optional.ofNullable(this.projectIndexer.getByKey(projKey));

        if (projectOpt.isEmpty()) {
            if (roleOpt.isPresent()) {
                deleteRole(roleOpt.get());
                return new Result(false);
            }
            return new Result(false);
        }

        List<V1OwnerReference> reconciledReferences = this.reconciliationService.reconcileOwnerReferences(roleOpt.orElse(null), projectOpt.get());
        List<V1PolicyRule> reconciledRules = this.reconciliationService.reconcileRoleRules(projectOpt.get(), projRoleEnum);

        if (roleOpt.isPresent()) {
            String projNameFromRoleName = K8sObjectUtils.getName(projectOpt.get());
            String roleNamespace = K8sObjectUtils.getNamespace(roleOpt.get());
            String projNameFromNamespace = this.namespaceNameResolver.resolveProjectName(roleNamespace);
            if (!projNameFromRoleName.equals(projNameFromNamespace)) {
                deleteRole(roleOpt.get());
                return new Result(false);
            }
            return reconcileExistingRole(roleOpt.get(), reconciledReferences, reconciledRules);
        }

        if (!K8sObjectUtils.isTerminating(projectOpt.get())) {
            return reconcileNoExistingRole(request.getNamespace(), request.getName(), reconciledReferences, reconciledRules);
        }

        return new Result(false);
    }

    private Result reconcileNoExistingRole(
            String namespace,
            String objName,
            List<V1OwnerReference> reconciledReferences,
            List<V1PolicyRule> reconciledRules) throws ApiException {
        V1Role role = new V1RoleBuilder()
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(objName)
                .withOwnerReferences(reconciledReferences)
                .endMetadata()
                .withRules(reconciledRules)
                .build();
        createRole(namespace, role);
        return new Result(false);
    }

    private Result reconcileExistingRole(
            V1Role role,
            List<V1OwnerReference> reconciledReferences,
            List<V1PolicyRule> reconciledRules) throws ApiException {
        if (Set.copyOf(K8sObjectUtils.getOwnerReferences(role)).equals(Set.copyOf(reconciledReferences)) &&
                RoleUtils.getRules(role).equals(reconciledRules)) {
            return new Result(false);
        }
        V1Role edited = new V1RoleBuilder(role)
                .editMetadata()
                .withOwnerReferences(reconciledReferences)
                .endMetadata()
                .withRules(reconciledRules)
                .build();
        updateRole(K8sObjectUtils.getNamespace(role), K8sObjectUtils.getName(role), edited);
        return new Result(false);
    }

    private void createRole(String namespace, V1Role role) throws ApiException {
        this.rbacAuthorizationV1Api
                .createNamespacedRole(namespace, role)
                .execute();
    }

    private void updateRole(String namespace, String objName, V1Role role) throws ApiException {
        this.rbacAuthorizationV1Api
                .replaceNamespacedRole(objName, namespace, role)
                .execute();
    }

    private void deleteRole(String namespace, String objName) throws ApiException {
        this.rbacAuthorizationV1Api
                .deleteNamespacedRole(objName, namespace)
                .execute();
    }

    private void deleteRole(V1Role object) throws ApiException {
        deleteRole(K8sObjectUtils.getNamespace(object), K8sObjectUtils.getName(object));
    }

}
