package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
public class V1alpha1NodeGroupDaemonSetPolicy {

    @Nullable
    private Boolean allowAllDaemonSets;
    @Nullable
    private List<String> allowedNamespaces;
    @Nullable
    private List<V1alpha1ObjectReference> allowedDaemonSets;

}
