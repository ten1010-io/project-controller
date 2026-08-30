package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import lombok.Data;
import org.jspecify.annotations.Nullable;

/**
 * cluster-scoped ClusterVolume. 소유자 라벨 전파 경로는 metadata 만 읽으므로 spec/status 는
 * 정의하지 않는다(CRD 스키마를 옮겨오면 spec 변경마다 끌려다닌다).
 */
@Data
public class V1alpha1ClusterVolume implements KubernetesObject {

  @Nullable
  private String apiVersion;
  @Nullable
  private String kind;
  @Nullable
  private V1ObjectMeta metadata;

}
