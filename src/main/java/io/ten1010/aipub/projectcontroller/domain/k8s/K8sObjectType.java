package io.ten1010.aipub.projectcontroller.domain.k8s;

import io.kubernetes.client.common.KubernetesObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class K8sObjectType<T extends KubernetesObject> {

    public static <O extends KubernetesObject> K8sObjectType<O> of(KubernetesObject object, Class<O> objClass) {
        return new K8sObjectType<>(K8sObjectTypeKey.of(object), objClass);
    }

    private final K8sObjectTypeKey typeKey;
    private final Class<T> objClass;

}
