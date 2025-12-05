package io.ten1010.aipub.projectcontroller.controller;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import lombok.AllArgsConstructor;

import java.time.Duration;
import java.util.Objects;

@AllArgsConstructor
public class CudEventPublishingWatch<ApiType extends KubernetesObject> implements ControllerWatch<ApiType> {

    private final WorkQueue<CudEventPublishRequest<ApiType>> workQueue;
    private final Class<ApiType> apiTypeClass;

    @Override
    public Class<ApiType> getResourceClass() {
        return this.apiTypeClass;
    }

    @Override
    public ResourceEventHandler<ApiType> getResourceEventHandler() {
        return new ResourceEventHandler<>() {

            @Override
            public void onAdd(ApiType obj) {
                Objects.requireNonNull(obj.getMetadata());
                Objects.requireNonNull(obj.getMetadata().getName());
                CudEventPublishRequest<ApiType> req = CudEventPublishRequest.buildCreatedEventRequest(
                        obj.getMetadata().getNamespace(), obj.getMetadata().getName(), obj);
                workQueue.add(req);
            }

            @Override
            public void onUpdate(ApiType oldObj, ApiType newObj) {
                Objects.requireNonNull(oldObj.getMetadata());
                Objects.requireNonNull(oldObj.getMetadata().getName());
                CudEventPublishRequest<ApiType> req = CudEventPublishRequest.buildUpdatedEventRequest(
                        oldObj.getMetadata().getNamespace(), oldObj.getMetadata().getName(), oldObj, newObj);
                workQueue.add(req);
            }

            @Override
            public void onDelete(ApiType obj, boolean deletedFinalStateUnknown) {
                Objects.requireNonNull(obj.getMetadata());
                Objects.requireNonNull(obj.getMetadata().getName());
                CudEventPublishRequest<ApiType> req = CudEventPublishRequest.buildDeletedEventRequest(
                        obj.getMetadata().getNamespace(), obj.getMetadata().getName(), obj);
                workQueue.add(req);
            }

        };
    }

    @Override
    public Duration getResyncPeriod() {
        return Duration.ZERO;
    }

}
