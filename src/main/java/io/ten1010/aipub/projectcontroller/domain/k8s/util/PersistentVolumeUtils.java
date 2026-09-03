package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeStatus;
import org.jspecify.annotations.Nullable;

public abstract class PersistentVolumeUtils {

  private static final String PHASE_AVAILABLE = "Available";

  @Nullable
  public static V1ObjectReference getClaimRef(V1PersistentVolume object) {
    V1PersistentVolumeSpec spec = object.getSpec();
    return spec == null ? null : spec.getClaimRef();
  }

  @Nullable
  public static String getClaimRefNamespace(V1PersistentVolume object) {
    V1ObjectReference claimRef = getClaimRef(object);
    return claimRef == null ? null : claimRef.getNamespace();
  }

  @Nullable
  public static String getPhase(V1PersistentVolume object) {
    V1PersistentVolumeStatus status = object.getStatus();
    return status == null ? null : status.getPhase();
  }

  /**
   * 아직 어떤 PVC 에도 발행되지 않은 PV. claimRef 를 함께 보는 이유는 동적 프로비저닝 PV 가
   * claimRef 를 물고 태어나 Bound 직전 Available 을 스치기 때문 — phase 만 보면 그 찰나에
   * 전체 프로젝트 ClusterRole 이 갱신된다.
   */
  public static boolean isUnclaimed(V1PersistentVolume object) {
    return getClaimRef(object) == null && PHASE_AVAILABLE.equals(getPhase(object));
  }

}
