package io.ten1010.aipub.projectcontroller.domain.k8s;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1Job;
import java.util.Optional;
import javax.annotation.Nullable;

public class WorkloadResourceResolver {

  @Nullable
  public Optional<String> resolveResource(KubernetesObject workload) {
    String resourceName = switch (workload.getKind()) {
      case "Workspace" -> "workspaces";
      case "AIPubJob" -> "aipubjobs";
      case "Operation" -> "operations";
      case "AIPubVolume" -> "aipubvolumes";
      case "SFTPServer" -> "sftpservers";
      case "FtpServer" -> "ftpservers";
      case "Job" -> "jobs";
      default -> null;
    };
    if (resourceName != null) {
      return Optional.of(resourceName);
    }
    if (workload instanceof V1Job) {
      return Optional.of("jobs");
    }
    return Optional.empty();
  }

  public String resolveGroup(KubernetesObject workload) {
    if (workload instanceof V1Job) {
      return "batch";
    }
    if ("Job".equals(workload.getKind())) {
      return "batch";
    }
    return ProjectApiConstants.AIPUB_GROUP;
  }

}
