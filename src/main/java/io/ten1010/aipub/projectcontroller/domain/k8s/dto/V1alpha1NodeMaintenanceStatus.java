package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
public class V1alpha1NodeMaintenanceStatus {

    @Nullable
    private List<String> allEffectedNodes;

    @Nullable
    private V1alpha1NodeMaintenanceAction action;

    @Nullable
    private String status;
}
