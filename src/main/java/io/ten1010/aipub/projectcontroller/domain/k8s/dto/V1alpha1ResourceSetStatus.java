package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1ResourceSetStatus {

  @Nullable
  List<V1alpha1ResourceSetStatusNode> nodes;
  @Nullable
  private String category;
  @Nullable
  private Boolean ready;
  @Nullable
  private String resourceName;

}
