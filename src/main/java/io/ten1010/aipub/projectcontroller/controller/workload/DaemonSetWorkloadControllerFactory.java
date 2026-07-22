package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1DaemonSetBuilder;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1NodeSelectorTerm;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.aipub.projectcontroller.controller.watch.DefaultControllerWatch;
import io.ten1010.aipub.projectcontroller.controller.watch.OnUpdateFilterFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.RequestBuilderFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectType;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroup;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.WorkloadUtils;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class DaemonSetWorkloadControllerFactory extends WorkloadControllerFactory {

  private final AppsV1Api appsV1Api;
  private final OnUpdateFilterFactory onUpdateFilterFactory;
  private final RequestBuilderFactory requestBuilderFactory;

  public DaemonSetWorkloadControllerFactory(
      SharedInformerFactory sharedInformerFactory,
      ReconciliationService reconciliationService,
      K8sApiProvider k8sApiProvider) {
    super(sharedInformerFactory, reconciliationService);
    this.appsV1Api = new AppsV1Api(k8sApiProvider.getApiClient());
    this.onUpdateFilterFactory = new OnUpdateFilterFactory();
    this.requestBuilderFactory = new RequestBuilderFactory(sharedInformerFactory);
  }

  @Override
  public K8sObjectType getObjectType() {
    return K8sObjectTypeConstants.DAEMON_SET_V1;
  }

  @Override
  public WorkloadControllerNodesResolver getWorkloadNodesResolver() {
    return new DaemonSetWorkloadControllerNodesResolver(this.sharedInformerFactory);
  }

  @Override
  protected void configureControllerName() {
    this.builder.withName("daemon-set-controller");
  }

  @Override
  protected void configureReadyFunc() {
    this.builder.withReadyFunc(
        this.sharedInformerFactory.getExistingSharedIndexInformer(V1DaemonSet.class)::hasSynced);
    this.builder.withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
        V1alpha1Project.class)::hasSynced);
    this.builder.withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
        V1alpha1NodeGroup.class)::hasSynced);
    this.builder.withReadyFunc(
        this.sharedInformerFactory.getExistingSharedIndexInformer(V1Node.class)::hasSynced);
  }

  @Override
  protected void configureWatch() {
    this.builder.watch(this::createDaemonSetWatch);
    this.builder.watch(this::createProjectWatch);
    this.builder.watch(this::createNodeGroupWatch);
    this.builder.watch(this::createNodeWatch);
  }

  @Override
  protected Function<KubernetesObject, V1PodTemplateSpec> getPodTemplateSpecResolver() {
    return object -> {
      if (!(object instanceof V1DaemonSet daemonSet)) {
        throw new IllegalArgumentException();
      }
      return WorkloadUtils.getPodTemplateSpec(daemonSet);
    };
  }

  @Override
  protected ControllerObjectReconciler getObjectReconciler() {
    return (KubernetesObject controller,
        List<V1Toleration> reconciledTolerations,
        List<V1NodeSelectorTerm> reconciledSelectorTerms,
        List<V1LocalObjectReference> reconciledImagePullSecrets) -> {
      if (!(controller instanceof V1DaemonSet daemonSet)) {
        throw new IllegalArgumentException();
      }
      return this.reconcileController(daemonSet, reconciledTolerations, reconciledSelectorTerms,
          reconciledImagePullSecrets);
    };
  }

  private Result reconcileController(
      V1DaemonSet controller,
      List<V1Toleration> reconciledTolerations,
      List<V1NodeSelectorTerm> reconciledSelectorTerms,
      List<V1LocalObjectReference> reconciledImagePullSecrets) throws ApiException {
    if (Set.copyOf(WorkloadUtils.getTolerations(controller))
        .equals(Set.copyOf(reconciledTolerations)) &&
        Set.copyOf(WorkloadUtils.getNodeSelectorTerms(controller))
            .equals(Set.copyOf(reconciledSelectorTerms)) &&
        Set.copyOf(WorkloadUtils.getImageRegistrySecrets(controller))
            .equals(Set.copyOf(reconciledImagePullSecrets))) {
      return new Result(false);
    }
    V1DaemonSet edited;
    if (reconciledSelectorTerms.isEmpty()) {
      edited = new V1DaemonSetBuilder(controller)
          .editSpec()
          .editTemplate()
          .editSpec()
          .withTolerations(reconciledTolerations)
          .editAffinity()
          .editNodeAffinity()
          .withRequiredDuringSchedulingIgnoredDuringExecution(null)
          .endNodeAffinity()
          .endAffinity()
          .withImagePullSecrets(reconciledImagePullSecrets)
          .endSpec()
          .endTemplate()
          .endSpec()
          .build();
    } else {
      edited = new V1DaemonSetBuilder(controller)
          .editSpec()
          .editTemplate()
          .editSpec()
          .withTolerations(reconciledTolerations)
          .editAffinity()
          .editNodeAffinity()
          .editRequiredDuringSchedulingIgnoredDuringExecution()
          .withNodeSelectorTerms(reconciledSelectorTerms)
          .endRequiredDuringSchedulingIgnoredDuringExecution()
          .endNodeAffinity()
          .endAffinity()
          .withImagePullSecrets(reconciledImagePullSecrets)
          .endSpec()
          .endTemplate()
          .endSpec()
          .build();
    }
    updateDaemonSet(K8sObjectUtils.getNamespace(controller), K8sObjectUtils.getName(controller),
        edited);
    return new Result(false);
  }

  private void updateDaemonSet(String namespace, String objName, V1DaemonSet daemonSet)
      throws ApiException {
    this.appsV1Api
        .replaceNamespacedDaemonSet(objName, namespace, daemonSet)
        .execute();
  }

  private ControllerWatch<V1DaemonSet> createDaemonSetWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1DaemonSet> watch = new DefaultControllerWatch<>(workQueue,
        V1DaemonSet.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.daemonSetFilter());
    return watch;
  }

  private ControllerWatch<V1alpha1Project> createProjectWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1Project> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1Project.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.projectSpecBindingFieldFilter());
    watch.setRequestBuilder(
        this.requestBuilderFactory.projectToNamespacedObjects(V1DaemonSet.class));
    return watch;
  }

  private ControllerWatch<V1alpha1NodeGroup> createNodeGroupWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1NodeGroup> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1NodeGroup.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.nodeGroupSpecFieldFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.nodeGroupToAllDaemonSet());
    return watch;
  }

  private ControllerWatch<V1Node> createNodeWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1Node> watch = new DefaultControllerWatch<>(workQueue, V1Node.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.nodeFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.nodeToNamespacedObjects(V1DaemonSet.class));
    return watch;
  }

}
