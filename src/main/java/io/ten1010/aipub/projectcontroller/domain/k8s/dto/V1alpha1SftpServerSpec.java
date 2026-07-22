package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1SftpServerSpec {

  @Nullable
  V1alpha1SftpServerSsh ssh;
  @Nullable
  V1PodTemplateSpec template;

}
