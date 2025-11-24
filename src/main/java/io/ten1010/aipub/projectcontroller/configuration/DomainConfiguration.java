package io.ten1010.aipub.projectcontroller.configuration;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import io.ten1010.aipub.projectcontroller.domain.k8s.DockerConfigJsonResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.SubjectResolver;
import java.io.FileReader;
import java.io.IOException;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DomainConfiguration {

  @Bean
  public ApiClient apiClient(K8sProperties k8sProperties) throws IOException {
    Objects.requireNonNull(k8sProperties.getKubeConfig());
    Objects.requireNonNull(k8sProperties.getKubeConfig().getMode());
    Objects.requireNonNull(k8sProperties.getVerifySsl());

    K8sProperties.KubeConfigProperty kubeConfigProperty = k8sProperties.getKubeConfig();
    return switch (kubeConfigProperty.getMode()) {
      case IN_CLUSTER -> ClientBuilder
          .cluster()
          .setVerifyingSsl(k8sProperties.getVerifySsl())
          .build();
      case FILE -> {
        Objects.requireNonNull(kubeConfigProperty.getKubeConfigPath());

        FileReader configFileReader = new FileReader(kubeConfigProperty.getKubeConfigPath());
        yield ClientBuilder
            .kubeconfig(KubeConfig.loadKubeConfig(configFileReader))
            .setVerifyingSsl(k8sProperties.getVerifySsl())
            .build();
      }
    };
  }

  @Bean
  public K8sApiProvider k8sApiProvider(ApiClient apiClient) {
    return new K8sApiProvider(apiClient);
  }

  @Bean
  public ReconciliationService reconciliationService(SubjectResolver subjectResolver,
      DockerConfigJsonResolver dockerConfigJsonResolver, AipubProperties aipubProperties) {
    return new ReconciliationService(subjectResolver, dockerConfigJsonResolver,
        aipubProperties.getReservedNamespace());
  }

}
