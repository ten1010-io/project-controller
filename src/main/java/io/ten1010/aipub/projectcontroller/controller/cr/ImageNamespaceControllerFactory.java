package io.ten1010.aipub.projectcontroller.controller.cr;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.ten1010.aipub.projectcontroller.controller.ControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.DefaultControllerWatch;
import io.ten1010.aipub.projectcontroller.controller.watch.OnUpdateFilterFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.RequestBuilderFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageNamespace;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;

public class ImageNamespaceControllerFactory implements ControllerFactory {

    private final SharedInformerFactory sharedInformerFactory;
    private final OnUpdateFilterFactory onUpdateFilterFactory;
    private final RequestBuilderFactory requestBuilderFactory;
    private final K8sApiProvider k8sApiProvider;
    private final ReconciliationService reconciliationService;

    public ImageNamespaceControllerFactory(
            SharedInformerFactory sharedInformerFactory,
            K8sApiProvider k8sApiProvider,
            ReconciliationService reconciliationService) {
        this.sharedInformerFactory = sharedInformerFactory;
        this.onUpdateFilterFactory = new OnUpdateFilterFactory();
        this.requestBuilderFactory = new RequestBuilderFactory(sharedInformerFactory);
        this.k8sApiProvider = k8sApiProvider;
        this.reconciliationService = reconciliationService;
    }

    @Override
    public Controller createController() {
        return ControllerBuilder.defaultBuilder(this.sharedInformerFactory)
                .withName("image-namespace-controller")
                .withWorkerCount(1)
                .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(V1alpha1ImageNamespace.class)::hasSynced)
                .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(V1alpha1Project.class)::hasSynced)
                .watch(this::createImageNamespaceWatch)
                .watch(this::createProjectSpecWatch)
                .watch(this::createProjectStatusWatch)
                .withReconciler(new ImageNamespaceReconciler(this.sharedInformerFactory, this.k8sApiProvider, this.reconciliationService))
                .build();
    }

    private ControllerWatch<V1alpha1ImageNamespace> createImageNamespaceWatch(WorkQueue<Request> workQueue) {
        DefaultControllerWatch<V1alpha1ImageNamespace> watch = new DefaultControllerWatch<>(workQueue, V1alpha1ImageNamespace.class);
        watch.setOnUpdateFilter(this.onUpdateFilterFactory.imageNamespaceSpecFieldFilter());
        return watch;
    }

    private ControllerWatch<V1alpha1Project> createProjectSpecWatch(WorkQueue<Request> workQueue) {
        DefaultControllerWatch<V1alpha1Project> watch = new DefaultControllerWatch<>(workQueue, V1alpha1Project.class);
        watch.setOnUpdateFilter(this.onUpdateFilterFactory.projectSpecFieldFilter());
        watch.setRequestBuilder(this.requestBuilderFactory.projectToBoundImageNamespaces());
        return watch;
    }

    private ControllerWatch<V1alpha1Project> createProjectStatusWatch(WorkQueue<Request> workQueue) {
        DefaultControllerWatch<V1alpha1Project> watch = new DefaultControllerWatch<>(workQueue, V1alpha1Project.class);
        watch.setOnUpdateFilter(this.onUpdateFilterFactory.projectStatusAllBoundAipubUsersFieldFilter());
        watch.setRequestBuilder(this.requestBuilderFactory.projectToBoundImageNamespaces());
        return watch;
    }

}
