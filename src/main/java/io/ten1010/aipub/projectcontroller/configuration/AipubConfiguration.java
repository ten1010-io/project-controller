package io.ten1010.aipub.projectcontroller.configuration;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.ten1010.aipub.projectcontroller.controller.ImageRegistryRobotControllerFactory;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.AipubDockerConfigJsonResolver;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.AipubSubjectResolver;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ArtifactService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.DockerfileService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ImageHubService;
import io.ten1010.aipub.projectcontroller.controller.namespaced.ImageRegistrySecretRequestQueue;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ImageRegistryRobotSecretStore;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ImageRegistryRobotService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ImageRegistryRobotUsernameResolver;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.RepositoryService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.TemplateService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl.ArtifactServiceImpl;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl.DockerfileServiceImpl;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl.ImageHubServiceImpl;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl.ImageRegistryRobotServiceImpl;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl.ImageRegistryRobotUsernameResolverImpl;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl.RepositoryServiceImpl;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl.TemplateServiceImpl;
import io.ten1010.aipub.projectcontroller.domain.k8s.DefaultDockerConfigJsonResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.DefaultSubjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.DockerConfigJsonResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.SubjectResolver;
import io.ten1010.common.apiclient.ApiClient;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class AipubConfiguration {

  private static final String V1ALPHA1_BASE_PATH = "/api/v1alpha1";
  private static final String V1ALPHA2_BASE_PATH = "/api/v1alpha2";

  private final boolean aipubEnabled;
  @Nullable
  private final ApiClient aipubBackendClient;
  @Nullable
  private final ApiClient aipubBackendV1alpha2Client;
  @Nullable
  private final String harborExternalUrl;

  public AipubConfiguration(AipubProperties aipubProperties) {
    Objects.requireNonNull(aipubProperties.getEnabled());
    this.aipubEnabled = aipubProperties.getEnabled();

    if (this.aipubEnabled) {
      Objects.requireNonNull(aipubProperties.getServerUrl());
      Objects.requireNonNull(aipubProperties.getHarborExternalUrl());

      this.harborExternalUrl = aipubProperties.getHarborExternalUrl();
      this.aipubBackendClient = buildClient(aipubProperties, V1ALPHA1_BASE_PATH);
      this.aipubBackendV1alpha2Client = buildClient(aipubProperties, V1ALPHA2_BASE_PATH);
    } else {
      this.aipubBackendClient = null;
      this.aipubBackendV1alpha2Client = null;
      this.harborExternalUrl = null;
    }
  }

  // basePath 만 다른 두 클라이언트. 인증은 mTLS 클라이언트 인증서 하나로 통일돼 있다.
  private static ApiClient buildClient(AipubProperties aipubProperties, String basePath) {
    return AipubBackendClientFactory.create(
        aipubProperties.getServerUrl(), aipubProperties.isVerifyingSsl(),
        aipubProperties.getMtls(), basePath);
  }

  @Bean
  public SubjectResolver subjectResolver() {
    if (this.aipubEnabled) {
      return new AipubSubjectResolver();
    }
    return new DefaultSubjectResolver();
  }

  /**
   * robot 생성 응답의 평문 secret 을 {@code ImageRegistryRobotReconciler}(생성 측)에서
   * {@link AipubDockerConfigJsonResolver}(K8s Secret 반영 측)로 넘기기 위한 저장소.
   *
   * <p>두 reconciler 가 같은 인스턴스를 봐야 전달이 성립하므로 반드시 단일 빈으로 공유한다.
   */
  @Bean
  public ImageRegistryRobotSecretStore imageRegistryRobotSecretStore() {
    return new ImageRegistryRobotSecretStore();
  }

  @Bean
  public DockerConfigJsonResolver dockerConfigJsonResolver(
      ImageRegistryRobotSecretStore imageRegistryRobotSecretStore) {
    if (this.aipubEnabled) {
      Objects.requireNonNull(this.aipubBackendClient);
      Objects.requireNonNull(this.harborExternalUrl);
      ImageRegistryRobotService robotService = new ImageRegistryRobotServiceImpl(
          this.aipubBackendClient);
      ImageRegistryRobotUsernameResolver usernameResolver = new ImageRegistryRobotUsernameResolverImpl();
      return new AipubDockerConfigJsonResolver(this.harborExternalUrl, robotService, usernameResolver,
          imageRegistryRobotSecretStore);
    }
    return new DefaultDockerConfigJsonResolver();
  }

  @Bean
  public ImageHubService imageHubService() {
    if (this.aipubEnabled) {
      Objects.requireNonNull(this.aipubBackendClient);
      return new ImageHubServiceImpl(this.aipubBackendClient);
    }
    return (hubId) -> Optional.empty();
  }

  // ImageRegistryRobot 과 같은 v1alpha1 클라이언트를 재사용해 신규 설정값 없이 붙인다.
  @Bean
  public DockerfileService dockerfileService() {
    if (this.aipubEnabled) {
      Objects.requireNonNull(this.aipubBackendClient);
      return new DockerfileServiceImpl(this.aipubBackendClient);
    }
    return (projectName) -> {
    };
  }

  @Bean
  public TemplateService templateService() {
    if (this.aipubEnabled) {
      Objects.requireNonNull(this.aipubBackendV1alpha2Client);
      return new TemplateServiceImpl(this.aipubBackendV1alpha2Client);
    }
    return (projectName) -> {
    };
  }

  @Bean
  public RepositoryService repositoryService() {
    if (this.aipubEnabled) {
      Objects.requireNonNull(this.aipubBackendClient);
      return new RepositoryServiceImpl(this.aipubBackendClient);
    }
    return (namespacedId, options) -> List.of();
  }

  @Bean
  public ArtifactService artifactService() {
    if (this.aipubEnabled) {
      Objects.requireNonNull(this.aipubBackendClient);
      return new ArtifactServiceImpl(this.aipubBackendClient);
    }
    return (namespacedId, repositoryName, options) -> List.of();
  }

  @Bean
  public Controller imageRegistryRobotController(SharedInformerFactory sharedInformerFactory,
      ImageRegistryRobotSecretStore imageRegistryRobotSecretStore,
      ImageRegistrySecretRequestQueue imageRegistrySecretRequestQueue) {
    if (this.aipubEnabled) {
      Objects.requireNonNull(this.aipubBackendClient);
      ImageRegistryRobotService robotService = new ImageRegistryRobotServiceImpl(
          this.aipubBackendClient);
      ImageRegistryRobotUsernameResolver usernameResolver = new ImageRegistryRobotUsernameResolverImpl();
      return new ImageRegistryRobotControllerFactory(robotService, usernameResolver,
          imageRegistryRobotSecretStore, sharedInformerFactory, imageRegistrySecretRequestQueue)
          .createController();
    }
    return new Controller() {
      @Override
      public void shutdown() {

      }

      @Override
      public void run() {

      }
    };
  }

}
