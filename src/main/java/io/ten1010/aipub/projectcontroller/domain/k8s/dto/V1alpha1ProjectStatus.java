package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
public class V1alpha1ProjectStatus {

    @Nullable
    private List<String> allBoundAipubUsers;
    @Nullable
    private V1alpha1ProjectStatusQuota quota;
    @Nullable
    private List<String> allBoundNodeGroups;
    @Nullable
    private List<String> allBoundNodes;
    @Nullable
    private List<String> allBoundImageNamespaces;

}
