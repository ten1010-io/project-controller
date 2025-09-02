package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
public class V1WorkspaceSpec {

    @Nullable
    List<V1WorkspacePort> ports;
    @Nullable
    Integer replicas;
    @Nullable
    V1WorkspaceSsh ssh;
    @Nullable
    Boolean suspend;
    @Nullable
    V1PodTemplateSpec template;

}
