package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import java.util.Map;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1ProjectSpecQuota {

  /**
   * Quota limits keyed by the literal ResourceQuota resource name (e.g. {@code requests.cpu},
   * {@code limits.memory}, {@code requests.storage}, {@code requests.ten1010.io/gpu-...}). Passed
   * through verbatim to the managed ResourceQuota {@code spec.hard}, matching the native
   * ResourceQuota format. Extended resources (e.g. GPU resource sets) must use the
   * {@code requests.} prefix, since native ResourceQuota disallows {@code limits.} for them.
   */
  @Nullable
  private Map<String, String> hard;

}
