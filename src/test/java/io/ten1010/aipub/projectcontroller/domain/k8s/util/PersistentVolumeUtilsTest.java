package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.kubernetes.client.openapi.models.V1ObjectReference;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeStatus;
import org.junit.jupiter.api.Test;

class PersistentVolumeUtilsTest {

  private static V1PersistentVolume persistentVolume(String claimRefNamespace, String phase) {
    V1PersistentVolume pv = new V1PersistentVolume();
    if (claimRefNamespace != null) {
      V1ObjectReference claimRef = new V1ObjectReference();
      claimRef.setNamespace(claimRefNamespace);
      claimRef.setName("some-pvc");
      V1PersistentVolumeSpec spec = new V1PersistentVolumeSpec();
      spec.setClaimRef(claimRef);
      pv.setSpec(spec);
    }
    if (phase != null) {
      V1PersistentVolumeStatus status = new V1PersistentVolumeStatus();
      status.setPhase(phase);
      pv.setStatus(status);
    }
    return pv;
  }

  @Test
  void isUnclaimed_withoutClaimRefAndAvailable_isTrue() {
    assertThat(PersistentVolumeUtils.isUnclaimed(persistentVolume(null, "Available"))).isTrue();
  }

  @Test
  void isUnclaimed_withClaimRefButAvailable_isFalse() {
    // 동적 프로비저닝 PV 는 claimRef 를 물고 태어나 Bound 직전 Available 을 스친다.
    assertThat(PersistentVolumeUtils.isUnclaimed(persistentVolume("proj1", "Available"))).isFalse();
  }

  @Test
  void isUnclaimed_withoutClaimRefButNotAvailable_isFalse() {
    assertThat(PersistentVolumeUtils.isUnclaimed(persistentVolume(null, "Pending"))).isFalse();
    assertThat(PersistentVolumeUtils.isUnclaimed(persistentVolume(null, null))).isFalse();
  }

  @Test
  void isUnclaimed_whenBoundOrReleased_isFalse() {
    assertThat(PersistentVolumeUtils.isUnclaimed(persistentVolume("proj1", "Bound"))).isFalse();
    assertThat(PersistentVolumeUtils.isUnclaimed(persistentVolume("proj1", "Released"))).isFalse();
  }

  @Test
  void getClaimRefNamespace_withoutSpecOrClaimRef_isNull() {
    assertThat(PersistentVolumeUtils.getClaimRefNamespace(persistentVolume(null, "Available")))
        .isNull();
    assertThat(PersistentVolumeUtils.getClaimRefNamespace(persistentVolume("proj1", "Bound")))
        .isEqualTo("proj1");
  }

}
