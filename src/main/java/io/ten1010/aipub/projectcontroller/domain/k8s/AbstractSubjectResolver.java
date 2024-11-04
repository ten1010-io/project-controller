package io.ten1010.aipub.projectcontroller.domain.k8s;

import io.kubernetes.client.openapi.models.RbacV1Subject;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1AipubUser;
import io.ten1010.aipub.projectcontroller.domain.k8s.dto.V1alpha1ProjectMember;
import io.ten1010.aipub.projectcontroller.domain.k8s.util.K8sObjectUtils;

import java.util.Optional;

public abstract class AbstractSubjectResolver implements SubjectResolver {

    @Override
    public Optional<RbacV1Subject> resolve(V1alpha1ProjectMember member) {
        if (member.getAipubUser() != null) {
            return resolve(member.getAipubUser());
        }
        if (member.getSubject() != null) {
            return resolve(member.getSubject());
        }
        return Optional.empty();
    }

    @Override
    public Optional<RbacV1Subject> resolve(V1alpha1AipubUser user) {
        return resolve(K8sObjectUtils.getName(user));
    }

    protected Optional<RbacV1Subject> resolve(String aipubUser) {
        return Optional.empty();
    }

    protected Optional<RbacV1Subject> resolve(RbacV1Subject subject) {
        return Optional.empty();
    }

}
