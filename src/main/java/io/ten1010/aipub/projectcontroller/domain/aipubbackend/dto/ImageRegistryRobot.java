package io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
public class ImageRegistryRobot {

    @Nullable
    private String id;
    @Nullable
    private Long createdTimestamp;
    @Nullable
    private String username;
    @Nullable
    private String secret;
    @Nullable
    private List<ImageRegistryRobotPermission> permissions;

}
