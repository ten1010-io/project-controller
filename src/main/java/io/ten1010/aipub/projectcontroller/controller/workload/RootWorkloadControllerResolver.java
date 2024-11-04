package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectType;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeKey;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;

import java.util.*;

public class RootWorkloadControllerResolver {

    private final KeyResolver keyResolver;
    private final Map<K8sObjectTypeKey, K8sObjectType<?>> supportedTypes;
    private final SharedInformerFactory sharedInformerFactory;

    public RootWorkloadControllerResolver(List<? extends K8sObjectType<?>> supportedTypes, SharedInformerFactory sharedInformerFactory) {
        this.keyResolver = new KeyResolver();
        this.supportedTypes = new HashMap<>();
        for (K8sObjectType type : supportedTypes) {
            this.supportedTypes.put(type.getTypeKey(), type);
        }
        this.sharedInformerFactory = sharedInformerFactory;
    }

    public Optional<KubernetesObject> getRootController(V1Pod pod) {
        Optional<V1OwnerReference> opt = K8sObjectUtils.findControllerOwnerReference(pod);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        KubernetesObject parentController = getObject(K8sObjectUtils.getNamespace(pod), opt.get());
        return Optional.of(getRootController(parentController));
    }

    private KubernetesObject getRootController(KubernetesObject controller) {
        Optional<V1OwnerReference> opt = K8sObjectUtils.findControllerOwnerReference(controller);
        if (opt.isEmpty()) {
            return controller;
        }
        KubernetesObject parentController = getObject(K8sObjectUtils.getNamespace(controller), opt.get());
        return getRootController(parentController);
    }

    private KubernetesObject getObject(String namespace, V1OwnerReference reference) {
        K8sObjectTypeKey typeKey = new K8sObjectTypeKey(reference.getApiVersion(), reference.getKind());
        K8sObjectType<?> type = this.supportedTypes.get(typeKey);
        if (type == null) {
            throw new UnsupportedControllerException(typeKey);
        }
        SharedIndexInformer<? extends KubernetesObject> informer = this.sharedInformerFactory.getExistingSharedIndexInformer(type.getObjClass());
        if (informer == null) {
            throw new UnsupportedControllerException(typeKey);
        }
        String objKey = this.keyResolver.resolveKey(namespace, reference.getName());
        KubernetesObject controllerObject = informer.getIndexer().getByKey(objKey);
        Objects.requireNonNull(controllerObject);

        return controllerObject;
    }

}
