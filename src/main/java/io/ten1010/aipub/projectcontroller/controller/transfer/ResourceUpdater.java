package io.ten1010.aipub.projectcontroller.controller.transfer;

import io.kubernetes.client.openapi.ApiException;

@FunctionalInterface
public interface ResourceUpdater<T> {

  void update(T resource) throws ApiException;

}
