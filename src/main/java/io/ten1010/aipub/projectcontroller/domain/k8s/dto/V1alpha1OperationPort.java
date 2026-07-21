package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1OperationPort {

  @Nullable
  String appProtocol;
  @Nullable
  V1alpha1OperationPortExpose expose;
  @Nullable
  String name;
  @Nullable
  Integer port;

}
