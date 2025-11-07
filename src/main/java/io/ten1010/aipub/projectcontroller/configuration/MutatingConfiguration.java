package io.ten1010.aipub.projectcontroller.configuration;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.ten1010.aipub.projectcontroller.controller.workload.PodNodesResolver;
import io.ten1010.aipub.projectcontroller.controller.workload.WorkloadControllerNodesResolver;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ArtifactService;
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
import java.util.List;
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
  public AdmissionReviewService admissionReviewService(List<ReviewHandler> reviewHandlers) {
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
      RepositoryService repositoryService, ArtifactService artifactService,
      SharedInformerFactory sharedInformerFactory) {
    return new ImageReviewReviewHandler(repositoryService, artifactService, sharedInformerFactory);
  }

}
