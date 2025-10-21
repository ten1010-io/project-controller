package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1AipubJobPodState {

    @Nullable
    private String message;
    @Nullable
    private String reason;
    @Nullable
    private String timestamp;
    @Nullable
    private String type;

}
