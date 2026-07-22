package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import java.util.Map;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1beta1WorkspaceTool {

  @Nullable
  String type;
  @Nullable
  Map<String, Object> config;

}
