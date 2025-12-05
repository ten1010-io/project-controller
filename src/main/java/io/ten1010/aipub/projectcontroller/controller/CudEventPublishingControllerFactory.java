package io.ten1010.aipub.projectcontroller.controller;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@AllArgsConstructor
public class CudEventPublishingControllerFactory<ApiType extends KubernetesObject> implements ControllerFactory {

    @AllArgsConstructor
    private static class AdaptingReconciler<T extends KubernetesObject> extends AbstractReconciler {

        private final CudEventPublishingReconciler<T> origin;

        @Override
        protected Result reconcileInternal(Request request) throws ApiException {
            return this.origin.reconcile(unwrap(request));
        }

        private CudEventPublishRequest<T> unwrap(Request request) {
            if (request instanceof AdaptingRequest adaptingRequest) {
                return adaptingRequest.getOrigin();
            }
            throw new RuntimeException("Request must be AdaptingRequest");
        }

    }

    private static class AdaptingRequest<T extends KubernetesObject> extends Request {

        @Getter
        private final CudEventPublishRequest<T> origin;

        public AdaptingRequest(CudEventPublishRequest<T> origin, @Nullable String namespace, String name) {
            super(namespace, name);
            this.origin = origin;
        }

    }

    @AllArgsConstructor
    private static class AdaptingWorkQueue<T extends KubernetesObject> implements WorkQueue<CudEventPublishRequest<T>> {

        private final WorkQueue<Request> origin;

        @Override
        public void add(CudEventPublishRequest<T> item) {
            this.origin.add(wrap(item));
        }

        @Override
        public int length() {
            return this.origin.length();
        }

        @Override
        public CudEventPublishRequest<T> get() throws InterruptedException {
            return unwrap(this.origin.get());
        }

        @Override
        public void done(CudEventPublishRequest<T> item) {
            this.origin.done(wrap(item));
        }

        @Override
        public void shutDown() {
            this.origin.shutDown();
        }

        @Override
        public boolean isShuttingDown() {
            return this.origin.isShuttingDown();
        }

        private AdaptingRequest<T> wrap(CudEventPublishRequest<T> item) {
            return new AdaptingRequest<>(item, item.getNamespace().orElse(null), item.getName());
        }

        private CudEventPublishRequest<T> unwrap(Request request) {
            if (request instanceof AdaptingRequest adaptingRequest) {
                return adaptingRequest.getOrigin();
            }
            throw new RuntimeException("Request must be AdaptingRequest");
        }

    }

    private final String controllerName;
    private final Class<ApiType> targetObjectClass;
    private final CudEventPublishingReconciler<ApiType> reconciler;
    private final SharedInformerFactory sharedInformerFactory;

    @Override
    public Controller createController() {
        return ControllerBuilder.defaultBuilder(this.sharedInformerFactory)
                .withName(this.controllerName)
                .withWorkerCount(1)
                .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(this.targetObjectClass)::hasSynced)
                .watch(queue -> new CudEventPublishingWatch<>(new AdaptingWorkQueue<>(queue), this.targetObjectClass))
                .withReconciler(new AdaptingReconciler<>(this.reconciler))
                .build();
    }

}
