package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;

@Data
public class V1alpha1AipubJobStatus {

    @Nullable
    private Integer active;
    @Nullable
    private V1alpha1AipubJobPodState lastExecutionPods;
    @Nullable
    private ArrayList<V1alpha1AipubJobInfo> failed;
    @Nullable
    private ArrayList<V1alpha1AipubJobInfo> pending;
    @Nullable
    private ArrayList<V1alpha1AipubJobInfo> running;
    @Nullable
    private ArrayList<V1alpha1AipubJobInfo> succeeded;
    @Nullable
    private V1alpha1AipubJobPodState state;


}
