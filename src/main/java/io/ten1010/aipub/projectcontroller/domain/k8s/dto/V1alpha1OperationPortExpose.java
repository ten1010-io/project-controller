package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1OperationPortExpose {

  @Nullable
  Boolean enabled;
  @Nullable
  String type;
  @Nullable
  Tcp tcp;
  @Nullable
  Http http;

  @Data
  public static class Tcp {

    @Nullable
    Integer port;

  }

  @Data
  public static class Http {

    @Nullable
    String host;
    @Nullable
    String path;
    @Nullable
    Boolean tls;

  }

}
