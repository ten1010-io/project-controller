package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1WorkspacePodState {

  @Nullable
  String creationTimestamp;
  @Nullable
  TerminationState lastTerminationState;
  @Nullable
  String message;
  @Nullable
  String name;
  @Nullable
  String nodeName;
  @Nullable
  String reason;
  @Nullable
  Integer restartCount;
  @Nullable
  Boolean terminating;
  @Nullable
  String timestamp;
  @Nullable
  String uid;

  @Data
  private static class TerminationState {

    @Nullable
    Integer exitCode;
    @Nullable
    String message;
    @Nullable
    String reason;
    @Nullable
    String timestamp;
  }

}
