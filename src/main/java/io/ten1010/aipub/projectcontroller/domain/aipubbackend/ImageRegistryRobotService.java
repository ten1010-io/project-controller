package io.ten1010.aipub.projectcontroller.domain.aipubbackend;

import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobot;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotListOptions;
import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ImageRegistryRobotSecret;

import java.util.List;

public interface ImageRegistryRobotService {

    void createImageRegistryRobot(ImageRegistryRobot imageRegistryRobot);

    List<ImageRegistryRobot> listImageRegistryRobots(ImageRegistryRobotListOptions options);

    ImageRegistryRobot getImageRegistryRobot(String id);

    void updateImageRegistryRobot(String id, ImageRegistryRobot imageRegistryRobot);

    ImageRegistryRobotSecret refreshSecret(String id);

    void deleteImageRegistryRobot(String id);

}
