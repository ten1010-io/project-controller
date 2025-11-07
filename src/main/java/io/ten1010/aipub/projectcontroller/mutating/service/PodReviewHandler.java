package io.ten1010.aipub.projectcontroller.mutating.service;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1AffinityBuilder;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1NodeSelectorTerm;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.aipub.projectcontroller.controller.workload.PodNodesResolver;
import io.ten1010.aipub.projectcontroller.controller.workload.UnsupportedControllerException;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.KeyResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.mutating.V1AdmissionReviewUtils;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.common.jsonpatch.JsonPatchBuilder;
import io.ten1010.common.jsonpatch.JsonPatchOperationBuilder;
import io.ten1010.common.jsonpatch.dto.JsonPatchOperation;
import java.util.List;
import java.util.Objects;

public class PodReviewHandler extends AbstractReviewHandler<V1Pod> {

  private final PodNodesResolver podNodesResolver;
  private final Indexer<V1alpha1Project> projectIndexer;
  private final ReconciliationService reconciliationService;

  public PodReviewHandler(
      PodNodesResolver podNodesResolver, SharedInformerFactory sharedInformerFactory,
      ReconciliationService reconciliationService) {
    super(K8sObjectTypeConstants.POD_V1);
    this.podNodesResolver = podNodesResolver;
    this.projectIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1Project.class)
        .getIndexer();
    this.reconciliationService = reconciliationService;
  }

  @Override
  public void handle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());

    V1Pod pod = getRequestObject(review);

    List<V1Node> allowedProjectNodeObjects;
    try {
      allowedProjectNodeObjects = this.podNodesResolver.getNodes(pod);
    } catch (UnsupportedControllerException e) {
      allowedProjectNodeObjects = this.podNodesResolver._getNodes(pod); // todo
    }
    V1alpha1Project project = this.projectIndexer.getByKey(
        new KeyResolver().resolveKey(K8sObjectUtils.getNamespace(pod)));

    List<V1Toleration> reconciledTolerations = this.reconciliationService.reconcileTolerations(pod,
        allowedProjectNodeObjects);
    List<V1NodeSelectorTerm> reconciledSelectorTerms = this.reconciliationService.reconcileNodeSelectorTerms(
        pod, project);
    List<V1LocalObjectReference> reconciledImagePullSecrets = this.reconciliationService.reconcileImagePullSecrets(
        pod, project);

    JsonPatchBuilder jsonPatchBuilder = new JsonPatchBuilder();

    JsonPatchOperation tolerationsPatchOp = new JsonPatchOperationBuilder()
        .replace()
        .setPath("/spec/tolerations")
        .setValue(createJsonNode(reconciledTolerations))
        .build();
    jsonPatchBuilder.addToOperations(tolerationsPatchOp);

    if (!reconciledSelectorTerms.isEmpty()) {
      V1Affinity reconciledAffinity = new V1AffinityBuilder(pod.getSpec().getAffinity())
          .editNodeAffinity()
          .editRequiredDuringSchedulingIgnoredDuringExecution()
          .withNodeSelectorTerms(reconciledSelectorTerms)
          .endRequiredDuringSchedulingIgnoredDuringExecution()
          .endNodeAffinity()
          .build();
      JsonPatchOperation affinityPatchOp = new JsonPatchOperationBuilder()
          .add()
          .setPath("/spec/affinity")
          .setValue(createJsonNode(reconciledAffinity))
          .build();
      jsonPatchBuilder.addToOperations(affinityPatchOp);
    }

    JsonPatchOperation imagePullSecretsPatchOp = new JsonPatchOperationBuilder()
        .replace()
        .setPath("/spec/imagePullSecrets")
        .setValue(createJsonNode(reconciledImagePullSecrets))
        .build();
    jsonPatchBuilder.addToOperations(imagePullSecretsPatchOp);

    V1AdmissionReviewUtils.allow(review, jsonPatchBuilder.build());
  }

}
