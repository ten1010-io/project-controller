package io.ten1010.aipub.projectcontroller.controller;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.DefaultControllerWatch;
import io.ten1010.aipub.projectcontroller.controller.watch.OnUpdateFilterFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.RequestBuilderFactory;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ImageRegistryRobotService;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.ImageRegistryRobotUsernameResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ImageHub;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;

public class ImageRegistryRobotControllerFactory implements ControllerFactory {

  private final ImageRegistryRobotService robotService;
  private final ImageRegistryRobotUsernameResolver usernameResolver;
  private final SharedInformerFactory sharedInformerFactory;
  private final OnUpdateFilterFactory onUpdateFilterFactory;
  private final RequestBuilderFactory requestBuilderFactory;

  public ImageRegistryRobotControllerFactory(
      ImageRegistryRobotService robotService,
      ImageRegistryRobotUsernameResolver usernameResolver,
      SharedInformerFactory sharedInformerFactory) {
    this.robotService = robotService;
    this.usernameResolver = usernameResolver;
    this.sharedInformerFactory = sharedInformerFactory;
    this.onUpdateFilterFactory = new OnUpdateFilterFactory();
    this.requestBuilderFactory = new RequestBuilderFactory(sharedInformerFactory);
  }

  @Override
  public Controller createController() {
    return ControllerBuilder.defaultBuilder(this.sharedInformerFactory)
        .withName("image-registry-robot-controller")
        .withWorkerCount(1)
        .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
            V1alpha1Project.class)::hasSynced)
        .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
            V1alpha1ImageHub.class)::hasSynced)
        .watch(this::createProjectWatch)
        .watch(this::createImageHubWatch)
        .withReconciler(new ImageRegistryRobotReconciler(this.robotService, this.usernameResolver,
            this.sharedInformerFactory))
        .build();
  }

  private ControllerWatch<V1alpha1Project> createProjectWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1Project> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1Project.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.projectSpecBindingImageHubsFieldFilter());
    return watch;
  }

  private ControllerWatch<V1alpha1ImageHub> createImageHubWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1ImageHub> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1ImageHub.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.imageHubSpecFieldFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.imageHubToBoundProjects());
    return watch;
  }

}
