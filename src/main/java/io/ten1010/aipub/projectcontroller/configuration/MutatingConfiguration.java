package io.ten1010.aipub.projectcontroller.configuration;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.ten1010.aipub.projectcontroller.controller.workload.PodNodesResolver;
import io.ten1010.aipub.projectcontroller.controller.workload.WorkloadControllerNodesResolver;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ArtifactService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ImageHubService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.RepositoryService;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.SubjectResolver;
import io.ten1010.aipub.projectcontroller.mutating.AdmissionReviewController;
import io.ten1010.aipub.projectcontroller.mutating.RequestContentCachingFilter;
import io.ten1010.aipub.projectcontroller.mutating.service.AdmissionReviewService;
import io.ten1010.aipub.projectcontroller.mutating.service.CompositeReviewHandler;
import io.ten1010.aipub.projectcontroller.mutating.service.DeploymentReviewHandler;
import io.ten1010.aipub.projectcontroller.mutating.service.ImageReviewReviewHandler;
import io.ten1010.aipub.projectcontroller.mutating.service.NamespaceReviewHandler;
import io.ten1010.aipub.projectcontroller.mutating.service.PodReviewHandler;
import io.ten1010.aipub.projectcontroller.mutating.service.ProjectReviewHandler;
import io.ten1010.aipub.projectcontroller.mutating.service.ReviewHandler;
import io.ten1010.aipub.projectcontroller.mutating.service.UserInfoAnalyzer;
import io.ten1010.aipub.projectcontroller.mutating.service.ApiResourceDiscovery;
import io.ten1010.aipub.projectcontroller.mutating.service.UserLabelReviewHandler;
import io.ten1010.aipub.projectcontroller.mutating.service.UserLabelSynchronizer;
import io.ten1010.aipub.projectcontroller.mutating.service.UserOwnerReviewHandler;
import io.ten1010.aipub.projectcontroller.mutating.service.UserAuthorityReviewMutateHandler;
import io.ten1010.aipub.projectcontroller.mutating.service.UserAuthorityReviewValidateHandler;
import io.ten1010.aipub.projectcontroller.mutating.service.WorkloadLabelReviewHandler;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MutatingConfiguration {

  @Bean
  public FilterRegistrationBean<RequestContentCachingFilter> requestContentCachingFilter() {
    FilterRegistrationBean<RequestContentCachingFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.setFilter(new RequestContentCachingFilter());
    registrationBean.addUrlPatterns(AdmissionReviewController.PATH);

    return registrationBean;
  }

  @Bean
  @Qualifier("admissionReviewHandlers")
  public List<ReviewHandler> admissionReviewHandlers(
      PodReviewHandler podReviewHandler,
      DeploymentReviewHandler deploymentReviewHandler,
      NamespaceReviewHandler namespaceReviewHandler,
      ProjectReviewHandler projectReviewHandler,
      ImageReviewReviewHandler imageReviewReviewHandler) {
    return List.of(podReviewHandler, deploymentReviewHandler, namespaceReviewHandler,
        projectReviewHandler, imageReviewReviewHandler);
  }

  @Bean
  public AdmissionReviewService admissionReviewService(
      @Qualifier("admissionReviewHandlers") List<ReviewHandler> reviewHandlers) {
    return new AdmissionReviewService(new CompositeReviewHandler(reviewHandlers));
  }

  @Bean
  public PodReviewHandler podReviewHandler(
      PodNodesResolver podNodesResolver, SharedInformerFactory sharedInformerFactory,
      ReconciliationService reconciliationService) {
    return new PodReviewHandler(podNodesResolver, sharedInformerFactory, reconciliationService);
  }

  @Bean
  public DeploymentReviewHandler deploymentReviewHandler(
      WorkloadControllerNodesResolver workloadControllerNodesResolver,
      SharedInformerFactory sharedInformerFactory,
      ReconciliationService reconciliationService) {
    return new DeploymentReviewHandler(workloadControllerNodesResolver, sharedInformerFactory,
        reconciliationService);
  }

  @Bean
  public NamespaceReviewHandler namespaceReviewHandler(AipubProperties aipubProperties,
      SubjectResolver subjectResolver, SharedInformerFactory sharedInformerFactory) {
    return new NamespaceReviewHandler(aipubProperties, subjectResolver, sharedInformerFactory);
  }

  @Bean
  public ProjectReviewHandler projectReviewHandler(AipubProperties aipubProperties,
      SubjectResolver subjectResolver, SharedInformerFactory sharedInformerFactory) {
    return new ProjectReviewHandler(aipubProperties, subjectResolver, sharedInformerFactory);
  }

  @Bean
  public ImageReviewReviewHandler imageReviewReviewHandler(
      ImageHubService imageHubService, RepositoryService repositoryService,
      ArtifactService artifactService, SharedInformerFactory sharedInformerFactory) {
    return new ImageReviewReviewHandler(imageHubService, repositoryService, artifactService,
        sharedInformerFactory);
  }

  @Bean
  public ApiResourceDiscovery apiResourceDiscovery(ApiClient apiClient) {
    return new ApiResourceDiscovery(apiClient);
  }

  @Bean
  public UserInfoAnalyzer userInfoAnalyzer(SharedInformerFactory sharedInformerFactory) {
    return new UserInfoAnalyzer(sharedInformerFactory);
  }

  @Bean
  @Qualifier("aipubReviewHandlers")
  public List<ReviewHandler> aipubReviewHandlers(
      UserInfoAnalyzer userInfoAnalyzer, AipubProperties aipubProperties,
      ApiResourceDiscovery apiResourceDiscovery, ApiClient apiClient) {
    Set<String> exceptGvkSet = aipubProperties.getAddOwnerExceptGvkList().stream()
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toSet());
    UserOwnerReviewHandler userOwnerReviewHandler = new UserOwnerReviewHandler(
        userInfoAnalyzer, exceptGvkSet);
    UserLabelReviewHandler userLabelReviewHandler = new UserLabelReviewHandler(
        userInfoAnalyzer, apiResourceDiscovery, apiClient);
    return List.of(userOwnerReviewHandler, userLabelReviewHandler);
  }

  @Bean
  public UserLabelSynchronizer userLabelSynchronizer(
      ApiResourceDiscovery apiResourceDiscovery, ApiClient apiClient) {
    return new UserLabelSynchronizer(apiResourceDiscovery, apiClient);
  }

  @Bean
  public WorkloadLabelReviewHandler workloadLabelReviewHandler(
      ApiResourceDiscovery apiResourceDiscovery, ApiClient apiClient) {
    return new WorkloadLabelReviewHandler(apiResourceDiscovery, apiClient);
  }

  @Bean
  public UserAuthorityReviewMutateHandler userAuthorityReviewMutateHandler(
      UserInfoAnalyzer userInfoAnalyzer,
      ApiResourceDiscovery apiResourceDiscovery,
      SharedInformerFactory sharedInformerFactory) {
    return new UserAuthorityReviewMutateHandler(
        userInfoAnalyzer, apiResourceDiscovery, sharedInformerFactory);
  }

  @Bean
  public UserAuthorityReviewValidateHandler userAuthorityReviewValidateHandler() {
    return new UserAuthorityReviewValidateHandler();
  }

}
