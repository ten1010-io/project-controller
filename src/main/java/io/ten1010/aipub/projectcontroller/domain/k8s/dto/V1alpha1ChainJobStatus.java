package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.openapi.models.V1Condition;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1ChainJobStatus {

  @Nullable
  private Integer currentStepIndex;
  @Nullable
  private Long observedGeneration;
  @Nullable
  private OffsetDateTime startTime;
  @Nullable
  private OffsetDateTime completionTime;
  @Nullable
  private List<V1Condition> conditions;
  @Nullable
  private List<V1alpha1ChainJobStepStatus> steps;

}
