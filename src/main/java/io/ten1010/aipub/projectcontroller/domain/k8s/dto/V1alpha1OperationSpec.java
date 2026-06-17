package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1OperationSpec {

  @Nullable
  V1alpha1OperationAutoScale autoScaling;
  @Nullable
  List<V1alpha1OperationPort> ports;
  @Nullable
  Integer replicas;
  @Nullable
  Integer revisionHistoryLimit;
  @Nullable
  Boolean suspend;
  @Nullable
  V1PodTemplateSpec template;

}
