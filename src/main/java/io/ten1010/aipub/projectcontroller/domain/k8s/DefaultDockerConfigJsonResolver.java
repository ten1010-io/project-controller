package io.ten1010.aipub.projectcontroller.domain.k8s;

import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import java.util.Map;
import java.util.Optional;

public class DefaultDockerConfigJsonResolver implements DockerConfigJsonResolver {

  @Override
  public Map<String, Object> resolve(V1alpha1Project project) {
    return Map.of();
  }

  @Override
  public Optional<String> resolveImageRegistryRobotId(V1alpha1Project project) {
    return Optional.empty();
  }

}
