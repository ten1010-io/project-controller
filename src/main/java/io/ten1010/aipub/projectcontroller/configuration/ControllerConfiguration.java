package io.ten1010.aipub.projectcontroller.configuration;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.ControllerManager;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.builder.ControllerManagerBuilder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.ten1010.aipub.projectcontroller.controller.CudEventPublishingControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.ProjectCudEventPublishingReconciler;
import io.ten1010.aipub.projectcontroller.controller.cluster.NamespaceControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.cluster.NodeControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.cr.AipubUserControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.cr.ImageHubControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.cr.NodeGroupControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.cr.ProjectControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.namespaced.ImagePullSecretReconcilerFactory;
import io.ten1010.aipub.projectcontroller.controller.namespaced.ResourceQuotaControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.rbac.aipub.AipubUserClusterRoleBindingControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.rbac.aipub.AipubUserClusterRoleControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.rbac.aipub.AipubUserRoleBindingControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.rbac.aipub.AipubUserRoleControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.rbac.member.ClusterRoleBindingControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.rbac.member.ClusterRoleControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.rbac.member.RoleBindingControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.rbac.member.RoleControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.workload.CompositeWorkloadControllerNodesResolver;
import io.ten1010.aipub.projectcontroller.controller.workload.CronJobInformerRegistrar;
import io.ten1010.aipub.projectcontroller.controller.workload.CronJobWorkloadControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.workload.DaemonSetInformerRegistrar;
import io.ten1010.aipub.projectcontroller.controller.workload.DaemonSetWorkloadControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.workload.DeploymentInformerRegistrar;
import io.ten1010.aipub.projectcontroller.controller.workload.DeploymentWorkloadControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.workload.JobInformerRegistrar;
import io.ten1010.aipub.projectcontroller.controller.workload.JobWorkloadControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.workload.PodControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.workload.PodNodesResolver;
import io.ten1010.aipub.projectcontroller.controller.workload.ReplicaSetInformerRegistrar;
import io.ten1010.aipub.projectcontroller.controller.workload.ReplicaSetWorkloadControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.workload.RootWorkloadControllerResolver;
import io.ten1010.aipub.projectcontroller.controller.workload.StatefulSetInformerRegistrar;
import io.ten1010.aipub.projectcontroller.controller.workload.StatefulSetWorkloadControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.workload.WorkloadControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.workload.WorkloadControllerNodesResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectType;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ControllerConfiguration {

  @Bean
  public ControllerManager controllerManager(
      SharedInformerFactory sharedInformerFactory, List<Controller> controllers,
      List<WorkloadControllerFactory<?>> workloadControllerFactories) {
    System.out.println(controllers);
    ControllerManagerBuilder builder = ControllerBuilder.controllerManagerBuilder(
        sharedInformerFactory);
    controllers.forEach(builder::addController);
    workloadControllerFactories.forEach(f -> builder.addController(f.createController()));
    ControllerManager controllerManager = builder.build();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(controllerManager);

    return controllerManager;
  }

  @Bean
  public Controller projectController(SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService,
      AipubProperties aipubProperties) {
    return new ProjectControllerFactory(sharedInformerFactory, k8sApiProvider,
        reconciliationService, aipubProperties.getReservedNamespace())
        .createController();
  }

  @Bean
  public Controller projectCudEventPublishingController(
      SharedInformerFactory sharedInformerFactory, K8sApiProvider k8sApiProvider) {
    String controllerName = "project-cud-event-publishing-controller";
    ProjectCudEventPublishingReconciler reconciler = new ProjectCudEventPublishingReconciler(
        controllerName, k8sApiProvider);
    return new CudEventPublishingControllerFactory<>(
        controllerName, V1alpha1Project.class, reconciler, sharedInformerFactory)
        .createController();
  }

  @Bean
  public Controller aipubUserController(SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    return new AipubUserControllerFactory(sharedInformerFactory, k8sApiProvider,
        reconciliationService)
        .createController();
  }

  @Bean
  public Controller aipubUserClusterRoleController(SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    return new AipubUserClusterRoleControllerFactory(sharedInformerFactory, k8sApiProvider,
        reconciliationService)
        .createController();
  }

  @Bean
  public Controller aipubUserClusterRoleBindingController(
      SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    return new AipubUserClusterRoleBindingControllerFactory(sharedInformerFactory, k8sApiProvider,
        reconciliationService)
        .createController();
  }

  @Bean
  public Controller aipubUserRoleController(SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    return new AipubUserRoleControllerFactory(sharedInformerFactory, k8sApiProvider,
        reconciliationService)
        .createController();
  }

  @Bean
  public Controller aipubUserRoleBindingController(SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    return new AipubUserRoleBindingControllerFactory(sharedInformerFactory, k8sApiProvider,
        reconciliationService)
        .createController();
  }

  @Bean
  public Controller nodeGroupController(SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    return new NodeGroupControllerFactory(sharedInformerFactory, k8sApiProvider,
        reconciliationService)
        .createController();
  }

  @Bean
  public Controller imageHubController(SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    return new ImageHubControllerFactory(sharedInformerFactory, k8sApiProvider,
        reconciliationService)
        .createController();
  }

  @Bean
  public Controller namespaceController(SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    return new NamespaceControllerFactory(sharedInformerFactory, k8sApiProvider,
        reconciliationService)
        .createController();
  }

  @Bean
  public Controller nodeController(SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    return new NodeControllerFactory(sharedInformerFactory, k8sApiProvider, reconciliationService)
        .createController();
  }

  @Bean
  public Controller clusterRoleController(SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    return new ClusterRoleControllerFactory(sharedInformerFactory, k8sApiProvider,
        reconciliationService)
        .createController();
  }

  @Bean
  public Controller clusterRoleBindingController(SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    return new ClusterRoleBindingControllerFactory(sharedInformerFactory, k8sApiProvider,
        reconciliationService)
        .createController();
  }

  @Bean
  public Controller roleController(SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    return new RoleControllerFactory(sharedInformerFactory, k8sApiProvider, reconciliationService)
        .createController();
  }

  @Bean
  public Controller roleBindingController(SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    return new RoleBindingControllerFactory(sharedInformerFactory, k8sApiProvider,
        reconciliationService)
        .createController();
  }

  @Bean
  public Controller resourceQuotaController(SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    return new ResourceQuotaControllerFactory(sharedInformerFactory, k8sApiProvider,
        reconciliationService)
        .createController();
  }

  @Bean
  public Controller imagePullSecretReconcilerFactory(SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    return new ImagePullSecretReconcilerFactory(sharedInformerFactory, k8sApiProvider,
        reconciliationService)
        .createController();
  }

  @Bean
  public Controller podController(SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      PodNodesResolver podNodesResolver) {
    return new PodControllerFactory(sharedInformerFactory, k8sApiProvider, podNodesResolver)
        .createController();
  }

  @Bean
  public RootWorkloadControllerResolver rootControllerResolver(
      SharedInformerFactory sharedInformerFactory,
      List<WorkloadControllerFactory<?>> workloadControllerFactories) {
    List<? extends K8sObjectType<?>> supportedTypes = workloadControllerFactories.stream()
        .map(WorkloadControllerFactory::getObjectType)
        .toList();
    return new RootWorkloadControllerResolver(supportedTypes, sharedInformerFactory);
  }

  @Bean
  public CompositeWorkloadControllerNodesResolver compositeWorkloadControllerNodesResolver(
      List<WorkloadControllerFactory<?>> workloadControllerFactories) {
    Map<Class<? extends KubernetesObject>, WorkloadControllerNodesResolver> resolvers = new HashMap<>();
    for (WorkloadControllerFactory<?> factory : workloadControllerFactories) {
      resolvers.put(factory.getObjectType().objClass(), factory.getWorkloadNodesResolver());
    }
    return new CompositeWorkloadControllerNodesResolver(resolvers);
  }

  @Bean
  public PodNodesResolver podNodesResolver(
      RootWorkloadControllerResolver rootWorkloadControllerResolver,
      CompositeWorkloadControllerNodesResolver workloadControllerNodesResolver,
      SharedInformerFactory sharedInformerFactory) {
    return new PodNodesResolver(rootWorkloadControllerResolver, workloadControllerNodesResolver,
        sharedInformerFactory);
  }

  @Bean
  public CronJobInformerRegistrar cronJobInformerRegistrar(K8sApiProvider k8sApiProvider) {
    return new CronJobInformerRegistrar(k8sApiProvider);
  }

  @Bean
  public CronJobWorkloadControllerFactory cronJobWorkloadControllerFactory(
      SharedInformerFactory sharedInformerFactory,
      ReconciliationService reconciliationService,
      K8sApiProvider k8sApiProvider) {
    return new CronJobWorkloadControllerFactory(sharedInformerFactory, reconciliationService,
        k8sApiProvider);
  }

  @Bean
  public DaemonSetInformerRegistrar daemonSetInformerRegistrar(K8sApiProvider k8sApiProvider) {
    return new DaemonSetInformerRegistrar(k8sApiProvider);
  }

  @Bean
  public DaemonSetWorkloadControllerFactory daemonSetWorkloadControllerFactory(
      SharedInformerFactory sharedInformerFactory,
      ReconciliationService reconciliationService,
      K8sApiProvider k8sApiProvider) {
    return new DaemonSetWorkloadControllerFactory(sharedInformerFactory, reconciliationService,
        k8sApiProvider);
  }

  @Bean
  public DeploymentInformerRegistrar deploymentInformerRegistrar(K8sApiProvider k8sApiProvider) {
    return new DeploymentInformerRegistrar(k8sApiProvider);
  }

  @Bean
  public DeploymentWorkloadControllerFactory deploymentWorkloadControllerFactory(
      SharedInformerFactory sharedInformerFactory,
      ReconciliationService reconciliationService,
      K8sApiProvider k8sApiProvider) {
    return new DeploymentWorkloadControllerFactory(sharedInformerFactory, reconciliationService,
        k8sApiProvider);
  }

  @Bean
  public JobInformerRegistrar jobInformerRegistrar(K8sApiProvider k8sApiProvider) {
    return new JobInformerRegistrar(k8sApiProvider);
  }

  @Bean
  public JobWorkloadControllerFactory jobWorkloadControllerFactory(
      SharedInformerFactory sharedInformerFactory,
      ReconciliationService reconciliationService,
      K8sApiProvider k8sApiProvider) {
    return new JobWorkloadControllerFactory(sharedInformerFactory, reconciliationService,
        k8sApiProvider);
  }

  @Bean
  public ReplicaSetInformerRegistrar replicaSetInformerRegistrar(K8sApiProvider k8sApiProvider) {
    return new ReplicaSetInformerRegistrar(k8sApiProvider);
  }

  @Bean
  public ReplicaSetWorkloadControllerFactory replicaSetWorkloadControllerFactory(
      SharedInformerFactory sharedInformerFactory,
      ReconciliationService reconciliationService,
      K8sApiProvider k8sApiProvider) {
    return new ReplicaSetWorkloadControllerFactory(sharedInformerFactory, reconciliationService,
        k8sApiProvider);
  }

  @Bean
  public StatefulSetInformerRegistrar statefulSetInformerRegistrar(K8sApiProvider k8sApiProvider) {
    return new StatefulSetInformerRegistrar(k8sApiProvider);
  }

  @Bean
  public StatefulSetWorkloadControllerFactory statefulSetWorkloadControllerFactory(
      SharedInformerFactory sharedInformerFactory,
      ReconciliationService reconciliationService,
      K8sApiProvider k8sApiProvider) {
    return new StatefulSetWorkloadControllerFactory(sharedInformerFactory, reconciliationService,
        k8sApiProvider);
  }

}
