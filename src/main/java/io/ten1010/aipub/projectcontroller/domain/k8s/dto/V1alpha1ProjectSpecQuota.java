package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@Data
public class V1alpha1ProjectSpecQuota {

    @Nullable
    private String pvcStorage;
    @Nullable
    private Map<String, String> extendedResources;

}
