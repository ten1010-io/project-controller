package io.ten1010.aipub.projectcontroller.controller;

import io.kubernetes.client.common.KubernetesObject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CudEventPublishRequest<ApiType extends KubernetesObject> {

    public static <T extends KubernetesObject> CudEventPublishRequest<T> buildCreatedEventRequest(@Nullable String namespace, String name, T created) {
        return new CudEventPublishRequest<>(CudEventEnum.CREATED, namespace, name, null, created);
    }

    public static <T extends KubernetesObject> CudEventPublishRequest<T> buildUpdatedEventRequest(@Nullable String namespace, String name, T oldObj, T newObj) {
        return new CudEventPublishRequest<>(CudEventEnum.UPDATED, namespace, name, oldObj, newObj);
    }

    public static <T extends KubernetesObject> CudEventPublishRequest<T> buildDeletedEventRequest(@Nullable String namespace, String name, T deleted) {
        return new CudEventPublishRequest<>(CudEventEnum.DELETED, namespace, name, deleted, null);
    }

    @Getter
    private CudEventEnum eventEnum;
    @Nullable
    private String namespace;
    @Getter
    private String name;
    @Nullable
    private ApiType oldObj;
    @Nullable
    private ApiType newObj;

    public Optional<String> getNamespace() {
        return Optional.ofNullable(this.namespace);
    }

    public Optional<ApiType> getOldObj() {
        return Optional.ofNullable(this.oldObj);
    }

    public Optional<ApiType> getNewObj() {
        return Optional.ofNullable(this.newObj);
    }

}
