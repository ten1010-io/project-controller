package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
public class V1alpha1ProjectSpec {

    @Nullable
    private List<V1alpha1ProjectMember> members;
    @Nullable
    private V1alpha1ProjectSpecQuota quota;
    @Nullable
    private V1alpha1ProjectBinding binding;

}
