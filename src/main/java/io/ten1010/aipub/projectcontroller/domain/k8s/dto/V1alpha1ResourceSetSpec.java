package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
public class V1alpha1ResourceSetSpec {

    @Nullable
    private String cpu;
    @Nullable
    private String gpu;
    @Nullable
    private String memory;
    @Nullable
    List<V1alpha1ResourceSetSpecNode> nodes;

}
