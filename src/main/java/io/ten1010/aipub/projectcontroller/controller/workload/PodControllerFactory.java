package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1Pod;
import io.ten1010.aipub.projectcontroller.controller.ControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.DefaultControllerWatch;
import io.ten1010.aipub.projectcontroller.controller.watch.OnUpdateFilterFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.RequestBuilderFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroup;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;

public class PodControllerFactory implements ControllerFactory {

  private final SharedInformerFactory sharedInformerFactory;
  private final OnUpdateFilterFactory onUpdateFilterFactory;
  private final RequestBuilderFactory requestBuilderFactory;
  private final K8sApiProvider k8sApiProvider;
  private final PodNodesResolver podNodesResolver;
  private final NamespaceAllowlistResolver namespaceAllowlistResolver;

  public PodControllerFactory(
      SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      PodNodesResolver podNodesResolver,
      NamespaceAllowlistResolver namespaceAllowlistResolver) {
    this.sharedInformerFactory = sharedInformerFactory;
    this.onUpdateFilterFactory = new OnUpdateFilterFactory();
    this.requestBuilderFactory = new RequestBuilderFactory(sharedInformerFactory);
    this.k8sApiProvider = k8sApiProvider;
    this.podNodesResolver = podNodesResolver;
    this.namespaceAllowlistResolver = namespaceAllowlistResolver;
  }

  @Override
  public Controller createController() {
    return ControllerBuilder.defaultBuilder(this.sharedInformerFactory)
        .withName("pod-controller")
        .withWorkerCount(1)
        .withReadyFunc(
            this.sharedInformerFactory.getExistingSharedIndexInformer(V1Pod.class)::hasSynced)
        .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
            V1alpha1Project.class)::hasSynced)
        .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
            V1alpha1NodeGroup.class)::hasSynced)
        .withReadyFunc(
            this.sharedInformerFactory.getExistingSharedIndexInformer(V1Node.class)::hasSynced)
        .withReadyFunc(
            this.sharedInformerFactory.getExistingSharedIndexInformer(V1Namespace.class)::hasSynced)
        .watch(this::createPodWatch)
        .watch(this::createProjectWatch)
        .watch(this::createNodeGroupWatch)
        .watch(this::createNodeWatch)
        .watch(this::createBoundPodNodeWatch)
        .watch(this::createNamespaceWatch)
        .withReconciler(new PodReconciler(
            this.sharedInformerFactory,
            this.k8sApiProvider,
            this.podNodesResolver,
            this.namespaceAllowlistResolver))
        .build();
  }

  private ControllerWatch<V1Pod> createPodWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1Pod> watch = new DefaultControllerWatch<>(workQueue, V1Pod.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.podNodeNameFieldFilter());
    return watch;
  }

  private ControllerWatch<V1alpha1Project> createProjectWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1Project> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1Project.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.projectSpecBindingFieldFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.projectToNamespacedObjects(V1Pod.class));
    return watch;
  }

  private ControllerWatch<V1alpha1NodeGroup> createNodeGroupWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1NodeGroup> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1NodeGroup.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.nodeGroupSpecFieldFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.nodeGroupToNamespacedObjects(V1Pod.class));
    return watch;
  }

  private ControllerWatch<V1Node> createNodeWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1Node> watch = new DefaultControllerWatch<>(workQueue, V1Node.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.nodeFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.nodeToNamespacedObjects(V1Pod.class));
    return watch;
  }

  private ControllerWatch<V1Node> createBoundPodNodeWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1Node> watch = new DefaultControllerWatch<>(workQueue, V1Node.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.nodeFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.nodeToBoundPods());
    return watch;
  }

  // allowlist 라벨이 제거되면 해당 네임스페이스의 파드를 다시 평가해, 재시작 없이 런타임에
  // eviction 정책이 반영되게 한다.
  private ControllerWatch<V1Namespace> createNamespaceWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1Namespace> watch = new DefaultControllerWatch<>(workQueue,
        V1Namespace.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.namespaceAllowlistLabelFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.namespaceToNamespacedObjects(V1Pod.class));
    return watch;
  }

}
