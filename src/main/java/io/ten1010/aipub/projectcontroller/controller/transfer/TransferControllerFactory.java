package io.ten1010.aipub.projectcontroller.controller.transfer;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.extended.controller.builder.ControllerBuilder;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.ten1010.aipub.projectcontroller.controller.ControllerFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.DefaultControllerWatch;
import io.ten1010.aipub.projectcontroller.domain.k8s.AnnotationConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import java.util.Map;

public class TransferControllerFactory<T extends KubernetesObject> implements ControllerFactory {

  private final SharedInformerFactory sharedInformerFactory;
  private final TransferService transferService;
  private final ResourceUpdater<T> resourceUpdater;
  private final Class<T> resourceClass;
  private final String controllerName;

  public TransferControllerFactory(
      SharedInformerFactory sharedInformerFactory,
      TransferService transferService,
      ResourceUpdater<T> resourceUpdater,
      Class<T> resourceClass,
      String controllerName) {
    this.sharedInformerFactory = sharedInformerFactory;
    this.transferService = transferService;
    this.resourceUpdater = resourceUpdater;
    this.resourceClass = resourceClass;
    this.controllerName = controllerName;
  }

  @Override
  public Controller createController() {
    return ControllerBuilder.defaultBuilder(this.sharedInformerFactory)
        .withName(this.controllerName)
        .withWorkerCount(1)
        .watch(workQueue -> {
          DefaultControllerWatch<T> watch = new DefaultControllerWatch<>(workQueue,
              this.resourceClass);
          watch.setOnUpdateFilter((oldObj, newObj) -> {
            String oldValue = getTransferAnnotation(oldObj);
            String newValue = getTransferAnnotation(newObj);
            return newValue != null && !newValue.equals(oldValue);
          });
          return watch;
        })
        .withReadyFunc(this.sharedInformerFactory
            .getExistingSharedIndexInformer(this.resourceClass)::hasSynced)
        .withReconciler(new TransferReconciler<>(
            this.sharedInformerFactory,
            this.resourceClass,
            this.transferService,
            this.resourceUpdater,
            this.resourceClass.getSimpleName()))
        .build();
  }

  private static String getTransferAnnotation(KubernetesObject obj) {
    Map<String, String> annotations = K8sObjectUtils.getAnnotations(obj);
    return annotations.get(AnnotationConstants.USER_TRANSFER_KEY);
  }

}
