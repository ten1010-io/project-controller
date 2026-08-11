package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import lombok.Data;
import org.jspecify.annotations.Nullable;

/**
 * 임의 CR 을 타입 정의 없이 메타데이터만으로 다루기 위한 부분 오브젝트.
 * spec/status 등 나머지 필드는 역직렬화 시 무시된다.
 */
@Data
public class V1PartialObject implements KubernetesObject {

  @Nullable
  private String apiVersion;
  @Nullable
  private String kind;
  @Nullable
  private V1ObjectMeta metadata;

}
