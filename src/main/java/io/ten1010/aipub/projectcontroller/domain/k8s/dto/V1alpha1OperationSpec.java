package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
public class V1alpha1OperationSpec {

    @Nullable
    V1alpha1OperationAutoScale autoScaling;
    @Nullable
    List<V1alpha1OperationPort> ports;
    @Nullable
    Integer replicas;
    @Nullable
    Integer reversionHistoryLimit;
    @Nullable
    V1PodTemplateSpec template;

}
