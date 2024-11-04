package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1ProjectStatusQuota {

    @Nullable
    private V1alpha1ProjectStatusQuotaMetric pvcStorage;

}
