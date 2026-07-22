package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.openapi.models.V1Condition;
import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1beta1WorkspaceStatus {

  @Nullable
  Long observedGeneration;
  @Nullable
  Integer replicas;
  @Nullable
  Integer readyReplicas;
  @Nullable
  List<V1Condition> conditions;
  @Nullable
  V1beta1WorkspaceSshStatus ssh;
  @Nullable
  List<V1beta1WorkspaceToolStatus> tools;
  @Nullable
  List<V1beta1WorkspacePortStatus> ports;

}
