package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Data
public class V1alpha1AipubVolumeStatus {

    @Nullable
    private V1alpha1AipubVolumeEvent event;
    @Nullable
    private List<V1alpha1AipubVolumeWorkload> mountWorkloads;
    @Nullable
    private String pvName;
    @Nullable
    private String pvcName;
    @Nullable
    private V1alpha1AipubVolumeCondition readyCondition;
    @Nullable
    private String used;

}
