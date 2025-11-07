package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1OperationPodStatus {

  @Nullable
  List<V1alpha1OperationPodState> pending;
  @Nullable
  List<V1alpha1OperationPodState> progressing;
  @Nullable
  List<V1alpha1OperationPodState> ready;

}
