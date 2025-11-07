package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1OperationStatus {

  @Nullable
  List<V1alpha1OperationStatusAddress> addresses;
  @Nullable
  V1alpha1OperationCondition availableCondition;
  @Nullable
  String deployName;
  @Nullable
  String hpaName;
  @Nullable
  String ingressName;
  @Nullable
  String serviceName;
  @Nullable
  V1alpha1OperationPodStatus pods;

}
