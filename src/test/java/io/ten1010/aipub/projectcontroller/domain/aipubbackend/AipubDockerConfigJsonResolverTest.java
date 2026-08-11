package io.ten1010.aipub.projectcontroller.domain.aipubbackend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobot;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotListOptions;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotSecret;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AipubDockerConfigJsonResolverTest {

  private static final String HARBOR_URL = "https://harbor.example.com";
  private static final String REGISTRY_DOMAIN = "harbor.example.com";
  private static final String PROJECT_NAME = "proj-1";
  private static final String ROBOT_USERNAME = "robot$proj-1";
  private static final String ROBOT_ID = "42";

  private ImageRegistryRobotService robotService;
  private ImageRegistryRobotSecretStore secretStore;
  private AipubDockerConfigJsonResolver resolver;

  private static V1alpha1Project project() {
    V1alpha1Project project = new V1alpha1Project();
    project.setMetadata(new V1ObjectMeta().name(PROJECT_NAME));
    return project;
  }

  @SuppressWarnings("unchecked")
  private static String passwordOf(Map<String, Object> dockerConfigJson) {
    Map<String, Object> auths = (Map<String, Object>) dockerConfigJson.get("auths");
    Map<String, String> registry = (Map<String, String>) auths.get(REGISTRY_DOMAIN);
    return registry.get("password");
  }

  @SuppressWarnings("unchecked")
  private static String authOf(Map<String, Object> dockerConfigJson) {
    Map<String, Object> auths = (Map<String, Object>) dockerConfigJson.get("auths");
    Map<String, String> registry = (Map<String, String>) auths.get(REGISTRY_DOMAIN);
    return registry.get("auth");
  }

  @BeforeEach
  void setUp() {
    this.robotService = mock(ImageRegistryRobotService.class);
    this.secretStore = new ImageRegistryRobotSecretStore();

    ImageRegistryRobot robot = new ImageRegistryRobot();
    robot.setId(ROBOT_ID);
    robot.setUsername(ROBOT_USERNAME);
    when(this.robotService.listImageRegistryRobots(any(ImageRegistryRobotListOptions.class)))
        .thenReturn(List.of(robot));

    ImageRegistryRobotUsernameResolver usernameResolver = projectName -> ROBOT_USERNAME;
    this.resolver = new AipubDockerConfigJsonResolver(HARBOR_URL, this.robotService,
        usernameResolver, this.secretStore);
  }

  @Test
  @DisplayName("생성 시점 secret 이 보관돼 있으면 그것을 쓰고 refreshsecret 을 부르지 않는다")
  void resolve_usesStoredSecretWithoutRefreshing() {
    this.secretStore.put(ROBOT_ID, "secret-from-create");

    Map<String, Object> dockerConfigJson = this.resolver.resolve(project());

    assertThat(passwordOf(dockerConfigJson)).isEqualTo("secret-from-create");
    verify(this.robotService, never()).refreshSecret(anyString());
  }

  @Test
  @DisplayName("보관된 secret 이 없으면 refreshsecret 으로 발급받는다")
  void resolve_fallsBackToRefreshSecret() {
    ImageRegistryRobotSecret refreshed = new ImageRegistryRobotSecret();
    refreshed.setSecret("secret-from-refresh");
    when(this.robotService.refreshSecret(ROBOT_ID)).thenReturn(refreshed);

    Map<String, Object> dockerConfigJson = this.resolver.resolve(project());

    assertThat(passwordOf(dockerConfigJson)).isEqualTo("secret-from-refresh");
    verify(this.robotService).refreshSecret(ROBOT_ID);
  }

  @Test
  @DisplayName("보관된 secret 은 한 번 쓰이고 소비된다 - 다음 resolve 는 refreshsecret 을 탄다")
  void resolve_consumesStoredSecret() {
    this.secretStore.put(ROBOT_ID, "secret-from-create");
    ImageRegistryRobotSecret refreshed = new ImageRegistryRobotSecret();
    refreshed.setSecret("secret-from-refresh");
    when(this.robotService.refreshSecret(ROBOT_ID)).thenReturn(refreshed);

    assertThat(passwordOf(this.resolver.resolve(project()))).isEqualTo("secret-from-create");
    assertThat(passwordOf(this.resolver.resolve(project()))).isEqualTo("secret-from-refresh");
  }

  @Test
  @DisplayName("보관된 secret 은 auth 필드에도 반영된다")
  void resolve_encodesStoredSecretIntoAuth() {
    this.secretStore.put(ROBOT_ID, "secret-from-create");

    Map<String, Object> dockerConfigJson = this.resolver.resolve(project());

    String expected = Base64.getEncoder()
        .encodeToString((ROBOT_USERNAME + ":secret-from-create").getBytes());
    assertThat(authOf(dockerConfigJson)).isEqualTo(expected);
  }

}
