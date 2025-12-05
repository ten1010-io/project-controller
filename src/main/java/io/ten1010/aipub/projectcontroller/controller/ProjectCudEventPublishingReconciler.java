package io.ten1010.aipub.projectcontroller.controller;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.EventsV1Api;
import io.kubernetes.client.openapi.models.EventsV1Event;
import io.kubernetes.client.openapi.models.EventsV1EventBuilder;
import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectReferenceBuilder;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

public class ProjectCudEventPublishingReconciler implements CudEventPublishingReconciler<V1alpha1Project> {

    private final String controllerName;

    private final EventsV1Api eventsV1Api;

    public ProjectCudEventPublishingReconciler(String controllerName, K8sApiProvider k8sApiProvider) {
        this.controllerName = controllerName;

        this.eventsV1Api = new EventsV1Api(k8sApiProvider.getApiClient());
    }

    @Override
    public Result reconcile(CudEventPublishRequest<V1alpha1Project> request) throws ApiException {
        return switch (request.getEventEnum()) {
            case CREATED -> {
                String eventName = generateEventName(request);
                V1ObjectReference regarding = getV1ObjectReference(request.getNewObj().orElseThrow());
                EventsV1Event event = new EventsV1EventBuilder()
                        .withNewMetadata()
                        .withName(eventName)
                        .endMetadata()
                        .withEventTime(generateEventTime())
                        .withReportingController(this.controllerName)
                        .withReportingInstance(this.controllerName)
                        .withType("Normal")
                        .withReason("ProjectCreated")
                        .withAction("ProjectCreated")
                        .withNote("Project created")
                        .withRegarding(regarding)
                        .build();
                String eventNs = request.getNamespace().orElse("default");
                this.eventsV1Api
                        .createNamespacedEvent(eventNs, event)
                        .execute();
                yield new Result(false);
            }
            case UPDATED -> {
                V1alpha1Project oldObj = request.getOldObj().orElseThrow();
                V1alpha1Project newObj = request.getNewObj().orElseThrow();
                Objects.requireNonNull(oldObj.getSpec());
                Objects.requireNonNull(newObj.getSpec());

                if (Objects.equals(oldObj.getSpec().getQuota(), newObj.getSpec().getQuota())) {
                    yield new Result(false);
                }

                String eventName = generateEventName(request);
                V1ObjectReference regarding = getV1ObjectReference(request.getNewObj().orElseThrow());
                EventsV1Event event = new EventsV1EventBuilder()
                        .withNewMetadata()
                        .withName(eventName)
                        .endMetadata()
                        .withEventTime(generateEventTime())
                        .withReportingController(this.controllerName)
                        .withReportingInstance(this.controllerName)
                        .withType("Normal")
                        .withReason("ProjectQuotaSpecUpdated")
                        .withAction("ProjectQuotaSpecUpdated")
                        .withNote(String.format("Project quota spec updated: old: %s new: %s", oldObj.getSpec().getQuota(), newObj.getSpec().getQuota()))
                        .withRegarding(regarding)
                        .build();
                String eventNs = request.getNamespace().orElse("default");
                this.eventsV1Api
                        .createNamespacedEvent(eventNs, event)
                        .execute();
                yield new Result(false);
            }
            case DELETED -> {
                String eventName = generateEventName(request);
                V1ObjectReference regarding = getV1ObjectReference(request.getOldObj().orElseThrow());
                EventsV1Event event = new EventsV1EventBuilder()
                        .withNewMetadata()
                        .withName(eventName)
                        .endMetadata()
                        .withEventTime(generateEventTime())
                        .withReportingController(this.controllerName)
                        .withReportingInstance(this.controllerName)
                        .withType("Normal")
                        .withReason("ProjectDeleted")
                        .withAction("ProjectDeleted")
                        .withNote("Project deleted")
                        .withRegarding(regarding)
                        .build();
                String eventNs = request.getNamespace().orElse("default");
                this.eventsV1Api
                        .createNamespacedEvent(eventNs, event)
                        .execute();
                yield new Result(false);
            }
        };
    }

    private String generateEventName(CudEventPublishRequest<?> request) {
        return request.getName() + "." + UUID.randomUUID();
    }

    private OffsetDateTime generateEventTime() {
        return Instant.now().atOffset(ZoneOffset.UTC);
    }

    private V1ObjectReference getV1ObjectReference(KubernetesObject object) {
        return new V1ObjectReferenceBuilder()
                .withApiVersion(object.getApiVersion())
                .withKind(object.getKind())
                .withName(object.getMetadata().getName())
                .withNamespace(object.getMetadata().getNamespace())
                .withResourceVersion(object.getMetadata().getResourceVersion())
                .withUid(object.getMetadata().getUid())
                .build();
    }

}
