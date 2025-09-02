package io.ten1010.aipub.projectcontroller.domain.k8s;

import io.kubernetes.client.common.KubernetesObject;

import javax.annotation.Nullable;
import java.util.Optional;

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
            default -> null;
        };
        return Optional.ofNullable(resourceName);
    }

}
