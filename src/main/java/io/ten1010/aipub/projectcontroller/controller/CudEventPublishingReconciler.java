package io.ten1010.aipub.projectcontroller.controller;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.ApiException;

public interface CudEventPublishingReconciler<ApiType extends KubernetesObject> {

    Result reconcile(CudEventPublishRequest<ApiType> request) throws ApiException;

}
