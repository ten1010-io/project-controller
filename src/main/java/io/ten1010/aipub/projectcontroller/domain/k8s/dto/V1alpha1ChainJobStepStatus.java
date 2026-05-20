package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import java.time.OffsetDateTime;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1ChainJobStepStatus {

  @Nullable
  private Integer index;
  @Nullable
  private String name;
  @Nullable
  private String jobName;
  @Nullable
  private V1alpha1ChainJobStepResult result;
  @Nullable
  private OffsetDateTime startTime;
  @Nullable
  private OffsetDateTime completionTime;

}
