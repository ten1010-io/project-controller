package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1ResourceSetSpec {

  @Nullable
  List<V1alpha1ResourceSetSpecNode> nodes;
  @Nullable
  private String cpu;
  @Nullable
  private String gpu;
  @Nullable
  private String memory;

}
