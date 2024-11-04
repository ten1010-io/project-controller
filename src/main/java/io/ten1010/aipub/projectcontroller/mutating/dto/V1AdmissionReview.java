package io.ten1010.aipub.projectcontroller.mutating.dto;

import io.kubernetes.client.common.KubernetesType;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1AdmissionReview implements KubernetesType {

    @Nullable
    private String apiVersion;
    @Nullable
    private String kind;
    @Nullable
    private V1AdmissionReviewRequest request;
    @Nullable
    private V1AdmissionReviewResponse response;

}
