package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.ControllerWatch;
import io.kubernetes.client.extended.controller.reconciler.Request;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.extended.workqueue.WorkQueue;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobBuilder;
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

public class JobWorkloadControllerFactory extends WorkloadControllerFactory {

  private final BatchV1Api batchV1Api;
  private final OnUpdateFilterFactory onUpdateFilterFactory;
  private final RequestBuilderFactory requestBuilderFactory;

  public JobWorkloadControllerFactory(
      SharedInformerFactory sharedInformerFactory,
      ReconciliationService reconciliationService,
      K8sApiProvider k8sApiProvider) {
    super(sharedInformerFactory, reconciliationService);
    this.batchV1Api = new BatchV1Api(k8sApiProvider.getApiClient());
    this.onUpdateFilterFactory = new OnUpdateFilterFactory();
    this.requestBuilderFactory = new RequestBuilderFactory(sharedInformerFactory);
  }

  @Override
  public K8sObjectType getObjectType() {
    return K8sObjectTypeConstants.JOB_V1;
  }

  @Override
  public WorkloadControllerNodesResolver getWorkloadNodesResolver() {
    return new DefaultWorkloadControllerNodesResolver(this.sharedInformerFactory);
  }

  @Override
  protected void configureControllerName() {
    this.builder.withName("job-controller");
  }

  @Override
  protected void configureReadyFunc() {
    this.builder.withReadyFunc(
        this.sharedInformerFactory.getExistingSharedIndexInformer(V1Job.class)::hasSynced);
    this.builder.withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
        V1alpha1Project.class)::hasSynced);
    this.builder.withReadyFunc(this.sharedInformerFactory.getExistingSharedIndexInformer(
        V1alpha1NodeGroup.class)::hasSynced);
    this.builder.withReadyFunc(
        this.sharedInformerFactory.getExistingSharedIndexInformer(V1Node.class)::hasSynced);
  }

  @Override
  protected void configureWatch() {
    this.builder.watch(this::createJobWatch);
    this.builder.watch(this::createProjectWatch);
    this.builder.watch(this::createNodeGroupWatch);
    this.builder.watch(this::createNodeWatch);
  }

  @Override
  protected Function<KubernetesObject, V1PodTemplateSpec> getPodTemplateSpecResolver() {
    return object -> {
      if (!(object instanceof V1Job job)) {
        throw new IllegalArgumentException();
      }
      return WorkloadUtils.getPodTemplateSpec(job);
    };
  }

  @Override
  protected ControllerObjectReconciler getObjectReconciler() {
    return (KubernetesObject controller,
        List<V1Toleration> reconciledTolerations,
        List<V1NodeSelectorTerm> reconciledSelectorTerms,
        List<V1LocalObjectReference> reconciledImagePullSecrets) -> {
      if (!(controller instanceof V1Job job)) {
        throw new IllegalArgumentException();
      }
      return this.reconcileController(job, reconciledTolerations, reconciledSelectorTerms,
          reconciledImagePullSecrets);
    };
  }

  private Result reconcileController(
      V1Job controller,
      List<V1Toleration> reconciledTolerations,
      List<V1NodeSelectorTerm> reconciledSelectorTerms,
      List<V1LocalObjectReference> reconciledImagePullSecrets) throws ApiException {
    if (Set.copyOf(WorkloadUtils.getTolerations(controller))
        .equals(Set.copyOf(reconciledTolerations)) &&
        Set.copyOf(WorkloadUtils.getNodeSelectorTerms(controller))
            .equals(Set.copyOf(reconciledSelectorTerms)) &&
        Set.copyOf(WorkloadUtils.getImageRegistrySecrets(controller))
            .equals(Set.copyOf(reconciledImagePullSecrets))) {
      return new Result(false);
    }
    V1Job edited;
    if (reconciledSelectorTerms.isEmpty()) {
      edited = new V1JobBuilder(controller)
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
          .build();
    } else {
      edited = new V1JobBuilder(controller)
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
          .build();
    }
    updateJob(K8sObjectUtils.getNamespace(controller), K8sObjectUtils.getName(controller), edited);
    return new Result(false);
  }

  private void updateJob(String namespace, String objName, V1Job job) throws ApiException {
    this.batchV1Api
        .replaceNamespacedJob(objName, namespace, job)
        .execute();
  }

  private ControllerWatch<V1Job> createJobWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1Job> watch = new DefaultControllerWatch<>(workQueue, V1Job.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.jobFilter());
    return watch;
  }

  private ControllerWatch<V1alpha1Project> createProjectWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1Project> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1Project.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.projectSpecBindingFieldFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.projectToNamespacedObjects(V1Job.class));
    return watch;
  }

  private ControllerWatch<V1alpha1NodeGroup> createNodeGroupWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1alpha1NodeGroup> watch = new DefaultControllerWatch<>(workQueue,
        V1alpha1NodeGroup.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.nodeGroupSpecFieldFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.nodeGroupToNamespacedObjects(V1Job.class));
    return watch;
  }

  private ControllerWatch<V1Node> createNodeWatch(WorkQueue<Request> workQueue) {
    DefaultControllerWatch<V1Node> watch = new DefaultControllerWatch<>(workQueue, V1Node.class);
    watch.setOnUpdateFilter(this.onUpdateFilterFactory.nodeFilter());
    watch.setRequestBuilder(this.requestBuilderFactory.nodeToNamespacedObjects(V1Job.class));
    return watch;
  }

}
