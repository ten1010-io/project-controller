package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.openapi.models.V1Condition;
import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1OperationStatus {

  @Nullable
  Long observedGeneration;
  @Nullable
  Integer replicas;
  @Nullable
  Integer readyReplicas;
  @Nullable
  Integer updatedReplicas;
  @Nullable
  List<V1alpha1OperationStatusPort> ports;
  @Nullable
  List<V1Condition> conditions;

}
