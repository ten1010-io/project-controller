package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1SftpServerCondition {

  @Nullable
  String message;
  @Nullable
  String reason;
  @Nullable
  Boolean status;
  @Nullable
  String timestamp;

}
