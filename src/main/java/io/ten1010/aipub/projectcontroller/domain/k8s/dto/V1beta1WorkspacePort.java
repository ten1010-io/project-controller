package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1beta1WorkspacePort {

  @Nullable
  String name;
  @Nullable
  Integer port;
  @Nullable
  String appProtocol;
  @Nullable
  V1beta1WorkspacePortExpose expose;

}
