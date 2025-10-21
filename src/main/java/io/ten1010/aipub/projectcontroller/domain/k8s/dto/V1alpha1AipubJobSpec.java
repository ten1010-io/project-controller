package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
public class V1alpha1AipubJobSpec {

    @Nullable
    private Long activeDeadlineSeconds;
    @Nullable
    private V1alpha1AipubJobCron cron;
    @Nullable
    private Long parallelism;
    @Nullable
    private Boolean suspend;
    @Nullable
    Object template;

}
