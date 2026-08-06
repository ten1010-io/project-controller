package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1NodeSelectorTerm;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.aipub.projectcontroller.controller.AbstractReconciler;
import io.ten1010.aipub.projectcontroller.controller.ReconcileRequestLogMessageFactory;
import io.ten1010.aipub.projectcontroller.controller.RequestHelper;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class WorkloadControllerReconciler extends AbstractReconciler {

  private final KeyResolver keyResolver;
  private final ReconciliationService reconciliationService;
  private final Class<? extends KubernetesObject> controllerObjectClass;
  private final Indexer<? extends KubernetesObject> controllerIndexer;
  private final Indexer<V1alpha1Project> projectIndexer;
  private final Function<KubernetesObject, V1PodTemplateSpec> podTemplateSpecResolver;
  private final ControllerObjectReconciler controllerObjectReconciler;
  private final WorkloadControllerNodesResolver workloadControllerNodesResolver;

  public WorkloadControllerReconciler(
      SharedInformerFactory sharedInformerFactory,
      ReconciliationService reconciliationService,
      Class<? extends KubernetesObject> controllerObjectClass,
      Function<KubernetesObject, V1PodTemplateSpec> podTemplateSpecResolver,
      ControllerObjectReconciler controllerObjectReconciler,
      WorkloadControllerNodesResolver workloadControllerNodesResolver) {
    ReconcileRequestLogMessageFactory logMessageFactory = new ReconcileRequestLogMessageFactory();
    logMessageFactory.setRequestDescriptionFactory(this::createRequestDescription);
    setLogMessageFactory(logMessageFactory);
    this.keyResolver = new KeyResolver();
    this.reconciliationService = reconciliationService;
    this.controllerIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(controllerObjectClass)
        .getIndexer();
    this.projectIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1Project.class)
        .getIndexer();
    this.controllerObjectClass = controllerObjectClass;
    this.podTemplateSpecResolver = podTemplateSpecResolver;
    this.controllerObjectReconciler = controllerObjectReconciler;
    this.workloadControllerNodesResolver = workloadControllerNodesResolver;
  }

  @Override
  protected Result reconcileInternal(Request request) throws ApiException {
    String controllerKey = new RequestHelper(this.keyResolver).resolveKey(request);
    Optional<KubernetesObject> controllerOpt = Optional.ofNullable(
        this.controllerIndexer.getByKey(controllerKey));
    if (controllerOpt.isEmpty()) {
      return new Result(false);
    }
    KubernetesObject controller = controllerOpt.get();
    if (K8sObjectUtils.findControllerOwnerReference(controller).isPresent()) {
      return new Result(false);
    }

    V1PodTemplateSpec templateSpec = this.podTemplateSpecResolver.apply(controller);

    // allowlist 네임스페이스는 라벨 제외 검사보다 먼저 처리한다. 제외 라벨이 붙은 워크로드라도
    // project-managed 노드에 스케줄되려면 toleration 주입이 필요하기 때문이다(PodReviewHandler와
    // 동일한 순서). project 소속과 무관하게 Exists toleration 쌍을 주입한다(project=null 경로는
    // affinity를 걷어내고 imagePullSecrets는 그대로 둔다).
    if (this.reconciliationService.isNamespaceAllowlisted(request.getNamespace())) {
      return this.controllerObjectReconciler.reconcileController(controller,
          this.reconciliationService.reconcileTolerationsForAllowlistedNamespace(templateSpec),
          this.reconciliationService.reconcileNodeSelectorTerms(templateSpec, null),
          this.reconciliationService.reconcileImageRegistrySecrets(templateSpec, null));
    }

    if (this.reconciliationService.isExcludedFromReconciliation(controller)) {
      return new Result(false);
    }

    String projKey = this.keyResolver.resolveKey(request.getNamespace());
    V1alpha1Project project = this.projectIndexer.getByKey(projKey);

    List<V1Node> nodeObjects = this.workloadControllerNodesResolver.getNodes(controller);
    List<V1Toleration> reconciledTolerations = this.reconciliationService.reconcileTolerations(
        templateSpec, nodeObjects);
    List<V1NodeSelectorTerm> reconciledSelectorTerms = this.reconciliationService.reconcileNodeSelectorTerms(
        templateSpec, project);
    List<V1LocalObjectReference> reconciledImagePullSecrets = this.reconciliationService.reconcileImageRegistrySecrets(
        templateSpec, project);

    return this.controllerObjectReconciler.reconcileController(controller, reconciledTolerations,
        reconciledSelectorTerms, reconciledImagePullSecrets);
  }

  private String createRequestDescription(Request request) {
    return String.format(
        "class=%s namespace=%s name=%s",
        this.controllerObjectClass.getSimpleName(),
        request.getNamespace(),
        request.getName());
  }

}
