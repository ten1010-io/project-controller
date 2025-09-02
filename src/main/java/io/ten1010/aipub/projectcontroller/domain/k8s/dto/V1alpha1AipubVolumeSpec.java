package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1AipubVolumeSpec {

    @Nullable
    private String capacity;
    @Nullable
    private V1alpha1AipubVolumeNfs nfs;
    @Nullable
    private String storageClassName;

}
