package io.ten1010.aipub.projectcontroller.domain.k8s;

import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;
import java.util.Map;
import java.util.Optional;

public interface DockerConfigJsonResolver {

  Map<String, Object> resolve(V1alpha1Project project);

  /**
   * project 에 연결된 image registry robot 의 id 를 조회한다. secret 을 새로 발급받지 않고
   * "지금 이 프로젝트의 robot 이 마지막으로 발급했던 robot 과 같은가"만 값싸게 확인하기 위한 용도.
   */
  Optional<String> resolveImageRegistryRobotId(V1alpha1Project project);

}
