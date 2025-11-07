package io.ten1010.aipub.projectcontroller.domain.k8s.util;

import io.kubernetes.client.common.KubernetesObject;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import java.util.Optional;

public abstract class UsernameUtils {

  public static Optional<String> getUsername(KubernetesObject object) {
    if (object.getMetadata() == null ||
        object.getMetadata().getLabels() == null ||
        object.getMetadata().getLabels().get(LabelConstants.OBJECT_OWN_USERNAME_KEY) == null) {
      return Optional.empty();
    }
    return Optional.of(
        object.getMetadata().getLabels().get(LabelConstants.OBJECT_OWN_USERNAME_KEY));
  }

}
