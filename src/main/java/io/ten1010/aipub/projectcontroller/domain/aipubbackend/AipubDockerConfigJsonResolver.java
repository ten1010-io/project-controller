package io.ten1010.aipub.projectcontroller.domain.aipubbackend;

import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobot;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotListOptions;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotSecret;
import io.ten1010.aipub.projectcontroller.domain.k8s.DockerConfigJsonResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public class AipubDockerConfigJsonResolver implements DockerConfigJsonResolver {

  private final String registryDomain;
  private final ImageRegistryRobotService imageRegistryRobotService;
  private final ImageRegistryRobotUsernameResolver imageRegistryRobotUsernameResolver;

  public AipubDockerConfigJsonResolver(
      ImageRegistryInfoService imageRegistryInfoService,
      ImageRegistryRobotService imageRegistryRobotService,
      ImageRegistryRobotUsernameResolver imageRegistryRobotUsernameResolver) {
    String uri = imageRegistryInfoService.getImageRegistryInfo().getUri();
    Objects.requireNonNull(uri);
    this.registryDomain = removeHttpProtocolPrefix(uri);
    this.imageRegistryRobotService = imageRegistryRobotService;
    this.imageRegistryRobotUsernameResolver = imageRegistryRobotUsernameResolver;
  }

  private static String removeHttpProtocolPrefix(String input) {
    return input.replace("http://", "").replace("https://", "");
  }

  private static String createAuth(String username, @Nullable String password) {
    String auth = username + ":" + password;
    return Base64.getEncoder().encodeToString(auth.getBytes());
  }

  @Override
  public Map<String, Object> resolve(V1alpha1Project project) {
    String username = this.imageRegistryRobotUsernameResolver.resolve(
        K8sObjectUtils.getName(project));
    Optional<ImageRegistryRobot> robotOpt = findByUsername(username);
    if (robotOpt.isEmpty()) {
      throw new IllegalStateException("Could not find image registry robot for " + username);
    }
    String password = getPassword(robotOpt.get());

    Map<String, String> registry = new HashMap<>();
    registry.put("username", username);
    registry.put("password", password);
    registry.put("auth", createAuth(username, password));

    Map<String, Object> auths = new HashMap<>();
    auths.put(this.registryDomain, registry);

    Map<String, Object> json = new HashMap<>();
    json.put("auths", auths);

    return json;
  }

  private String getPassword(ImageRegistryRobot robot) {
    Objects.requireNonNull(robot.getId());
    ImageRegistryRobotSecret secret = this.imageRegistryRobotService.refreshSecret(robot.getId());
    Objects.requireNonNull(secret.getSecret());
    return secret.getSecret();
  }

  private Optional<ImageRegistryRobot> findByUsername(String username) {
    ImageRegistryRobotListOptions options = new ImageRegistryRobotListOptions();
    options.setPageOffset(0);
    options.setPageSize(100);
    List<ImageRegistryRobot> robots = this.imageRegistryRobotService.listImageRegistryRobots(
        options);
    return robots.stream()
        .filter(e -> Objects.nonNull(e.getUsername()))
        .filter(e -> e.getUsername().equals(username))
        .findFirst();
  }

}
