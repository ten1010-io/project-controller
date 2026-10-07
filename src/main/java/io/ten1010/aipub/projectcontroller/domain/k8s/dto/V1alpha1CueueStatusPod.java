package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import java.time.OffsetDateTime;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1CueueStatusPod {

  @Nullable
  private String name;
  @Nullable
  private String namespace;
  @Nullable
  private String uid;
  @Nullable
  private OffsetDateTime enqueueTime;

}
