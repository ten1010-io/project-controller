package io.ten1010.aipub.projectcontroller.controller.rbac.aipub;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.*;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.AipubUserRoleNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.RoleUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class AipubUserClusterRoleBindingReconciler extends AbstractReconciler {

    private final KeyResolver keyResolver;
    private final AipubUserRoleNameResolver roleNameResolver;
    private final ReconciliationService reconciliationService;
    private final Indexer<V1ClusterRoleBinding> clusterRoleBindingIndexer;
    private final Indexer<V1alpha1AipubUser> userIndexer;
    private final RbacAuthorizationV1Api rbacAuthorizationV1Api;

    public AipubUserClusterRoleBindingReconciler(
            SharedInformerFactory sharedInformerFactory,
            K8sApiProvider k8sApiProvider,
            ReconciliationService reconciliationService) {
        this.keyResolver = new KeyResolver();
        this.roleNameResolver = new AipubUserRoleNameResolver();
        this.reconciliationService = reconciliationService;
        this.clusterRoleBindingIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1ClusterRoleBinding.class)
                .getIndexer();
        this.userIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1AipubUser.class)
                .getIndexer();
        this.rbacAuthorizationV1Api = new RbacAuthorizationV1Api(k8sApiProvider.getApiClient());
    }

    @Override
    protected Result reconcileInternal(Request request) throws ApiException {
        log.info("AipubUserClusterRoleBindingReconciler reconcile request");
        Optional<String> userNameOpt = this.roleNameResolver.resolveAipubUserName(request.getName());
        if (userNameOpt.isEmpty()) {
            return new Result(false);
        }
        String userName = userNameOpt.get();

        String roleBindingKey = new RequestHelper(this.keyResolver).resolveKey(request);
        Optional<V1ClusterRoleBinding> roleBindingOpt = Optional.ofNullable(this.clusterRoleBindingIndexer.getByKey(roleBindingKey));
        String userKey = this.keyResolver.resolveKey(userName);
        Optional<V1alpha1AipubUser> userOpt = Optional.ofNullable(this.userIndexer.getByKey(userKey));

        if (userOpt.isEmpty()) {
            if (roleBindingOpt.isPresent()) {
                V1ClusterRoleBinding roleBinding = roleBindingOpt.get();
                deleteRoleBinding(K8sObjectUtils.getName(roleBinding));
                return new Result(true, Duration.ofSeconds(5));
            }
            return new Result(false);
        }

        List<V1OwnerReference> reconciledReferences = this.reconciliationService.reconcileOwnerReferences(roleBindingOpt.orElse(null), userOpt.get());
        V1RoleRef reconciledRoleRef = this.reconciliationService.reconcileClusterRoleRef(userOpt.get());
        List<RbacV1Subject> subjects = this.reconciliationService.reconcileSubjects(userOpt.get());

        if (roleBindingOpt.isPresent()) {
            return reconcileExistingRoleBinding(roleBindingOpt.get(), reconciledReferences, reconciledRoleRef, subjects);
        }

        if (!K8sObjectUtils.isTerminating(userOpt.get())) {
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
        return new Result(true, Duration.ofSeconds(5));
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
