package io.ten1010.aipub.projectcontroller.domain.aipubbackend;

import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobot;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotListOptions;
import java.util.Objects;
import java.util.Optional;

public abstract class ImageRegistryRobotUtils {

  /**
   * 느슨하게 매칭하는 Harbor 버전을 만나도 이름으로 추릴 여지를 남긴다. 정확히 일치하면 어차피 1건이다.
   */
  private static final int PAGE_SIZE = 100;

  /**
   * Harbor 는 robot 이름을 <b>접두사 없이</b> 질의한다 — {@code name=robot$foo} 는 0건이고 {@code name=foo} 가 1건이다.
   * 반면 응답의 username 에는 접두사가 그대로 붙어 온다.
   */
  private static final String QUERY_NAME_PREFIX = "robot$";

  /**
   * 이름으로 robot 하나를 찾는다.
   *
   * <p>예전에는 필터 없이 첫 100건을 받아 그 안에서 골랐다. robot 이 100개를 넘는 클러스터에서는 101번째부터 '없음' 으로 보여, 권한이 갱신되지 않고
   * Secret 도 다시 만들어지지 않았다.
   */
  public static Optional<ImageRegistryRobot> findByUsername(
      ImageRegistryRobotService robotService, String username) {
    Objects.requireNonNull(robotService);
    Objects.requireNonNull(username);
    ImageRegistryRobotListOptions options = new ImageRegistryRobotListOptions();
    options.setQ("username=" + toQueryName(username));
    options.setPageOffset(0);
    options.setPageSize(PAGE_SIZE);
    return robotService.listImageRegistryRobots(options).stream()
        .filter(e -> Objects.nonNull(e.getUsername()))
        .filter(e -> e.getUsername().equals(username))
        .findFirst();
  }

  static String toQueryName(String username) {
    return username.startsWith(QUERY_NAME_PREFIX)
        ? username.substring(QUERY_NAME_PREFIX.length())
        : username;
  }

}
