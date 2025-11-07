package io.ten1010.aipub.projectcontroller.domain.k8s;

import io.kubernetes.client.common.KubernetesObject;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public record K8sObjectType<T extends KubernetesObject>(K8sObjectTypeKey typeKey,
                                                        Class<T> objClass) {

  public static <O extends KubernetesObject> K8sObjectType<O> of(KubernetesObject object,
      Class<O> objClass) {
    return new K8sObjectType<>(K8sObjectTypeKey.of(object), objClass);
  }

}
