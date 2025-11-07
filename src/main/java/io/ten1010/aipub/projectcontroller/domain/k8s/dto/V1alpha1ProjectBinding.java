package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1ProjectBinding {

  @Nullable
  private List<String> nodes;
  @Nullable
  private List<String> nodeGroups;
  @Nullable
  private List<String> imageHubs;

}
