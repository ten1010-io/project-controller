package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import io.kubernetes.client.openapi.models.RbacV1Subject;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1ProjectMember {

    @Nullable
    private String aipubUser;
    @Nullable
    private RbacV1Subject subject;
    @Nullable
    private String role;

}
