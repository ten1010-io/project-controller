package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

@Data
public class V1alpha1NodeGroupSpec {

    @Nullable
    private V1alpha1NodeGroupPolicy policy;
    @Nullable
    private Boolean enableNodeSelector;
    @Nullable
    private Map<String, String> nodeSelector;
    @Nullable
    private List<String> nodes;

}
