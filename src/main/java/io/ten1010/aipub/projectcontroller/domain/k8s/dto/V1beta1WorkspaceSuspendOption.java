package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1beta1WorkspaceSuspendOption {

  @Nullable
  Commit commit;

  @Data
  public static class Commit {

    @Nullable
    Boolean enabled;
    @Nullable
    String repository;
    @Nullable
    Boolean applyImage;

  }

}
