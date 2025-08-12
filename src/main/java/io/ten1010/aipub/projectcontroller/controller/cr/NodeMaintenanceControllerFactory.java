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
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeMaintenance;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class NodeMaintenanceControllerFactory implements ControllerFactory {

    private final SharedInformerFactory sharedInformerFactory;
    private final K8sApiProvider k8sApiProvider;
    private final ReconciliationService reconciliationService;
    private final OnUpdateFilterFactory onUpdateFilterFactory;
    private final RequestBuilderFactory requestBuilderFactory;

    public NodeMaintenanceControllerFactory(
            SharedInformerFactory sharedInformerFactory,
            K8sApiProvider k8sApiProvider,
            ReconciliationService reconciliationService) {
        this.sharedInformerFactory = sharedInformerFactory;
        this.k8sApiProvider = k8sApiProvider;
        this.reconciliationService = reconciliationService;
        this.onUpdateFilterFactory = new OnUpdateFilterFactory();
        this.requestBuilderFactory = new RequestBuilderFactory(sharedInformerFactory);
    }

    @Override
    public Controller createController() {
        return ControllerBuilder.defaultBuilder(this.sharedInformerFactory)
                .withName("node-maintenance-controller")
                .withWorkerCount(1)
                .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(V1alpha1NodeMaintenance.class)::hasSynced)
                .watch(this::createNodeMaintenanceWatch)
                .withReconciler(new NodeMaintenanceReconciler(this.reconciliationService, this.sharedInformerFactory, this.k8sApiProvider))
                .build();
    }

    private ControllerWatch<V1alpha1NodeMaintenance> createNodeMaintenanceWatch(WorkQueue<Request> workQueue) {
        DefaultControllerWatch<V1alpha1NodeMaintenance> watch = new DefaultControllerWatch<>(workQueue, V1alpha1NodeMaintenance.class);
        watch.setOnUpdateFilter(this.onUpdateFilterFactory.nodeMaintenanceSpecFieldFilter());
        return watch;
    }

}
