package io.ten1010.aipub.projectcontroller.controller.watch;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.ResourceEventHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class DefaultControllerWatch<ApiType extends KubernetesObject> implements ControllerWatch<ApiType> {

    private final WorkQueue<Request> workQueue;
    private final Class<ApiType> apiTypeClass;

    @Getter
    @Setter
    private Function<ApiType, List<Request>> requestBuilder;
    @Getter
    @Setter
    private BiPredicate<ApiType, ApiType> onUpdateFilter;

    public DefaultControllerWatch(WorkQueue<Request> workQueue, Class<ApiType> apiTypeClass) {
        this.workQueue = workQueue;
        this.apiTypeClass = apiTypeClass;
        this.requestBuilder = obj -> {
            Objects.requireNonNull(obj.getMetadata());
            Objects.requireNonNull(obj.getMetadata().getName());
            Request request = new Request(obj.getMetadata().getNamespace(), obj.getMetadata().getName());
            return List.of(request);
        };
        this.onUpdateFilter = new OnUpdateFilterFactory().alwaysTrueFilter();
    }

    @Override
    public Class<ApiType> getResourceClass() {
        return this.apiTypeClass;
    }

    @Override
    public ResourceEventHandler<ApiType> getResourceEventHandler() {
        return new ResourceEventHandler<>() {

            @Override
            public void onAdd(ApiType obj) {
                requestBuilder.apply(obj).forEach(workQueue::add);
            }

            @Override
            public void onUpdate(ApiType oldObj, ApiType newObj) {
                if (onUpdateFilter.test(oldObj, newObj)) {
                    Set<Request> requests = new HashSet<>(requestBuilder.apply(oldObj));
                    requests.addAll(requestBuilder.apply(newObj));
                    requests.forEach(workQueue::add);
                }
            }

            @Override
            public void onDelete(ApiType obj, boolean deletedFinalStateUnknown) {
                requestBuilder.apply(obj).forEach(workQueue::add);
            }

        };
    }

    @Override
    public Duration getResyncPeriod() {
        return Duration.ZERO;
    }

}
