package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.*;
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

public class ReplicaSetWorkloadControllerFactory extends WorkloadControllerFactory {

  private final AppsV1Api appsV1Api;
  private final OnUpdateFilterFactory onUpdateFilterFactory;
  private final RequestBuilderFactory requestBuilderFactory;

  public ReplicaSetWorkloadControllerFactory(
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
    return K8sObjectTypeConstants.REPLICA_SET_V1;
  }

  @Override
  public WorkloadControllerNodesResolver getWorkloadNodesResolver() {
    return new DefaultWorkloadControllerNodesResolver(this.sharedInformerFactory);
  }

  @Override
  protected void configureControllerName() {
    this.builder.withName("replica-set-controller");
  }

  @Override
  protected void configureReadyFunc() {
    this.builder.withReadyFunc(
        this.sharedInformerFactory.getExistingSharedIndexInformer(V1ReplicaSet.class)::hasSynced);
    this.builder.withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
        V1alpha1Project.class)::hasSynced);
    this.builder.withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
        V1alpha1NodeGroup.class)::hasSynced);
    this.builder.withReadyFunc(
        this.sharedInformerFactory.getExistingSharedIndexInformer(V1Node.class)::hasSynced);
  }

  @Override
  protected void configureWatch() {
    this.builder.watch(this::createReplicaSetWatch);
    this.builder.watch(this::createProjectWatch);
    this.builder.watch(this::createNodeGroupWatch);
    this.builder.watch(this::createNodeWatch);
  }

  @Override
  protected Function<KubernetesObject, V1PodTemplateSpec> getPodTemplateSpecResolver() {
    return object -> {
      if (!(object instanceof V1ReplicaSet replicaSet)) {
        throw new IllegalArgumentException();
      }
      return WorkloadUtils.getPodTemplateSpec(replicaSet);
    };
  }

  @Override
  protected ControllerObjectReconciler getObjectReconciler() {
    return (KubernetesObject controller,
        List<V1Toleration> reconciledTolerations,
        List<V1NodeSelectorTerm> reconciledSelectorTerms,
        List<V1LocalObjectReference> reconciledImagePullSecrets) -> {
      if (!(controller instanceof V1ReplicaSet replicaSet)) {
        throw new IllegalArgumentException();
      }
      return this.reconcileController(replicaSet, reconciledTolerations, reconciledSelectorTerms,
          reconciledImagePullSecrets);
    };
  }

  private Result reconcileController(
      V1ReplicaSet controller,
      List<V1Toleration> reconciledTolerations,
      List<V1NodeSelectorTerm> reconciledSelectorTerms,
      List<V1LocalObjectReference> reconciledImagePullSecrets) throws ApiException {
    if (Set.copyOf(WorkloadUtils.getTolerations(controller))
        .equals(Set.copyOf(reconciledTolerations)) &&
        Set.copyOf(WorkloadUtils.getNodeSelectorTerms(controller))
            .equals(Set.copyOf(reconciledSelectorTerms)) &&
        Set.copyOf(WorkloadUtils.getImagePullSecrets(controller))
            .equals(Set.copyOf(reconciledImagePullSecrets))) {
      return new Result(false);
    }
    V1ReplicaSet edited;
    if (reconciledSelectorTerms.isEmpty()) {
      edited = new V1ReplicaSetBuilder(controller)
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
      edited = new V1ReplicaSetBuilder(controller)
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
    updateReplicaSet(K8sObjectUtils.getNamespace(controller), K8sObjectUtils.getName(controller),
        edited);
    return new Result(false);
  }

  private void updateReplicaSet(String namespace, String objName, V1ReplicaSet replicaSet)
      throws ApiException {
    this.appsV1Api
        .replaceNamespacedReplicaSet(objName, namespace, replicaSet)
        .execute();
  }

  private ControllerWatch<V1ReplicaSet> createReplicaSetWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1ReplicaSet> watch = new DefaultControllerWatch<>(workQueue,
        V1ReplicaSet.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.replicaSetFilter());
    return watch;
  }

  private ControllerWatch<V1alpha1Project> createProjectWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1Project> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1Project.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.projectSpecBindingFieldFilter());
    watch.setRequestBuilder(
        this.requestBuilderFactory.projectToNamespacedObjects(V1ReplicaSet.class));
    return watch;
  }

  private ControllerWatch<V1alpha1NodeGroup> createNodeGroupWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1NodeGroup> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1NodeGroup.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.nodeGroupSpecFieldFilter());
    watch.setRequestBuilder(
        this.requestBuilderFactory.nodeGroupToNamespacedObjects(V1ReplicaSet.class));
    return watch;
  }

  private ControllerWatch<V1Node> createNodeWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1Node> watch = new DefaultControllerWatch<>(workQueue, V1Node.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.nodeFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.nodeToNamespacedObjects(V1ReplicaSet.class));
    return watch;
  }

}
