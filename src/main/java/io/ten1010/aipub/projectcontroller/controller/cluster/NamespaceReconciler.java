package io.ten1010.aipub.projectcontroller.controller.cluster;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceBuilder;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceNameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class NamespaceReconciler extends AbstractReconciler {

  private final KeyResolver keyResolver;
  private final NamespaceNameResolver namespaceNameResolver;
  private final ReconciliationService reconciliationService;
  private final Indexer<V1Namespace> namespaceIndexer;
  private final Indexer<V1alpha1Project> projectIndexer;
  private final CoreV1Api coreV1Api;

  public NamespaceReconciler(
      SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    this.keyResolver = new KeyResolver();
    this.namespaceNameResolver = new NamespaceNameResolver();
    this.reconciliationService = reconciliationService;
    this.namespaceIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1Namespace.class)
        .getIndexer();
    this.projectIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1Project.class)
        .getIndexer();
    this.coreV1Api = new CoreV1Api(k8sApiProvider.getApiClient());
  }

  @Override
  protected Result reconcileInternal(Request request) throws ApiException {
    String nsName = request.getName();
    String projName = this.namespaceNameResolver.resolveProjectName(nsName);

    String namespaceKey = this.keyResolver.resolveKey(nsName);
    String projKey = this.keyResolver.resolveKey(projName);
    Optional<V1Namespace> namespaceOpt = Optional.ofNullable(
        this.namespaceIndexer.getByKey(namespaceKey));
    Optional<V1alpha1Project> projectOpt = Optional.ofNullable(
        this.projectIndexer.getByKey(projKey));

    List<V1OwnerReference> reconciledReferences = this.reconciliationService.reconcileOwnerReferences(
        namespaceOpt.orElse(null),
        projectOpt.orElse(null));

    if (namespaceOpt.isPresent()) {
      return reconcileExistingNamespace(namespaceOpt.get(), reconciledReferences);
    }

    if (projectOpt.isPresent() && !K8sObjectUtils.isTerminating(projectOpt.get())) {
      return reconcileNoExistingNamespace(request.getName(), reconciledReferences);
    }

    return new Result(false);
  }

  private Result reconcileNoExistingNamespace(String objName,
      List<V1OwnerReference> reconciledReferences) throws ApiException {
    V1Namespace namespace = new V1NamespaceBuilder()
        .withNewMetadata()
        .withName(objName)
        .withOwnerReferences(reconciledReferences)
        .endMetadata()
        .build();
    createNamespace(namespace);
    return new Result(false);
  }

  private Result reconcileExistingNamespace(V1Namespace namespace,
      List<V1OwnerReference> reconciledReferences) throws ApiException {
    if (Set.copyOf(K8sObjectUtils.getOwnerReferences(namespace))
        .equals(Set.copyOf(reconciledReferences))) {
      return new Result(false);
    }
    V1Namespace edited = new V1NamespaceBuilder(namespace)
        .editMetadata()
        .withOwnerReferences(reconciledReferences)
        .endMetadata()
        .build();
    updateNamespace(K8sObjectUtils.getName(namespace), edited);
    return new Result(false);
  }

  private void createNamespace(V1Namespace namespace) throws ApiException {
    this.coreV1Api
        .createNamespace(namespace)
        .execute();
  }

  private void updateNamespace(String objName, V1Namespace namespace) throws ApiException {
    this.coreV1Api
        .replaceNamespace(objName, namespace)
        .execute();
  }

}
