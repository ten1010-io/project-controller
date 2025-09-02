package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1FtpServerSpec {

    @Nullable
    V1FtpServer ssh;
    @Nullable
    V1PodTemplateSpec template;

}
