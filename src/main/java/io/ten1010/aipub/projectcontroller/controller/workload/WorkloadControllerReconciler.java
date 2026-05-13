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
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class WorkloadControllerReconciler extends AbstractReconciler {

  private static final String PART_OF_LABEL_KEY = "app.kubernetes.io/part-of";
  private static final String CILIUM_PART_OF_VALUE = "cilium";

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
    if (isCiliumComponent(controller)) {
      return new Result(false);
    }
    if (K8sObjectUtils.findControllerOwnerReference(controller).isPresent()) {
      return new Result(false);
    }
    String projKey = this.keyResolver.resolveKey(request.getNamespace());
    V1alpha1Project project = this.projectIndexer.getByKey(projKey);

    V1PodTemplateSpec templateSpec = this.podTemplateSpecResolver.apply(controller);
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

  private static boolean isCiliumComponent(KubernetesObject controller) {
    if (controller.getMetadata() == null) {
      return false;
    }
    Map<String, String> labels = controller.getMetadata().getLabels();
    if (labels == null) {
      return false;
    }
    return CILIUM_PART_OF_VALUE.equals(labels.get(PART_OF_LABEL_KEY));
  }

  private String createRequestDescription(Request request) {
    return String.format(
        "class=%s namespace=%s name=%s",
        this.controllerObjectClass.getSimpleName(),
        request.getNamespace(),
        request.getName());
  }

}
