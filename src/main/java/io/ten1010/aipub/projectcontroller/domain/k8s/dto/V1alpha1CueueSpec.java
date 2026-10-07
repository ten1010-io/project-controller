package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.openapi.models.V1LabelSelector;
import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1CueueSpec {

  /** 기본값은 apiserver(CRD default)가 채운다 — 여기서 보정하지 않는다. */
  @Nullable
  private Integer priority;
  @Nullable
  private V1alpha1CueuePolicy policy;
  @Nullable
  private List<V1alpha1CueueSpecPod> pods;
  @Nullable
  private List<String> nodes;
  @Nullable
  private V1LabelSelector nodeSelector;

}
