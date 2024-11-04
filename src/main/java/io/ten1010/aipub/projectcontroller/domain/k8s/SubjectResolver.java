package io.ten1010.aipub.projectcontroller.domain.k8s;

import io.kubernetes.client.openapi.models.RbacV1Subject;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectMember;

import java.util.Optional;

public interface SubjectResolver {

    Optional<RbacV1Subject> resolve(V1alpha1ProjectMember member);

    Optional<RbacV1Subject> resolve(V1alpha1AipubUser user);

}
