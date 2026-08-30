package io.ten1010.aipub.projectcontroller.controller.cr;

import io.kubernetes.client.openapi.ApiException;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * ClusterVolume 자식(PVC/PV)의 라벨을 부분 갱신한다. 값이 null 인 키는 제거한다(JSON merge patch).
 * 리컨실러가 API 호출 없이 단위 테스트되도록 분리했다.
 */
public interface ClusterVolumeChildLabelPatcher {

  void patchPersistentVolumeClaimLabels(String namespace, String name,
      Map<String, @Nullable String> labels) throws ApiException;

  void patchPersistentVolumeLabels(String name, Map<String, @Nullable String> labels)
      throws ApiException;

}
