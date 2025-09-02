package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1AipubJobPodState {

    @Nullable
    private Long failed;
    @Nullable
    private Long scheduled;
    @Nullable
    private Long succeeded;

}
