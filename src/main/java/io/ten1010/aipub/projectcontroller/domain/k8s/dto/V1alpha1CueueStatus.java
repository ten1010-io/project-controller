package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.openapi.models.V1Condition;
import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1CueueStatus {

  @Nullable
  private Long observedGeneration;
  @Nullable
  private List<String> nodes;
  @Nullable
  private List<V1alpha1CueueStatusPod> pods;
  @Nullable
  private List<V1Condition> conditions;

}
