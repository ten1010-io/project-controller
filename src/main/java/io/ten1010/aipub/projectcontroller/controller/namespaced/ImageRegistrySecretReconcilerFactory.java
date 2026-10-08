package io.ten1010.aipub.projectcontroller.controller.namespaced;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.models.V1Secret;
import io.ten1010.aipub.projectcontroller.controller.ControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.DefaultControllerWatch;
import io.ten1010.aipub.projectcontroller.controller.watch.OnUpdateFilterFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.RequestBuilderFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;

public class ImageRegistrySecretReconcilerFactory implements ControllerFactory {

  private final SharedInformerFactory sharedInformerFactory;
  private final OnUpdateFilterFactory onUpdateFilterFactory;
  private final RequestBuilderFactory requestBuilderFactory;
  private final K8sApiProvider k8sApiProvider;
  private final ReconciliationService reconciliationService;
  private final ImageRegistrySecretRequestQueue secretRequestQueue;

  public ImageRegistrySecretReconcilerFactory(
      SharedInformerFactory sharedInformerFactory,
      K8sApiProvider k8sApiProvider,
      ReconciliationService reconciliationService,
      ImageRegistrySecretRequestQueue secretRequestQueue) {
    this.sharedInformerFactory = sharedInformerFactory;
    this.onUpdateFilterFactory = new OnUpdateFilterFactory();
    this.requestBuilderFactory = new RequestBuilderFactory(sharedInformerFactory);
    this.k8sApiProvider = k8sApiProvider;
    this.reconciliationService = reconciliationService;
    this.secretRequestQueue = secretRequestQueue;
  }

  @Override
  public Controller createController() {
    return ControllerBuilder.defaultBuilder(this.sharedInformerFactory)
        .withName("image-registry-secret-controller")
        .withWorkerCount(1)
        .withReadyFunc(
            this.sharedInformerFactory.getExistingSharedIndexInformer(V1Secret.class)::hasSynced)
        .withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
            V1alpha1Project.class)::hasSynced)
        .watch(this::createSecretWatch)
        .watch(this::createProjectWatch)
        .withReconciler(
            new ImageRegistrySecretReconciler(this.sharedInformerFactory, this.k8sApiProvider,
                this.reconciliationService))
        .build();
  }

  private ControllerWatch<V1Secret> createSecretWatch(WorkQueue<Request> workQueue) {
    // robot 컨트롤러가 robot 생성 직후 이 큐에 직접 넣는다 — Harbor 오브젝트라 k8s 이벤트가 없다
    this.secretRequestQueue.bind(workQueue);
    DefaultControllerWatch<V1Secret> watch = new DefaultControllerWatch<>(workQueue,
        V1Secret.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.alwaysFalseFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.secretToImageRegistrySecrets());
    return watch;
  }

  private ControllerWatch<V1alpha1Project> createProjectWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1Project> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1Project.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.projectSpecBindingImageHubsFieldFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.projectToImageRegistrySecrets());
    return watch;
  }

}
