package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
public class V1alpha1ResourceSetStatus {

    @Nullable
    private String category;
    @Nullable
    List<V1alpha1ResourceSetStatusNode> nodes;
    @Nullable
    private Boolean ready;
    @Nullable
    private String resourceName;

}
