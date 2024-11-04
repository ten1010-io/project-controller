package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1Pod;
import io.ten1010.aipub.projectcontroller.controller.ControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.DefaultControllerWatch;
import io.ten1010.aipub.projectcontroller.controller.watch.OnUpdateFilterFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.RequestBuilderFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroup;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;

public class PodControllerFactory implements ControllerFactory {

    private final SharedInformerFactory sharedInformerFactory;
    private final OnUpdateFilterFactory onUpdateFilterFactory;
    private final RequestBuilderFactory requestBuilderFactory;
    private final K8sApiProvider k8sApiProvider;
    private final PodNodesResolver podNodesResolver;

    public PodControllerFactory(
            SharedInformerFactory sharedInformerFactory,
            K8sApiProvider k8sApiProvider,
            PodNodesResolver podNodesResolver) {
        this.sharedInformerFactory = sharedInformerFactory;
        this.onUpdateFilterFactory = new OnUpdateFilterFactory();
        this.requestBuilderFactory = new RequestBuilderFactory(sharedInformerFactory);
        this.k8sApiProvider = k8sApiProvider;
        this.podNodesResolver = podNodesResolver;
    }

    @Override
    public Controller createController() {
        return ControllerBuilder.defaultBuilder(this.sharedInformerFactory)
                .withName("pod-controller")
                .withWorkerCount(1)
                .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(V1Pod.class)::hasSynced)
                .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(V1alpha1Project.class)::hasSynced)
                .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(V1alpha1NodeGroup.class)::hasSynced)
                .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(V1Node.class)::hasSynced)
                .watch(this::createPodWatch)
                .watch(this::createProjectWatch)
                .watch(this::createNodeGroupWatch)
                .watch(this::createNodeWatch)
                .watch(this::createBoundPodNodeWatch)
                .withReconciler(new PodReconciler(
                        this.sharedInformerFactory,
                        this.k8sApiProvider,
                        this.podNodesResolver))
                .build();
    }

    private ControllerWatch<V1Pod> createPodWatch(WorkQueue<Request> workQueue) {
        DefaultControllerWatch<V1Pod> watch = new DefaultControllerWatch<>(workQueue, V1Pod.class);
        watch.setOnUpdateFilter(this.onUpdateFilterFactory.podNodeNameFieldFilter());
        return watch;
    }

    private ControllerWatch<V1alpha1Project> createProjectWatch(WorkQueue<Request> workQueue) {
        DefaultControllerWatch<V1alpha1Project> watch = new DefaultControllerWatch<>(workQueue, V1alpha1Project.class);
        watch.setOnUpdateFilter(this.onUpdateFilterFactory.projectSpecBindingFieldFilter());
        watch.setRequestBuilder(this.requestBuilderFactory.projectToNamespacedObjects(V1Pod.class));
        return watch;
    }

    private ControllerWatch<V1alpha1NodeGroup> createNodeGroupWatch(WorkQueue<Request> workQueue) {
        DefaultControllerWatch<V1alpha1NodeGroup> watch = new DefaultControllerWatch<>(workQueue, V1alpha1NodeGroup.class);
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

}
