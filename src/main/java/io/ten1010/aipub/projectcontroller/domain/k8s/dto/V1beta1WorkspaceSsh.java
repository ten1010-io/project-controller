package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.openapi.models.V1ConfigMapKeySelector;
import io.kubernetes.client.openapi.models.V1SecretKeySelector;
import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1beta1WorkspaceSsh {

  @Nullable
  Integer port;
  @Nullable
  String authorizedKeys;
  @Nullable
  List<AuthorizedKeysFrom> authorizedKeysFrom;
  @Nullable
  Password password;

  @Data
  public static class AuthorizedKeysFrom {

    @Nullable
    V1ConfigMapKeySelector configMapKeyRef;
    @Nullable
    V1SecretKeySelector secretKeyRef;

  }

  @Data
  public static class Password {

    @Nullable
    Boolean enabled;
    @Nullable
    V1SecretKeySelector secretRef;

  }

}
