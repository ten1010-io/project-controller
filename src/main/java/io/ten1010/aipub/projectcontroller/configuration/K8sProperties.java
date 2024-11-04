package io.ten1010.aipub.projectcontroller.configuration;

import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.k8s")
@Data
public class K8sProperties {

    public enum KubeConfigMode {

        IN_CLUSTER, FILE

    }

    @Data
    public static class KubeConfigProperty {

        @Nullable
        private KubeConfigMode mode;
        @Nullable
        private String kubeConfigPath;

    }


    @Nullable
    private Boolean verifySsl;
    @Nullable
    private KubeConfigProperty kubeConfig;

}
