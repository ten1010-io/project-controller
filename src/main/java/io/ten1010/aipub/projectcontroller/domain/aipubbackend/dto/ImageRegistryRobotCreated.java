package io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

/**
 * robot 생성 응답. Harbor 가 생성 시점에 단 한 번만 반환하는 평문 secret 이 담겨 온다.
 *
 * <p>두 필드 모두 nullable 이다. aipub 백엔드는 유효한 permission 이 없어 생성을 건너뛴 경우와 Harbor 응답
 * 본문을 파싱하지 못한 경우 secret·robotId 없이 응답한다.
 */
@Data
public class ImageRegistryRobotCreated {

  @Nullable
  private String robotId;
  @Nullable
  private String secret;

}
