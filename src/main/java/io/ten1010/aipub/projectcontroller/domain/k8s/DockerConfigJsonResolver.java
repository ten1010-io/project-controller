package io.ten1010.aipub.projectcontroller.domain.k8s;

import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1Project;

import java.util.Map;

public interface DockerConfigJsonResolver {

    Map<String, Object> resolve(V1alpha1Project project);

}
