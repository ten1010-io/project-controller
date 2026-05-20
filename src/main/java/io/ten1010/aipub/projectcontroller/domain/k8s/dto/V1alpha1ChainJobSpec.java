package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1ChainJobSpec {

  @Nullable
  private Long activeDeadlineSeconds;
  @Nullable
  private Boolean suspend;
  @Nullable
  private Integer ttlSecondsAfterFinished;
  @Nullable
  private List<V1alpha1ChainJobStep> steps;

}
