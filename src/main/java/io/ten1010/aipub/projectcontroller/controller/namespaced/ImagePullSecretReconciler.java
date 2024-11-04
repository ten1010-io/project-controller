package io.ten1010.aipub.projectcontroller.controller.namespaced;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.*;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;

import java.util.*;

public class ImagePullSecretReconciler extends AbstractReconciler {

    private final KeyResolver keyResolver;
    private final NamespaceNameResolver namespaceNameResolver;
    private final ImagePullSecretNameResolver secretNameResolver;
    private final ReconciliationService reconciliationService;
    private final Indexer<V1Secret> secretIndexer;
    private final Indexer<V1alpha1Project> projectIndexer;
    private final CoreV1Api coreV1Api;

    public ImagePullSecretReconciler(
            SharedInformerFactory sharedInformerFactory,
            K8sApiProvider k8sApiProvider,
            ReconciliationService reconciliationService) {
        this.keyResolver = new KeyResolver();
        this.namespaceNameResolver = new NamespaceNameResolver();
        this.secretNameResolver = new ImagePullSecretNameResolver();
        this.reconciliationService = reconciliationService;
        this.secretIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1Secret.class)
                .getIndexer();
        this.projectIndexer = sharedInformerFactory
                .getExistingSharedIndexInformer(V1alpha1Project.class)
                .getIndexer();
        this.coreV1Api = new CoreV1Api(k8sApiProvider.getApiClient());
    }

    @Override
    protected Result reconcileInternal(Request request) throws ApiException {
        Optional<String> projNameOpt = this.secretNameResolver.resolveProjectName(request.getName());
        if (projNameOpt.isEmpty()) {
            return new Result(false);
        }
        String projName = projNameOpt.get();

        String secretKey = new RequestHelper(this.keyResolver).resolveKey(request);
        Optional<V1Secret> secretOpt = Optional.ofNullable(this.secretIndexer.getByKey(secretKey));
        String projKey = this.keyResolver.resolveKey(projName);
        Optional<V1alpha1Project> projectOpt = Optional.ofNullable(this.projectIndexer.getByKey(projKey));

        if (projectOpt.isEmpty()) {
            if (secretOpt.isPresent()) {
                deleteSecret(secretOpt.get());
                return new Result(false);
            }
            return new Result(false);
        }

        List<V1OwnerReference> reconciledReferences = this.reconciliationService.reconcileOwnerReferences(secretOpt.orElse(null), projectOpt.get());
        String reconciledType = this.reconciliationService.reconcileImagePullSecretType(projectOpt.get());

        if (secretOpt.isPresent()) {
            String projNameFromSecretName = projNameOpt.get();
            String secretNamespace = K8sObjectUtils.getNamespace(secretOpt.get());
            String projNameFromNamespace = this.namespaceNameResolver.resolveProjectName(secretNamespace);
            if (!projNameFromSecretName.equals(projNameFromNamespace)) {
                deleteSecret(secretOpt.get());
                return new Result(false);
            }
            Map<String, byte[]> reconciledData = this.reconciliationService.reconcileImagePullSecretData(projectOpt.get());
            return reconcileExistingSecret(secretOpt.get(), reconciledReferences, reconciledType, reconciledData);
        }

        if (!K8sObjectUtils.isTerminating(projectOpt.get())) {
            Map<String, byte[]> reconciledData = this.reconciliationService.reconcileImagePullSecretData(projectOpt.get());
            return reconcileNoExistingSecret(request.getNamespace(), request.getName(), reconciledReferences, reconciledType, reconciledData);
        }

        return new Result(false);
    }

    private Result reconcileNoExistingSecret(
            String namespace,
            String objName,
            List<V1OwnerReference> reconciledReferences,
            String reconciledType,
            Map<String, byte[]> reconciledData) throws ApiException {
        V1Secret secret = new V1SecretBuilder()
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(objName)
                .withOwnerReferences(reconciledReferences)
                .endMetadata()
                .withType(reconciledType)
                .withData(reconciledData)
                .build();
        createSecret(namespace, secret);

        return new Result(false);
    }

    private Result reconcileExistingSecret(
            V1Secret existing,
            List<V1OwnerReference> reconciledReferences,
            String reconciledType,
            Map<String, byte[]> reconciledData) throws ApiException {
        if (Set.copyOf(K8sObjectUtils.getOwnerReferences(existing)).equals(Set.copyOf(reconciledReferences)) &&
                Objects.equals(existing.getType(), reconciledType) &&
                Objects.equals(existing.getData(), reconciledData)) {
            return new Result(false);
        }
        V1Secret edited = new V1SecretBuilder(existing)
                .editMetadata()
                .withOwnerReferences(reconciledReferences)
                .endMetadata()
                .withType(reconciledType)
                .withData(reconciledData)
                .build();
        updateSecret(K8sObjectUtils.getNamespace(existing), K8sObjectUtils.getName(existing), edited);

        return new Result(false);
    }

    private void createSecret(String namespace, V1Secret secret) throws ApiException {
        this.coreV1Api
                .createNamespacedSecret(namespace, secret)
                .execute();
    }

    private void updateSecret(String namespace, String objName, V1Secret secret) throws ApiException {
        this.coreV1Api
                .replaceNamespacedSecret(objName, namespace, secret)
                .execute();
    }

    private void deleteSecret(String namespace, String objName) throws ApiException {
        this.coreV1Api
                .deleteNamespacedSecret(objName, namespace)
                .execute();
    }

    private void deleteSecret(V1Secret object) throws ApiException {
        deleteSecret(K8sObjectUtils.getNamespace(object), K8sObjectUtils.getName(object));
    }

}
