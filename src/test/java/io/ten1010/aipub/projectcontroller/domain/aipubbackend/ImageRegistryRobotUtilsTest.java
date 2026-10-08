package io.ten1010.aipub.projectcontroller.domain.aipubbackend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobot;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotListOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ImageRegistryRobotUtilsTest {

  private static final String USERNAME = "robot$project-aipub-ten1010-io-abc";

  /**
   * Harbor 는 접두사를 붙이면 0건을 준다. 붙여 보내면 모든 robot 이 '없음' 이 돼 권한 갱신과 Secret 생성이 통째로 멈춘다.
   */
  @Test
  void query_dropsTheRobotPrefixThatHarborDoesNotMatch() {
    ImageRegistryRobotService service = mock(ImageRegistryRobotService.class);
    when(service.listImageRegistryRobots(any())).thenReturn(List.of(robot(USERNAME)));

    ImageRegistryRobotUtils.findByUsername(service, USERNAME);

    ArgumentCaptor<ImageRegistryRobotListOptions> captor =
        ArgumentCaptor.forClass(ImageRegistryRobotListOptions.class);
    verify(service).listImageRegistryRobots(captor.capture());
    assertThat(captor.getValue().getQ()).isEqualTo("username=project-aipub-ten1010-io-abc");
  }

  /** 응답의 username 에는 접두사가 붙어 온다 — 질의와 비교가 서로 다른 모양이라는 뜻이다. */
  @Test
  void response_isMatchedWithThePrefixStillAttached() {
    ImageRegistryRobotService service = mock(ImageRegistryRobotService.class);
    when(service.listImageRegistryRobots(any())).thenReturn(List.of(robot(USERNAME)));

    Optional<ImageRegistryRobot> found = ImageRegistryRobotUtils.findByUsername(service, USERNAME);

    assertThat(found).isPresent();
    assertThat(found.get().getUsername()).isEqualTo(USERNAME);
  }

  /** 예전에는 필터 없이 첫 100건만 받아 101번째 robot 을 영영 못 찾았다. */
  @Test
  void robotBeyondTheFirstHundred_isStillFound() {
    ImageRegistryRobotService service = mock(ImageRegistryRobotService.class);
    List<ImageRegistryRobot> page = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      page.add(robot("robot$project-aipub-ten1010-io-other-" + i));
    }
    page.add(robot(USERNAME));
    when(service.listImageRegistryRobots(any())).thenReturn(page);

    assertThat(ImageRegistryRobotUtils.findByUsername(service, USERNAME)).isPresent();
  }

  /** 느슨하게 매칭하는 Harbor 버전이 비슷한 이름을 섞어 줘도 다른 robot 을 집지 않는다. */
  @Test
  void looseMatchesInTheResponse_areRejected() {
    ImageRegistryRobotService service = mock(ImageRegistryRobotService.class);
    when(service.listImageRegistryRobots(any()))
        .thenReturn(List.of(robot(USERNAME + "-staging"), robot("robot$unrelated")));

    assertThat(ImageRegistryRobotUtils.findByUsername(service, USERNAME)).isEmpty();
  }

  @Test
  void usernameWithoutThePrefix_isQueriedAsIs() {
    assertThat(ImageRegistryRobotUtils.toQueryName("plain-name")).isEqualTo("plain-name");
  }

  private static ImageRegistryRobot robot(String username) {
    ImageRegistryRobot robot = new ImageRegistryRobot();
    robot.setUsername(username);
    return robot;
  }

}
