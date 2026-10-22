package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1OperationStatusPort {

  @Nullable
  String name;
  @Nullable
  Integer port;
  @Nullable
  String type;
  @Nullable
  String internalAddress;
  @Nullable
  String externalAddress;

}
