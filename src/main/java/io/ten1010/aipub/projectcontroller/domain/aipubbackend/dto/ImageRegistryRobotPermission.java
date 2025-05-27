package io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
public class ImageRegistryRobotPermission {

    @Nullable
    private String imageHubId;
    @Nullable
    private List<ImageRegistryAccess> accesses;

}
