package io.ten1010.aipub.projectcontroller.mutating.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1AdmissionReviewResponse {

    @Nullable
    private String uid;
    @Nullable
    private Boolean allowed;
    @Nullable
    private V1Status status;
    @Nullable
    private String patchType;
    @Nullable
    private String patch;

}
