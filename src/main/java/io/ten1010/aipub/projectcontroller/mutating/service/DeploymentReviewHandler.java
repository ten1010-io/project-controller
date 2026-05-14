package io.ten1010.aipub.projectcontroller.mutating.service;

import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1AffinityBuilder;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1NodeSelectorTerm;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.ten1010.aipub.projectcontroller.controller.workload.WorkloadControllerNodesResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.K8sObjectTypeConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.ReconciliationService;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.WorkloadUtils;
import io.ten1010.aipub.projectcontroller.mutating.V1AdmissionReviewUtils;
import io.ten1010.aipub.projectcontroller.mutating.dto.V1AdmissionReview;
import io.ten1010.common.jsonpatch.JsonPatchBuilder;
import io.ten1010.common.jsonpatch.JsonPatchOperationBuilder;
import io.ten1010.common.jsonpatch.dto.JsonPatchOperation;
import java.util.List;
import java.util.Objects;

public class DeploymentReviewHandler extends AbstractReviewHandler<V1Deployment> {

  private final WorkloadControllerNodesResolver workloadControllerNodesResolver;
  private final Indexer<V1alpha1Project> projectIndexer;
  private final ReconciliationService reconciliationService;

  public DeploymentReviewHandler(
      WorkloadControllerNodesResolver workloadControllerNodesResolver,
      SharedInformerFactory sharedInformerFactory, ReconciliationService reconciliationService) {
    super(K8sObjectTypeConstants.DEPLOYMENT_V1);
    this.workloadControllerNodesResolver = workloadControllerNodesResolver;
    this.projectIndexer = sharedInformerFactory
        .getExistingSharedIndexInformer(V1alpha1Project.class)
        .getIndexer();
    this.reconciliationService = reconciliationService;
  }

  @Override
  public void handle(V1AdmissionReview review) {
    Objects.requireNonNull(review.getRequest());

    V1Deployment deployment = getRequestObject(review);

    if (K8sObjectUtils.isCiliumComponent(deployment)) {
      V1AdmissionReviewUtils.allow(review);
      return;
    }

    V1alpha1Project project = this.projectIndexer.getByKey(K8sObjectUtils.getNamespace(deployment));

    V1PodTemplateSpec podTemplateSpec = WorkloadUtils.getPodTemplateSpec(deployment);
    List<V1Node> nodeObjects = this.workloadControllerNodesResolver.getNodes(deployment);
    List<V1Toleration> reconciledTolerations = this.reconciliationService.reconcileTolerations(
        podTemplateSpec, nodeObjects);
    List<V1NodeSelectorTerm> reconciledSelectorTerms = this.reconciliationService.reconcileNodeSelectorTerms(
        podTemplateSpec, project);
    List<V1LocalObjectReference> reconciledImageRegistrySecrets = this.reconciliationService.reconcileImageRegistrySecrets(
        podTemplateSpec, project);

    JsonPatchBuilder jsonPatchBuilder = new JsonPatchBuilder();

    JsonPatchOperation tolerationsPatchOp = new JsonPatchOperationBuilder()
        .replace()
        .setPath("/spec/template/spec/tolerations")
        .setValue(createJsonNode(reconciledTolerations))
        .build();
    jsonPatchBuilder.addToOperations(tolerationsPatchOp);

    if (!reconciledSelectorTerms.isEmpty()) {
      V1Affinity reconciledAffinity = new V1AffinityBuilder(
          deployment.getSpec().getTemplate().getSpec().getAffinity())
          .editNodeAffinity()
          .editRequiredDuringSchedulingIgnoredDuringExecution()
          .withNodeSelectorTerms(reconciledSelectorTerms)
          .endRequiredDuringSchedulingIgnoredDuringExecution()
          .endNodeAffinity()
          .build();
      JsonPatchOperation affinityPatchOp = new JsonPatchOperationBuilder()
          .add()
          .setPath("/spec/template/spec/affinity")
          .setValue(createJsonNode(reconciledAffinity))
          .build();
      jsonPatchBuilder.addToOperations(affinityPatchOp);
    }

    JsonPatchOperation imagePullSecretsPatchOp = new JsonPatchOperationBuilder()
        .replace()
        .setPath("/spec/template/spec/imagePullSecrets")
        .setValue(createJsonNode(reconciledImageRegistrySecrets))
        .build();
    jsonPatchBuilder.addToOperations(imagePullSecretsPatchOp);

    V1AdmissionReviewUtils.allow(review, jsonPatchBuilder.build());
  }

}
