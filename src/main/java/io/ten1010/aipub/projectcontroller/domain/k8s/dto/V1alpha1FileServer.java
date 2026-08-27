package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import lombok.Data;
import org.jspecify.annotations.Nullable;

/**
 * RBAC 목적으로만 관측하는 타입이라 spec/status 는 매핑하지 않는다 — 소유권 판별에
 * 필요한 건 metadata(이름·네임스페이스·소유자 레이블)뿐이며, 매핑하지 않은 필드는
 * 역직렬화 시 무시된다.
 */
@Data
public class V1alpha1FileServer implements KubernetesObject {

  @Nullable
  private String apiVersion;
  @Nullable
  private String kind;
  @Nullable
  private V1ObjectMeta metadata;

}
