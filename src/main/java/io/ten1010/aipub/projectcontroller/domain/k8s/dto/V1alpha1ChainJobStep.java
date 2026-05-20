package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.openapi.models.V1JobTemplateSpec;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1ChainJobStep {

  @Nullable
  private String name;
  @Nullable
  private V1alpha1ChainJobOnFailure onFailure;
  @Nullable
  private V1JobTemplateSpec job;

}
