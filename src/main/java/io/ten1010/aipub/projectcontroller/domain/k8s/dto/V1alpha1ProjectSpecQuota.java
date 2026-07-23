package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import java.util.Map;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1ProjectSpecQuota {

  @Nullable
  private String pvcStorage;
  @Nullable
  private Map<String, String> extendedResources;
  @Nullable
  private V1alpha1ProjectSpecQuotaHard hard;

}
