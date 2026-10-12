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
import io.ten1010.aipub.projectcontroller.controller.namespaced.ImageRegistrySecretReconcilerFactory;
import io.ten1010.aipub.projectcontroller.controller.namespaced.ImageRegistrySecretRequestQueue;
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
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.DockerfileService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.TemplateService;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectType;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.informer.owned.OwnedObjectInformerManager;
import io.ten1010.aipub.projectcontroller.leaderelection.LeaderElectionRunner;
import io.ten1010.aipub.projectcontroller.leaderelection.LeadershipState;
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
  public LeadershipState leadershipState() {
    return new LeadershipState();
  }

  @Bean
  public LeaderElectionRunner leaderElectionRunner(K8sApiProvider k8sApiProvider,
      LeadershipState leadershipState) {
    return new LeaderElectionRunner(k8sApiProvider.getApiClient(), leadershipState);
  }

  @Bean
  public ControllerManager controllerManager(
      SharedInformerFactory sharedInformerFactory, List<Controller> controllers,
      List<WorkloadControllerFactory<?>> workloadControllerFactories,
      OwnedObjectInformerManager ownedObjectInformerManager,
      LeaderElectionRunner leaderElectionRunner) {
    ControllerManagerBuilder builder = ControllerBuilder.controllerManagerBuilder(
        sharedInformerFactory);
    controllers.forEach(builder::addController);
    List<? extends K8sObjectType<?>> supportedTypes = resolveSupportedWorkloadTypes(
        workloadControllerFactories);
    workloadControllerFactories.forEach(
        f -> builder.addController(f.createController(supportedTypes)));
    ControllerManager controllerManager = builder.build();

    // 모든 컨트롤러 빌드가 끝나 개인 Role 워크큐가 매니저에 등록된 뒤에
    // 소유권 인포머를 시작해야 초기 onAdd 이벤트가 유실되지 않는다
    ownedObjectInformerManager.start();

    // ⚠️ 인포머는 리더가 아닌 파드에서도 돌아야 한다. 어드미션 웹훅 핸들러가 이 캐시를 읽는데
    // failurePolicy 가 Fail 이라, 캐시가 비면 파드·네임스페이스 생성이 거부된다.
    // ControllerManager.run() 도 같은 호출을 하지만 이미 시작된 인포머는 건너뛴다.
    sharedInformerFactory.startAllRegisteredInformers();

    // 워크큐는 여기서 통째로 리더에 묶인다. 스스로 스케줄을 잡고 도는 작업은 이 바깥이라
    // 레플리카마다 돌므로, 쓰는 쪽은 각자 LeadershipState 를 보고 건너뛴다
    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(() -> leaderElectionRunner.runWhenLeader(controllerManager));

    return controllerManager;
  }

  @Bean
  public Controller projectController(SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService,
      AipubProperties aipubProperties,
      DockerfileService dockerfileService,
      TemplateService templateService) {
    return new ProjectControllerFactory(sharedInformerFactory, k8sApiProvider,
        reconciliationService, aipubProperties.getReservedNamespace(), dockerfileService,
        templateService)
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
      ReconciliationService reconciliationService,
      OwnedObjectInformerManager ownedObjectInformerManager) {
    return new AipubUserRoleControllerFactory(sharedInformerFactory, k8sApiProvider,
        reconciliationService, ownedObjectInformerManager)
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
  public ImageRegistrySecretRequestQueue imageRegistrySecretRequestQueue() {
    return new ImageRegistrySecretRequestQueue();
  }

  @Bean
  public Controller imageRegistrySecretReconcilerFactory(
      SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService,
      ImageRegistrySecretRequestQueue imageRegistrySecretRequestQueue) {
    return new ImageRegistrySecretReconcilerFactory(sharedInformerFactory, k8sApiProvider,
        reconciliationService, imageRegistrySecretRequestQueue)
        .createController();
  }

  @Bean
  public Controller podController(SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      PodNodesResolver podNodesResolver,
      NamespaceAllowlistResolver namespaceAllowlistResolver) {
    return new PodControllerFactory(sharedInformerFactory, k8sApiProvider, podNodesResolver,
        namespaceAllowlistResolver)
        .createController();
  }

  /**
   * reconcile 대상 워크로드 타입 전체. root 워크로드 판정 기준이므로 이 값을 쓰는 두 지점
   * (워크로드 리컨실러의 owner kind 대조와 {@link RootWorkloadControllerResolver})이 반드시 같은
   * 값을 봐야 한다. 각자 따로 수집하면 root 정의가 갈라진다.
   *
   * <p>가시성이 package-private 인 것은 테스트 전용이다(ControllerConfigurationWiringTest). 외부에
   * 공개할 API 가 아니다.
   */
  static List<? extends K8sObjectType<?>> resolveSupportedWorkloadTypes(
      List<WorkloadControllerFactory<?>> workloadControllerFactories) {
    return workloadControllerFactories.stream()
        .map(WorkloadControllerFactory::getObjectType)
        .toList();
  }

  @Bean
  public RootWorkloadControllerResolver rootControllerResolver(
      SharedInformerFactory sharedInformerFactory,
      List<WorkloadControllerFactory<?>> workloadControllerFactories) {
    return new RootWorkloadControllerResolver(
        resolveSupportedWorkloadTypes(workloadControllerFactories), sharedInformerFactory);
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
