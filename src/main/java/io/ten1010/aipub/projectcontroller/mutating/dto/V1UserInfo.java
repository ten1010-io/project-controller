package io.ten1010.aipub.projectcontroller.mutating.dto;

import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1UserInfo {

  @Nullable
  private String username;
  @Nullable
  private List<String> groups;

}
