package io.ten1010.aipub.projectcontroller.domain.aipubbackend;

import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobot;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotListOptions;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotSecret;
import io.ten1010.aipub.projectcontroller.domain.k8s.DockerConfigJsonResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ImageHubNotConnectedException;
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
  private final ImageRegistryRobotSecretStore secretStore;

  public AipubDockerConfigJsonResolver(
      String harborExternalUrl,
      ImageRegistryRobotService imageRegistryRobotService,
      ImageRegistryRobotUsernameResolver imageRegistryRobotUsernameResolver,
      ImageRegistryRobotSecretStore secretStore) {
    Objects.requireNonNull(harborExternalUrl);
    this.registryDomain = removeHttpProtocolPrefix(harborExternalUrl);
    this.imageRegistryRobotService = imageRegistryRobotService;
    this.imageRegistryRobotUsernameResolver = imageRegistryRobotUsernameResolver;
    this.secretStore = Objects.requireNonNull(secretStore);
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
      throw new ImageHubNotConnectedException(
          "Could not find image registry robot for " + username);
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

  @Override
  public Optional<String> resolveImageRegistryRobotId(V1alpha1Project project) {
    String username = this.imageRegistryRobotUsernameResolver.resolve(
        K8sObjectUtils.getName(project));
    return findByUsername(username).map(ImageRegistryRobot::getId);
  }

  /**
   * robot 의 평문 비밀번호를 구한다.
   *
   * <p>robot 이 방금 생성된 경우엔 생성 응답의 secret 이 {@link ImageRegistryRobotSecretStore} 에 들어
   * 있으므로 그것을 쓴다. 없으면 refreshsecret 으로 새로 발급받는다. 재발급은 기존 비밀번호를 무효화하므로,
   * 이미 쓸 수 있는 secret 이 있을 때 굳이 부르지 않는 편이 낫다.
   */
  private String getPassword(ImageRegistryRobot robot) {
    String robotId = Objects.requireNonNull(robot.getId());
    Optional<String> storedSecret = this.secretStore.take(robotId);
    if (storedSecret.isPresent()) {
      return storedSecret.get();
    }
    ImageRegistryRobotSecret secret = this.imageRegistryRobotService.refreshSecret(robotId);
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
