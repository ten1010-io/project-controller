package io.ten1010.aipub.projectcontroller.controller.namespaced;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1ResourceQuota;
import io.kubernetes.client.openapi.models.V1ResourceQuotaBuilder;
import io.kubernetes.client.openapi.models.V1ResourceQuotaSpec;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.*;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ResourceQuotaReconciler extends AbstractReconciler {

    private final KeyResolver keyResolver;
    private final NamespaceNameResolver namespaceNameResolver;
    private final ResourceQuotaNameResolver quotaNameResolver;
    private final ReconciliationService reconciliationService;
    private final Indexer<V1ResourceQuota> quotaIndexer;
    private final Indexer<V1alpha1Project> projectIndexer;
    private final CoreV1Api coreV1Api;

    public ResourceQuotaReconciler(
            SharedInformerFactory sharedInformerFactory,
            K8sApiProvider k8sApiProvider,
            ReconciliationService reconciliationService) {
        this.keyResolver = new KeyResolver();
        this.namespaceNameResolver = new NamespaceNameResolver();
        this.quotaNameResolver = new ResourceQuotaNameResolver();
        this.reconciliationService = reconciliationService;
        this.quotaIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1ResourceQuota.class)
                .getIndexer();
        this.projectIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1Project.class)
                .getIndexer();
        this.coreV1Api = new CoreV1Api(k8sApiProvider.getApiClient());
    }

    @Override
    protected Result reconcileInternal(Request request) throws ApiException {
        Optional<String> projNameOpt = this.quotaNameResolver.resolveProjectName(request.getName());
        if (projNameOpt.isEmpty()) {
            return new Result(false);
        }
        String projName = projNameOpt.get();

        String quotaKey = new RequestHelper(this.keyResolver).resolveKey(request);
        Optional<V1ResourceQuota> quotaOpt = Optional.ofNullable(this.quotaIndexer.getByKey(quotaKey));
        String projKey = this.keyResolver.resolveKey(projName);
        Optional<V1alpha1Project> projectOpt = Optional.ofNullable(this.projectIndexer.getByKey(projKey));

        if (projectOpt.isEmpty()) {
            if (quotaOpt.isPresent()) {
                deleteQuota(quotaOpt.get());
                return new Result(false);
            }
            return new Result(false);
        }

        List<V1OwnerReference> reconciledReferences = this.reconciliationService.reconcileOwnerReferences(quotaOpt.orElse(null), projectOpt.get());
        V1ResourceQuotaSpec reconciledQuotaSpec = this.reconciliationService.reconcileQuotaSpec(projectOpt.get());

        if (quotaOpt.isPresent()) {
            String projNameFromQuotaName = K8sObjectUtils.getName(projectOpt.get());
            String quotaNamespace = K8sObjectUtils.getNamespace(quotaOpt.get());
            String projNameFromNamespace = this.namespaceNameResolver.resolveProjectName(quotaNamespace);
            if (!projNameFromQuotaName.equals(projNameFromNamespace)) {
                deleteQuota(quotaOpt.get());
                return new Result(false);
            }
            return reconcileExistingQuota(quotaOpt.get(), reconciledReferences, reconciledQuotaSpec);
        }

        if (!K8sObjectUtils.isTerminating(projectOpt.get())) {
            return reconcileNoExistingQuota(request.getNamespace(), request.getName(), reconciledReferences, reconciledQuotaSpec);
        }

        return new Result(false);
    }

    private Result reconcileNoExistingQuota(
            String namespace,
            String objName,
            List<V1OwnerReference> reconciledReferences,
            V1ResourceQuotaSpec reconciledQuotaSpec) throws ApiException {
        V1ResourceQuota quota = new V1ResourceQuotaBuilder()
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(objName)
                .withOwnerReferences(reconciledReferences)
                .endMetadata()
                .withSpec(reconciledQuotaSpec)
                .build();
        createQuota(namespace, quota);

        return new Result(false);
    }

    private Result reconcileExistingQuota(
            V1ResourceQuota quota,
            List<V1OwnerReference> reconciledReferences,
            V1ResourceQuotaSpec reconciledQuotaSpec) throws ApiException {
        if (Set.copyOf(K8sObjectUtils.getOwnerReferences(quota)).equals(Set.copyOf(reconciledReferences)) &&
                Objects.equals(quota.getSpec(), reconciledQuotaSpec)) {
            return new Result(false);
        }
        V1ResourceQuota edited = new V1ResourceQuotaBuilder(quota)
                .editMetadata()
                .withOwnerReferences(reconciledReferences)
                .endMetadata()
                .withSpec(reconciledQuotaSpec)
                .build();
        updateQuota(K8sObjectUtils.getNamespace(quota), K8sObjectUtils.getName(quota), edited);

        return new Result(false);
    }

    private void createQuota(String namespace, V1ResourceQuota quota) throws ApiException {
        this.coreV1Api
                .createNamespacedResourceQuota(namespace, quota)
                .execute();
    }

    private void updateQuota(String namespace, String objName, V1ResourceQuota quota) throws ApiException {
        this.coreV1Api
                .replaceNamespacedResourceQuota(objName, namespace, quota)
                .execute();
    }

    private void deleteQuota(String namespace, String objName) throws ApiException {
        this.coreV1Api
                .deleteNamespacedResourceQuota(objName, namespace)
                .execute();
    }

    private void deleteQuota(V1ResourceQuota object) throws ApiException {
        deleteQuota(K8sObjectUtils.getNamespace(object), K8sObjectUtils.getName(object));
    }

}
