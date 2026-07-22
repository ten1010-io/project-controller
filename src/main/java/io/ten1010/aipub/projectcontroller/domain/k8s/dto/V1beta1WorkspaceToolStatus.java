package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1beta1WorkspaceToolStatus {

  @Nullable
  String type;
  @Nullable
  String address;

}
