package io.ten1010.aipub.projectcontroller.controller.cr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.util.PatchUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * core/v1 PVC·PV 에 JSON merge patch 로 {@code metadata.labels} 만 갱신한다. merge patch 라
 * 명시한 키만 바뀌고(null 은 제거) 다른 라벨·spec 은 건드리지 않는다 — ClusterVolume 컨트롤러가
 * 관리하는 owner 라벨과 충돌하지 않는다.
 */
public class CoreV1ClusterVolumeChildLabelPatcher implements ClusterVolumeChildLabelPatcher {

  private final ApiClient apiClient;
  private final CoreV1Api coreV1Api;
  private final ObjectMapper mapper;

  public CoreV1ClusterVolumeChildLabelPatcher(ApiClient apiClient) {
    this.apiClient = apiClient;
    this.coreV1Api = new CoreV1Api(apiClient);
    this.mapper = new ObjectMapperFactory().createObjectMapper();
  }

  @Override
  public void patchPersistentVolumeClaimLabels(String namespace, String name,
      Map<String, @Nullable String> labels) throws ApiException {
    V1Patch patch = buildLabelsMergePatch(labels);
    PatchUtils.patch(
        V1PersistentVolumeClaim.class,
        () -> this.coreV1Api.patchNamespacedPersistentVolumeClaim(name, namespace, patch)
            .buildCall(null),
        V1Patch.PATCH_FORMAT_JSON_MERGE_PATCH,
        this.apiClient);
  }

  @Override
  public void patchPersistentVolumeLabels(String name, Map<String, @Nullable String> labels)
      throws ApiException {
    V1Patch patch = buildLabelsMergePatch(labels);
    PatchUtils.patch(
        V1PersistentVolume.class,
        () -> this.coreV1Api.patchPersistentVolume(name, patch).buildCall(null),
        V1Patch.PATCH_FORMAT_JSON_MERGE_PATCH,
        this.apiClient);
  }

  private V1Patch buildLabelsMergePatch(Map<String, @Nullable String> labels) {
    ObjectNode root = this.mapper.createObjectNode();
    ObjectNode labelsNode = root.putObject("metadata").putObject("labels");
    labels.forEach((key, value) -> {
      if (value == null) {
        labelsNode.putNull(key);
      } else {
        labelsNode.put(key, value);
      }
    });
    try {
      return new V1Patch(this.mapper.writeValueAsString(root));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

}
