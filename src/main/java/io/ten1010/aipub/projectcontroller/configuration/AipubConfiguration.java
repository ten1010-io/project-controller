package io.ten1010.aipub.projectcontroller.configuration;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.ten1010.aipub.projectcontroller.controller.ImageRegistryRobotControllerFactory;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.*;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl.*;
import io.ten1010.aipub.projectcontroller.domain.k8s.DefaultDockerConfigJsonResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.DefaultSubjectResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.DockerConfigJsonResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.SubjectResolver;
import io.ten1010.common.apiclient.ApiClient;
import io.ten1010.common.apiclient.Authentication;
import io.ten1010.common.apiclient.HttpBasicAuthentication;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Objects;

@Configuration
@Getter
public class AipubConfiguration {

    private final boolean aipubEnabled;
    @Nullable
    private final ApiClient aipubBackendClient;

    public AipubConfiguration(AipubProperties aipubProperties) {
        Objects.requireNonNull(aipubProperties.getEnabled());
        this.aipubEnabled = aipubProperties.getEnabled();

        if (this.aipubEnabled) {
            Objects.requireNonNull(aipubProperties.getServerUrl());
            Objects.requireNonNull(aipubProperties.getVerifyingSsl());
            Objects.requireNonNull(aipubProperties.getUsername());
            Objects.requireNonNull(aipubProperties.getPassword());

            ApiClient client = new ApiClient();
            client.setBasePath(aipubProperties.getServerUrl() + "/api/v1alpha1");
            client.setVerifyingSsl(aipubProperties.getVerifyingSsl());
            Authentication authentication = new HttpBasicAuthentication(aipubProperties.getUsername(), aipubProperties.getPassword());
            client.setAuthentication(authentication);

            this.aipubBackendClient = client;
        } else {
            this.aipubBackendClient = null;
        }
    }

    @Bean
    public SubjectResolver subjectResolver() {
        if (this.aipubEnabled) {
            return new AipubSubjectResolver();
        }
        return new DefaultSubjectResolver();
    }

    @Bean
    public DockerConfigJsonResolver dockerConfigJsonResolver() {
        if (this.aipubEnabled) {
            Objects.requireNonNull(this.aipubBackendClient);
            ImageRegistryInfoService infoService = new ImageRegistryInfoServiceImpl(this.aipubBackendClient);
            ImageRegistryRobotService robotService = new ImageRegistryRobotServiceImpl(this.aipubBackendClient);
            ImageRegistryRobotUsernameResolver usernameResolver = new ImageRegistryRobotUsernameResolverImpl();
            return new AipubDockerConfigJsonResolver(infoService, robotService, usernameResolver);
        }
        return new DefaultDockerConfigJsonResolver();
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
    public Controller imageRegistryRobotController(SharedInformerFactory sharedInformerFactory) {
        if (this.aipubEnabled) {
            Objects.requireNonNull(this.aipubBackendClient);
            ImageRegistryRobotService robotService = new ImageRegistryRobotServiceImpl(this.aipubBackendClient);
            ImageRegistryRobotUsernameResolver usernameResolver = new ImageRegistryRobotUsernameResolverImpl();
            return new ImageRegistryRobotControllerFactory(robotService, usernameResolver, sharedInformerFactory)
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
