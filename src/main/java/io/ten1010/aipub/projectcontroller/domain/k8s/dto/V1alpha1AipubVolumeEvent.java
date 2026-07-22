package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1AipubVolumeEvent {

  @Nullable
  private String created;
  @Nullable
  private String deleted;
  @Nullable
  private String updated;

}
