package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import java.util.Map;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Data
public class V1alpha1ProjectStatusQuota {

  /**
   * Enforced quota limits, keyed by the literal ResourceQuota resource name. Mirrors the native
   * ResourceQuota {@code status.hard}.
   */
  @Nullable
  private Map<String, String> hard;
  /**
   * Observed usage, keyed by the literal ResourceQuota resource name. Mirrors the native
   * ResourceQuota {@code status.used}.
   */
  @Nullable
  private Map<String, String> used;

}
