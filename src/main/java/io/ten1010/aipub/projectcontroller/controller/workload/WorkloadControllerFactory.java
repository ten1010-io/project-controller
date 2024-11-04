package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.builder.DefaultControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Reconciler;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.ten1010.aipub.projectcontroller.controller.ControllerFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectType;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;

import java.util.function.Function;

public abstract class WorkloadControllerFactory<T extends KubernetesObject> implements ControllerFactory {

    protected final DefaultControllerBuilder builder;
    protected final SharedInformerFactory sharedInformerFactory;
    protected final ReconciliationService reconciliationService;

    public WorkloadControllerFactory(
            SharedInformerFactory sharedInformerFactory,
            ReconciliationService reconciliationService) {
        this.builder = ControllerBuilder.defaultBuilder(sharedInformerFactory);
        this.sharedInformerFactory = sharedInformerFactory;
        this.reconciliationService = reconciliationService;
    }

    @Override
    public Controller createController() {
        configureControllerName();
        configureReadyFunc();
        configureWatch();
        this.builder.withWorkerCount(1);
        this.builder.withReconciler(createReconciler());
        this.builder.withReconciler(createReconciler());

        return this.builder.build();
    }

    public abstract K8sObjectType<T> getObjectType();

    public abstract WorkloadControllerNodesResolver getWorkloadNodesResolver();

    protected abstract void configureControllerName();

    protected abstract void configureReadyFunc();

    protected abstract void configureWatch();

    protected abstract Function<KubernetesObject, V1PodTemplateSpec> getPodTemplateSpecResolver();

    protected abstract ControllerObjectReconciler getObjectReconciler();

    private Reconciler createReconciler() {
        return new WorkloadControllerReconciler(
                this.sharedInformerFactory,
                this.reconciliationService,
                getObjectType().getObjClass(),
                getPodTemplateSpecResolver(),
                getObjectReconciler(),
                getWorkloadNodesResolver());
    }

}
