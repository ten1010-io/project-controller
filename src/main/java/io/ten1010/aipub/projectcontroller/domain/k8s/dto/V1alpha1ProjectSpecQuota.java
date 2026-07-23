package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import java.util.Map;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1ProjectSpecQuota {

  /**
   * Quota limits keyed by the literal ResourceQuota resource name (e.g. {@code requests.cpu},
   * {@code limits.memory}, {@code requests.storage}, {@code limits.ten1010.io/gpu-...}). Passed
   * through verbatim to the managed ResourceQuota {@code spec.hard}, matching the native
   * ResourceQuota / UserResourceQuota format.
   */
  @Nullable
  private Map<String, String> hard;

}
