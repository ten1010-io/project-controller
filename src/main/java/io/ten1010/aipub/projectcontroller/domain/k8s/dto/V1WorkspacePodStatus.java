package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1WorkspacePodStatus {

  @Nullable
  List<V1WorkspacePodState> pending;
  @Nullable
  List<V1WorkspacePodState> progressing;
  @Nullable
  List<V1WorkspacePodState> ready;

}
