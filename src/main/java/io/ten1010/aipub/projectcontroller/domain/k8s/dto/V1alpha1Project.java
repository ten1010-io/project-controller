package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1Project implements KubernetesObject {

    @Nullable
    private String apiVersion;
    @Nullable
    private String kind;
    @Nullable
    private V1ObjectMeta metadata;
    @Nullable
    private V1alpha1ProjectSpec spec;
    @Nullable
    private V1alpha1ProjectStatus status;

}
