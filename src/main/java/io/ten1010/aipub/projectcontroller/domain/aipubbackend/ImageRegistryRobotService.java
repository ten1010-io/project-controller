package io.ten1010.aipub.projectcontroller.domain.aipubbackend;

import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobot;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotCreated;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotListOptions;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotSecret;
import java.util.List;
import java.util.Optional;

public interface ImageRegistryRobotService {

  /**
   * robot 을 생성하고 생성 응답을 돌려준다. 응답에는 Harbor 가 생성 시점에만 반환하는 평문 secret 이 담겨
   * 있어, {@link #refreshSecret} 를 다시 부르지 않고 그대로 쓸 수 있다.
   *
   * <p>백엔드가 본문 없이 응답하면 빈 값이 된다(유효한 permission 이 없어 생성을 건너뛴 경우 등).
   */
  Optional<ImageRegistryRobotCreated> createImageRegistryRobot(
      ImageRegistryRobot imageRegistryRobot);

  List<ImageRegistryRobot> listImageRegistryRobots(ImageRegistryRobotListOptions options);

  ImageRegistryRobot getImageRegistryRobot(String id);

  void updateImageRegistryRobot(String id, ImageRegistryRobot imageRegistryRobot);

  ImageRegistryRobotSecret refreshSecret(String id);

  void deleteImageRegistryRobot(String id);

}
