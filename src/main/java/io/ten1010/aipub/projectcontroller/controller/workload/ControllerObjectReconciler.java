package io.ten1010.aipub.projectcontroller.controller.workload;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1NodeSelectorTerm;
import io.kubernetes.client.openapi.models.V1Toleration;
import java.util.List;

public interface ControllerObjectReconciler {

  Result reconcileController(
      KubernetesObject controller,
      List<V1Toleration> reconciledTolerations,
      List<V1NodeSelectorTerm> reconciledSelectorTerms,
      List<V1LocalObjectReference> reconciledImagePullSecrets) throws ApiException;

}
