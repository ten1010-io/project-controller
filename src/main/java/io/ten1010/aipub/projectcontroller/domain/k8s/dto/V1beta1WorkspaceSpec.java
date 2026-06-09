package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1beta1WorkspaceSpec {

  @Nullable
  Integer replicas;
  @Nullable
  Boolean suspend;
  @Nullable
  V1beta1WorkspaceSsh ssh;
  @Nullable
  List<V1beta1WorkspaceTool> tools;
  @Nullable
  List<V1beta1WorkspacePort> ports;
  @Nullable
  V1PodTemplateSpec template;

}
