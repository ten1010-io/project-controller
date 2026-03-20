package io.ten1010.aipub.projectcontroller.controller.transfer;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.ten1010.aipub.projectcontroller.domain.k8s.AnnotationConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sApiProvider;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.ProjectApiConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUserList;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.informer.IndexerConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransferService {

  private static final Logger logger = LoggerFactory.getLogger(TransferService.class);

  private final GenericKubernetesApi<V1alpha1AipubUser, V1alpha1AipubUserList> aipubUserApi;
  private final Indexer<V1Deployment> deploymentIndexer;
  private final Indexer<V1DaemonSet> daemonSetIndexer;
  private final Indexer<V1StatefulSet> statefulSetIndexer;
  private final Indexer<V1ReplicaSet> replicaSetIndexer;
  private final Indexer<V1CronJob> cronJobIndexer;
  private final Indexer<V1Job> jobIndexer;
  private final Indexer<V1Pod> podIndexer;
  private final AppsV1Api appsV1Api;
  private final BatchV1Api batchV1Api;
  private final CoreV1Api coreV1Api;

  public TransferService(
      K8sApiProvider k8sApiProvider,
      SharedInformerFactory sharedInformerFactory) {
    this.aipubUserApi = k8sApiProvider.getAipubUserApi();
    this.deploymentIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1Deployment.class).getIndexer();
    this.daemonSetIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1DaemonSet.class).getIndexer();
    this.statefulSetIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1StatefulSet.class).getIndexer();
    this.replicaSetIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1ReplicaSet.class).getIndexer();
    this.cronJobIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1CronJob.class).getIndexer();
    this.jobIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1Job.class).getIndexer();
    this.podIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1Pod.class).getIndexer();
    this.appsV1Api = new AppsV1Api(k8sApiProvider.getApiClient());
    this.batchV1Api = new BatchV1Api(k8sApiProvider.getApiClient());
    this.coreV1Api = new CoreV1Api(k8sApiProvider.getApiClient());
  }

  @Nullable
  public V1alpha1AipubUser getAipubUser(String userName) {
    KubernetesApiResponse<V1alpha1AipubUser> response = this.aipubUserApi.get(userName);
    if (!response.isSuccess()) {
      return null;
    }
    return response.getObject();
  }

  public void applyTransferMetadata(KubernetesObject object, V1alpha1AipubUser targetUser) {
    V1ObjectMeta metadata = Objects.requireNonNull(object.getMetadata());
    Objects.requireNonNull(targetUser.getMetadata());
    Objects.requireNonNull(targetUser.getSpec());
    Objects.requireNonNull(targetUser.getSpec().getId());

    replaceAipubUserOwnerReference(metadata, targetUser);
    setUserLabels(metadata, targetUser);
    removeTransferAnnotation(metadata);
  }

  public void applyTransferMetadataForChild(KubernetesObject object,
      V1alpha1AipubUser targetUser) {
    V1ObjectMeta metadata = Objects.requireNonNull(object.getMetadata());
    Objects.requireNonNull(targetUser.getMetadata());
    Objects.requireNonNull(targetUser.getSpec());
    Objects.requireNonNull(targetUser.getSpec().getId());

    replaceAipubUserOwnerReference(metadata, targetUser);
    setUserLabels(metadata, targetUser);
  }

  /**
   * Cascade transfer to all objects with the same username-v2 label as the parent,
   * excluding the parent itself (already transferred by the reconciler).
   */
  public void cascadeTransferByUsernameV2Label(KubernetesObject parent,
      String oldUsername, V1alpha1AipubUser targetUser) throws ApiException {
    String parentKey = K8sObjectUtils.getNamespace(parent) + "/" + K8sObjectUtils.getName(parent);

    cascadeIndexer(this.deploymentIndexer, oldUsername, parentKey, targetUser,
        (dep) -> this.appsV1Api.replaceNamespacedDeployment(
            K8sObjectUtils.getName(dep), K8sObjectUtils.getNamespace(dep), dep).execute(),
        "Deployment");
    cascadeIndexer(this.daemonSetIndexer, oldUsername, parentKey, targetUser,
        (ds) -> this.appsV1Api.replaceNamespacedDaemonSet(
            K8sObjectUtils.getName(ds), K8sObjectUtils.getNamespace(ds), ds).execute(),
        "DaemonSet");
    cascadeIndexer(this.statefulSetIndexer, oldUsername, parentKey, targetUser,
        (ss) -> this.appsV1Api.replaceNamespacedStatefulSet(
            K8sObjectUtils.getName(ss), K8sObjectUtils.getNamespace(ss), ss).execute(),
        "StatefulSet");
    cascadeIndexer(this.replicaSetIndexer, oldUsername, parentKey, targetUser,
        (rs) -> this.appsV1Api.replaceNamespacedReplicaSet(
            K8sObjectUtils.getName(rs), K8sObjectUtils.getNamespace(rs), rs).execute(),
        "ReplicaSet");
    cascadeIndexer(this.cronJobIndexer, oldUsername, parentKey, targetUser,
        (cj) -> this.batchV1Api.replaceNamespacedCronJob(
            K8sObjectUtils.getName(cj), K8sObjectUtils.getNamespace(cj), cj).execute(),
        "CronJob");
    cascadeIndexer(this.jobIndexer, oldUsername, parentKey, targetUser,
        (job) -> this.batchV1Api.replaceNamespacedJob(
            K8sObjectUtils.getName(job), K8sObjectUtils.getNamespace(job), job).execute(),
        "Job");
    cascadeIndexer(this.podIndexer, oldUsername, parentKey, targetUser,
        (pod) -> this.coreV1Api.replaceNamespacedPod(
            K8sObjectUtils.getName(pod), K8sObjectUtils.getNamespace(pod), pod).execute(),
        "Pod");
  }

  private <T extends KubernetesObject> void cascadeIndexer(
      Indexer<T> indexer, String oldUsername, String parentKey,
      V1alpha1AipubUser targetUser, ResourceUpdater<T> updater, String typeName)
      throws ApiException {
    List<T> objects = indexer.byIndex(
        IndexerConstants.USERNAME_V2_LABEL_TO_OBJECTS_INDEXER_NAME, oldUsername);
    for (T obj : objects) {
      String objKey = K8sObjectUtils.getNamespace(obj) + "/" + K8sObjectUtils.getName(obj);
      if (objKey.equals(parentKey)) {
        continue;
      }
      applyTransferMetadataForChild(obj, targetUser);
      updater.update(obj);
      logger.debug("Cascaded transfer to {} [{}]", typeName, objKey);
    }
  }

  private void replaceAipubUserOwnerReference(V1ObjectMeta metadata,
      V1alpha1AipubUser targetUser) {
    V1OwnerReference newOwnerRef = K8sObjectUtils.buildV1OwnerReference(targetUser, false, false);

    List<V1OwnerReference> newRefs = new ArrayList<>();
    newRefs.add(newOwnerRef);
    if (metadata.getOwnerReferences() != null) {
      for (V1OwnerReference existing : metadata.getOwnerReferences()) {
        if (!ProjectApiConstants.AIPUB_USER_RESOURCE_KIND.equals(existing.getKind())) {
          newRefs.add(existing);
        }
      }
    }
    metadata.setOwnerReferences(newRefs);
  }

  private void setUserLabels(V1ObjectMeta metadata, V1alpha1AipubUser targetUser) {
    Map<String, String> labels = metadata.getLabels();
    if (labels == null) {
      labels = new HashMap<>();
      metadata.setLabels(labels);
    }
    String username = K8sObjectUtils.getName(targetUser);
    String userid = targetUser.getSpec().getId();
    labels.put(LabelConstants.OBJECT_OWN_USERNAME_KEY, username);
    labels.put(LabelConstants.OBJECT_OWN_USERID_KEY, userid);
    labels.put(LabelConstants.OBJECT_OWN_USERNAME_V2_KEY, username);
    labels.put(LabelConstants.OBJECT_OWN_USERID_V2_KEY, userid);
  }

  private void removeTransferAnnotation(V1ObjectMeta metadata) {
    Map<String, String> annotations = metadata.getAnnotations();
    if (annotations != null) {
      annotations.remove(AnnotationConstants.USER_TRANSFER_KEY);
    }
  }

}
