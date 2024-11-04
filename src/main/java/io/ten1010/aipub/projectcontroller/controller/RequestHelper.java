package io.ten1010.aipub.projectcontroller.controller;

import io.kubernetes.client.extended.controller.reconciler.Request;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import lombok.AllArgsConstructor;

import java.util.Objects;

@AllArgsConstructor
public class RequestHelper {

    private final KeyResolver keyResolver;

    public String resolveKey(Request request) {
        Objects.requireNonNull(request.getName());
        if (request.getNamespace() == null) {
            return this.keyResolver.resolveKey(request.getName());
        }
        return this.keyResolver.resolveKey(request.getNamespace(), request.getName());
    }

}
