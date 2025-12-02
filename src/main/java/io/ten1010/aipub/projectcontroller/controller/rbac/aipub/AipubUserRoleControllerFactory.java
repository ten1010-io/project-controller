package io.ten1010.aipub.projectcontroller.controller.rbac.aipub;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.V1Role;
import io.ten1010.aipub.projectcontroller.controller.ControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.DefaultControllerWatch;
import io.ten1010.aipub.projectcontroller.controller.watch.OnUpdateFilterFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.RequestBuilderFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1Workspace;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubJob;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubVolume;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Operation;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1SftpServer;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class AipubUserRoleControllerFactory implements ControllerFactory {

  private final SharedInformerFactory sharedInformerFactory;
  private final OnUpdateFilterFactory onUpdateFilterFactory;
  private final RequestBuilderFactory requestBuilderFactory;
  private final K8sApiProvider k8sApiProvider;
  private final ReconciliationService reconciliationService;

  public AipubUserRoleControllerFactory(
      SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService) {
    this.sharedInformerFactory = sharedInformerFactory;
    this.onUpdateFilterFactory = new OnUpdateFilterFactory();
    this.requestBuilderFactory = new RequestBuilderFactory(sharedInformerFactory);
    this.k8sApiProvider = k8sApiProvider;
    this.reconciliationService = reconciliationService;
  }

  @Override
  public Controller createController() {
    return ControllerBuilder.defaultBuilder(this.sharedInformerFactory)
        .withName("aipub-user-role-controller")
        .withWorkerCount(1)
        .withReadyFunc(
            this.sharedInformerFactory.getExistingSharedIndexInformer(V1Role.class)::hasSynced)
        .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
            V1alpha1Project.class)::hasSynced)
        .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
            V1alpha1AipubUser.class)::hasSynced)
        .withReadyFunc(
            this.sharedInformerFactory.getExistingSharedIndexInformer(V1Workspace.class)::hasSynced)
        .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
            V1alpha1AipubJob.class)::hasSynced)
        .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
            V1alpha1Operation.class)::hasSynced)
        .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
            V1alpha1AipubVolume.class)::hasSynced)
        .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
            V1alpha1SftpServer.class)::hasSynced)
        .watch(this::createRoleWatch)
        .watch(this::createProjectWatch)
        .watch(this::createAipubUserWatch)
        .watch(this::createWorkspaceWatch)
        .watch(this::createAipubJobWatch)
        .watch(this::createOperationWatch)
        .watch(this::createAipubVolumeWatch)
        .watch(this::createSftpServerWatch)
        .withReconciler(new AipubUserRoleReconciler(this.sharedInformerFactory, this.k8sApiProvider,
            this.reconciliationService))
        .build();
  }

  private ControllerWatch<V1Role> createRoleWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1Role> watch = new DefaultControllerWatch<>(workQueue, V1Role.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.aipubUserRoleFilter());

    return watch;
  }

  private ControllerWatch<V1alpha1Project> createProjectWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1Project> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1Project.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.alwaysTrueFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.projectToAipubUserRoles());

    return watch;
  }

  private ControllerWatch<V1alpha1AipubUser> createAipubUserWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1AipubUser> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1AipubUser.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.aipubUserStatusBoundProjectsFieldFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.aipubUserToAipubUserRoles());

    return watch;
  }

  private ControllerWatch<V1Workspace> createWorkspaceWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1Workspace> watch = new DefaultControllerWatch<>(workQueue,
        V1Workspace.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.alwaysTrueFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.workspaceToAipubUserRoles());
    return watch;
  }

  private ControllerWatch<V1alpha1AipubJob> createAipubJobWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1AipubJob> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1AipubJob.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.alwaysTrueFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.aipubJobToAipubUserRoles());
    return watch;
  }

  private ControllerWatch<V1alpha1Operation> createOperationWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1Operation> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1Operation.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.alwaysTrueFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.operationToAipubUserRoles());
    return watch;
  }

  private ControllerWatch<V1alpha1AipubVolume> createAipubVolumeWatch(
      WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1AipubVolume> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1AipubVolume.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.alwaysTrueFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.aipubVolumeToAipubUserRoles());
    return watch;
  }

  private ControllerWatch<V1alpha1SftpServer> createSftpServerWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1SftpServer> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1SftpServer.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.alwaysTrueFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.sftpServerToAipubUserRoles());
    return watch;
  }

}
