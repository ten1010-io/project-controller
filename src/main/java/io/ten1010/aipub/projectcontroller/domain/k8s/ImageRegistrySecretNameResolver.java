package io.ten1010.aipub.projectcontroller.domain.k8s;

import java.util.Optional;

public class ImageRegistrySecretNameResolver {

  private static final String PREFIX = "image-registry-secret-project-aipub-ten1010-io-";

  public String resolveSecretName(String projectName) {
    return PREFIX + projectName;
  }

  public Optional<String> resolveProjectName(String secretName) {
    if (!secretName.startsWith(PREFIX)) {
      return Optional.empty();
    }

    return Optional.of(secretName.substring(PREFIX.length()));
  }

}
