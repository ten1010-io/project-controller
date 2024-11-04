package io.ten1010.aipub.projectcontroller.domain.k8s;

import io.kubernetes.client.openapi.models.RbacV1Subject;

import java.util.Optional;

public class DefaultSubjectResolver extends AbstractSubjectResolver {

    @Override
    protected Optional<RbacV1Subject> resolve(RbacV1Subject subject) {
        return Optional.of(subject);
    }

}
