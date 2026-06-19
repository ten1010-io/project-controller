package io.ten1010.aipub.projectcontroller.domain.k8s;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1Job;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageBuild;
import java.util.Optional;
import javax.annotation.Nullable;

public class WorkloadResourceResolver {

  @Nullable
  public Optional<String> resolveResource(KubernetesObject workload) {
    String kind = workload.getKind();
    if (kind != null) {
      String resourceName = switch (kind) {
        case "Workspace" -> "workspaces";
        case "Operation" -> "operations";
        case "AIPubVolume" -> "aipubvolumes";
        case "SFTPServer" -> "sftpservers";
        case "FtpServer" -> "ftpservers";
        case "Job" -> "jobs";
        case "CronJob" -> "cronjobs";
        case "ChainJob" -> "chainjobs";
        case "ImageBuild" -> "imagebuilds";
        default -> null;
      };
      if (resourceName != null) {
        return Optional.of(resourceName);
      }
    }
    if (workload instanceof V1Job) {
      return Optional.of("jobs");
    }
    if (workload instanceof V1CronJob) {
      return Optional.of("cronjobs");
    }
    if (workload instanceof V1alpha1ImageBuild) {
      return Optional.of("imagebuilds");
    }
    return Optional.empty();
  }

  public String resolveGroup(KubernetesObject workload) {
    if (workload instanceof V1Job || workload instanceof V1CronJob) {
      return "batch";
    }
    String kind = workload.getKind();
    if ("Job".equals(kind) || "CronJob".equals(kind)) {
      return "batch";
    }
    return ProjectApiConstants.AIPUB_GROUP;
  }

}
