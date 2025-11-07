package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1CronJobBuilder;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1NodeSelectorTerm;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.aipub.projectcontroller.controller.watch.DefaultControllerWatch;
import io.ten1010.aipub.projectcontroller.controller.watch.OnUpdateFilterFactory;
import io.ten1010.aipub.projectcontroller.controller.watch.RequestBuilderFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectType;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1NodeGroup;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.WorkloadUtils;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class CronJobWorkloadControllerFactory extends WorkloadControllerFactory<V1CronJob> {

  private final BatchV1Api batchV1Api;
  private final OnUpdateFilterFactory onUpdateFilterFactory;
  private final RequestBuilderFactory requestBuilderFactory;

  public CronJobWorkloadControllerFactory(
      SharedInformerFactory sharedInformerFactory,
      ReconciliationService reconciliationService,
      K8sApiProvider k8sApiProvider) {
    super(sharedInformerFactory, reconciliationService);
    this.batchV1Api = new BatchV1Api(k8sApiProvider.getApiClient());
    this.onUpdateFilterFactory = new OnUpdateFilterFactory();
    this.requestBuilderFactory = new RequestBuilderFactory(sharedInformerFactory);
  }

  @Override
  public K8sObjectType<V1CronJob> getObjectType() {
    return K8sObjectTypeConstants.CRON_JOB_V1;
  }

  @Override
  public WorkloadControllerNodesResolver getWorkloadNodesResolver() {
    return new DefaultWorkloadControllerNodesResolver(this.sharedInformerFactory);
  }

  @Override
  protected void configureControllerName() {
    this.builder.withName("cron-job-controller");
  }

  @Override
  protected void configureReadyFunc() {
    this.builder.withReadyFunc(
        this.sharedInformerFactory.getExistingSharedIndexInformer(V1CronJob.class)::hasSynced);
    this.builder.withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
        V1alpha1Project.class)::hasSynced);
    this.builder.withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
        V1alpha1NodeGroup.class)::hasSynced);
    this.builder.withReadyFunc(
        this.sharedInformerFactory.getExistingSharedIndexInformer(V1Node.class)::hasSynced);
  }

  @Override
  protected void configureWatch() {
    this.builder.watch(this::createCronJobWatch);
    this.builder.watch(this::createProjectWatch);
    this.builder.watch(this::createNodeGroupWatch);
    this.builder.watch(this::createNodeWatch);
  }

  @Override
  protected Function<KubernetesObject, V1PodTemplateSpec> getPodTemplateSpecResolver() {
    return object -> {
      if (!(object instanceof V1CronJob cronJob)) {
        throw new IllegalArgumentException();
      }
      return WorkloadUtils.getPodTemplateSpec(cronJob);
    };
  }

  @Override
  protected ControllerObjectReconciler getObjectReconciler() {
    return (KubernetesObject controller,
        List<V1Toleration> reconciledTolerations,
        List<V1NodeSelectorTerm> reconciledSelectorTerms,
        List<V1LocalObjectReference> reconciledImagePullSecrets) -> {
      if (!(controller instanceof V1CronJob cronJob)) {
        throw new IllegalArgumentException();
      }
      return this.reconcileController(cronJob, reconciledTolerations, reconciledSelectorTerms,
          reconciledImagePullSecrets);
    };
  }

  private Result reconcileController(
      V1CronJob controller,
      List<V1Toleration> reconciledTolerations,
      List<V1NodeSelectorTerm> reconciledSelectorTerms,
      List<V1LocalObjectReference> reconciledImagePullSecrets) throws ApiException {
    if (Set.copyOf(WorkloadUtils.getTolerations(controller))
        .equals(Set.copyOf(reconciledTolerations)) &&
        Set.copyOf(WorkloadUtils.getNodeSelectorTerms(controller))
            .equals(Set.copyOf(reconciledSelectorTerms)) &&
        Set.copyOf(WorkloadUtils.getImagePullSecrets(controller))
            .equals(Set.copyOf(reconciledImagePullSecrets))) {
      return new Result(false);
    }
    V1CronJob edited;
    if (reconciledTolerations.isEmpty()) {
      edited = new V1CronJobBuilder(controller)
          .editSpec()
          .editJobTemplate()
          .editSpec()
          .editTemplate()
          .editSpec()
          .withTolerations(reconciledTolerations)
          .editAffinity()
          .editNodeAffinity()
          .withRequiredDuringSchedulingIgnoredDuringExecution(null)
          .endNodeAffinity()
          .endAffinity()
          .withImagePullSecrets(reconciledImagePullSecrets)
          .endSpec()
          .endTemplate()
          .endSpec()
          .endJobTemplate()
          .endSpec()
          .build();
    } else {
      edited = new V1CronJobBuilder(controller)
          .editSpec()
          .editJobTemplate()
          .editSpec()
          .editTemplate()
          .editSpec()
          .withTolerations(reconciledTolerations)
          .editAffinity()
          .editNodeAffinity()
          .editRequiredDuringSchedulingIgnoredDuringExecution()
          .withNodeSelectorTerms(reconciledSelectorTerms)
          .endRequiredDuringSchedulingIgnoredDuringExecution()
          .endNodeAffinity()
          .endAffinity()
          .withImagePullSecrets(reconciledImagePullSecrets)
          .endSpec()
          .endTemplate()
          .endSpec()
          .endJobTemplate()
          .endSpec()
          .build();
    }
    updateCronJob(K8sObjectUtils.getNamespace(controller), K8sObjectUtils.getName(controller),
        edited);
    return new Result(false);
  }

  private void updateCronJob(String namespace, String objName, V1CronJob cronJob)
      throws ApiException {
    this.batchV1Api
        .replaceNamespacedCronJob(objName, namespace, cronJob)
        .execute();
  }

  private ControllerWatch<V1CronJob> createCronJobWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1CronJob> watch = new DefaultControllerWatch<>(workQueue,
        V1CronJob.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.cronJobFilter());
    return watch;
  }

  private ControllerWatch<V1alpha1Project> createProjectWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1Project> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1Project.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.projectSpecBindingFieldFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.projectToNamespacedObjects(V1CronJob.class));
    return watch;
  }

  private ControllerWatch<V1alpha1NodeGroup> createNodeGroupWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1NodeGroup> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1NodeGroup.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.nodeGroupSpecFieldFilter());
    watch.setRequestBuilder(
        this.requestBuilderFactory.nodeGroupToNamespacedObjects(V1CronJob.class));
    return watch;
  }

  private ControllerWatch<V1Node> createNodeWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1Node> watch = new DefaultControllerWatch<>(workQueue, V1Node.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.nodeFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.nodeToNamespacedObjects(V1CronJob.class));
    return watch;
  }

}
