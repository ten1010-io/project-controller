package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import io.kubernetes.client.common.KubernetesObject;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import java.util.Optional;

public abstract class ClusterVolumeUtils {

  /**
   * ClusterVolume 컨트롤러가 자식(복제/앵커 PVC·PV)에 붙이는 owner 라벨 값 = 부모 ClusterVolume 이름.
   * 편입 원본 PVC 는 {@code claimed-by} 라벨만 가지므로 여기서 빈 값이 나온다.
   */
  public static Optional<String> getOwnerClusterVolumeName(KubernetesObject object) {
    String value = K8sObjectUtils.getLabels(object).get(LabelConstants.CLUSTER_VOLUME_OWNER_KEY);
    if (value == null || value.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(value);
  }

}
