package io.ten1010.aipub.projectcontroller.controller.cr;

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
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeMaintenance;

public class NodeMaintenanceControllerFactory implements ControllerFactory {

    private final SharedInformerFactory sharedInformerFactory;
    private final K8sApiProvider k8sApiProvider;
    private final OnUpdateFilterFactory onUpdateFilterFactory;

    public NodeMaintenanceControllerFactory(
            SharedInformerFactory sharedInformerFactory,
            K8sApiProvider k8sApiProvider,
            ReconciliationService reconciliationService) {
        this.sharedInformerFactory = sharedInformerFactory;
        this.k8sApiProvider = k8sApiProvider;
        this.onUpdateFilterFactory = new OnUpdateFilterFactory();
    }

    @Override
    public Controller createController() {
        return ControllerBuilder.defaultBuilder(this.sharedInformerFactory)
                .withName("node-maintenance-controller")
                .withWorkerCount(1)
                .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(V1alpha1NodeMaintenance.class)::hasSynced)
                .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(V1Node.class)::hasSynced)
                .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(V1Pod.class)::hasSynced)
                .watch(this::createNodeMaintenanceWatch)
                .watch(this::changeNodeStatus)
                .watch(this::changePodStatus)
                .withReconciler(new NodeMaintenanceReconciler(this.sharedInformerFactory, this.k8sApiProvider))
                .build();
    }

    private ControllerWatch<V1Node> changeNodeStatus(WorkQueue<Request> workQueue) {
        DefaultControllerWatch<V1Node> watch = new DefaultControllerWatch<>(workQueue, V1Node.class);
        watch.setOnUpdateFilter(this.onUpdateFilterFactory.nodeFilter());
        return watch;
    }

    private ControllerWatch<V1Pod> changePodStatus(WorkQueue<Request> workQueue) {
        DefaultControllerWatch<V1Pod> watch = new DefaultControllerWatch<>(workQueue, V1Pod.class);
        watch.setOnUpdateFilter(this.onUpdateFilterFactory.podNodeNameFieldFilter());
        return watch;
    }

    private ControllerWatch<V1alpha1NodeMaintenance> createNodeMaintenanceWatch(WorkQueue<Request> workQueue) {
        DefaultControllerWatch<V1alpha1NodeMaintenance> watch = new DefaultControllerWatch<>(workQueue, V1alpha1NodeMaintenance.class);
        watch.setOnUpdateFilter(this.onUpdateFilterFactory.nodeMaintenanceCreateFilter());
        return watch;
    }

}
