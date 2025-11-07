package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1AipubJobInfo {

  @Nullable
  private String creationTimestamp;
  @Nullable
  private Long exitCode;
  @Nullable
  private String finishedAt;
  @Nullable
  private String message;
  @Nullable
  private String name;
  @Nullable
  private String nodeName;
  @Nullable
  private String reason;
  @Nullable
  private String startedAt;
  @Nullable
  private Boolean terminating;
  @Nullable
  private String timestamp;
  @Nullable
  private String uid;

}
