package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1ObjectReference {

    @Nullable
    private String namespace;
    @Nullable
    private String name;

}
