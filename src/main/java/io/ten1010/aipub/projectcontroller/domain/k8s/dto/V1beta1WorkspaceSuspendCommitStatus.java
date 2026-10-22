package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1beta1WorkspaceSuspendCommitStatus {

  @Nullable
  String image;
  @Nullable
  String prevImage;
  @Nullable
  String appliedImage;

}
