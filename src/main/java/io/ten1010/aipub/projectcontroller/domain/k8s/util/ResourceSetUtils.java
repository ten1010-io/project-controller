package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ResourceSet;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ResourceSetSpecNode;
import java.util.List;
import java.util.Objects;

public abstract class ResourceSetUtils {

  public static List<String> getSpecNodeNames(V1alpha1ResourceSet object) {
    if (object.getSpec() == null ||
        object.getSpec().getNodes() == null ||
        object.getSpec().getNodes().isEmpty()) {
      return List.of();
    }
    return object.getSpec().getNodes().stream()
        .map(V1alpha1ResourceSetSpecNode::getNodeName)
        .filter(Objects::nonNull)
        .toList();
  }

}
