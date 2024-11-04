package io.ten1010.aipub.projectcontroller.controller.namespaced;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.V1Secret;
import io.ten1010.aipub.projectcontroller.controller.ControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.DefaultControllerWatch;
import io.ten1010.aipub.projectcontroller.controller.watch.OnUpdateFilterFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.RequestBuilderFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;

public class ImagePullSecretReconcilerFactory implements ControllerFactory {

    private final SharedInformerFactory sharedInformerFactory;
    private final OnUpdateFilterFactory onUpdateFilterFactory;
    private final RequestBuilderFactory requestBuilderFactory;
    private final K8sApiProvider k8sApiProvider;
    private final ReconciliationService reconciliationService;

    public ImagePullSecretReconcilerFactory(
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
                .withName("image-pull-secret-controller")
                .withWorkerCount(1)
                .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(V1Secret.class)::hasSynced)
                .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(V1alpha1Project.class)::hasSynced)
                .watch(this::createSecretWatch)
                .watch(this::createProjectWatch)
                .withReconciler(new ImagePullSecretReconciler(this.sharedInformerFactory, this.k8sApiProvider, this.reconciliationService))
                .build();
    }

    private ControllerWatch<V1Secret> createSecretWatch(WorkQueue<Request> workQueue) {
        DefaultControllerWatch<V1Secret> watch = new DefaultControllerWatch<>(workQueue, V1Secret.class);
        watch.setOnUpdateFilter(this.onUpdateFilterFactory.alwaysFalseFilter());
        watch.setRequestBuilder(this.requestBuilderFactory.secretToImagePullSecrets());
        return watch;
    }

    private ControllerWatch<V1alpha1Project> createProjectWatch(WorkQueue<Request> workQueue) {
        DefaultControllerWatch<V1alpha1Project> watch = new DefaultControllerWatch<>(workQueue, V1alpha1Project.class);
        watch.setOnUpdateFilter(this.onUpdateFilterFactory.projectSpecBindingImageNamespacesFieldFilter());
        watch.setRequestBuilder(this.requestBuilderFactory.projectToImagePullSecrets());
        return watch;
    }

}
