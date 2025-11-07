package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class V1WorkspacePodReadyCondition {

  @Nullable
  String message;
  @Nullable
  String reason;
  @Nullable
  Boolean status;
  @Nullable
  String timestamp;

}
