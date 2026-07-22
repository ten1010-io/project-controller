package io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto;

import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class ImageRegistryRobotPermission {

  @Nullable
  private String imageHubId;
  @Nullable
  private List<ImageRegistryAccess> accesses;

}
