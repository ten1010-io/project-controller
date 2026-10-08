package io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.Nullable;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ImageRegistryRobotListOptions extends ListOptions {

  /** 백엔드가 harbor 의 q 로 넘긴다. username 은 harbor 의 name 에 대응한다. */
  @Nullable
  private String q;

}
