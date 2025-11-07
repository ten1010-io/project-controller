package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1OperationAutoScale {

  @Nullable
  Integer maxReplicas;
  @Nullable
  Integer minReplicas;
  @Nullable
  String rps;
  @Nullable
  V1alpha1OperationScale scaleDown;
  @Nullable
  V1alpha1OperationScale scaleUp;

}
