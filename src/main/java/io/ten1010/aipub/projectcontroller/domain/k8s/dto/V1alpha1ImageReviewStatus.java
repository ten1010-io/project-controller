package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1ImageReviewStatus {

  @Nullable
  private List<V1alpha1ReviewRepository> repositories;
  @Nullable
  private List<V1alpha1ReviewArtifact> artifacts;

}
