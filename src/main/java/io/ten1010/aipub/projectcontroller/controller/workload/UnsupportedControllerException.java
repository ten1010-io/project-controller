package io.ten1010.aipub.projectcontroller.controller.workload;

import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeKey;
import lombok.Getter;

@Getter
public class UnsupportedControllerException extends RuntimeException {

    private final K8sObjectTypeKey controllerTypeKey;

    public UnsupportedControllerException(K8sObjectTypeKey controllerTypeKey) {
        super(String.format("Unsupported Controller object type [%s]", controllerTypeKey));
        this.controllerTypeKey = controllerTypeKey;
    }

}
