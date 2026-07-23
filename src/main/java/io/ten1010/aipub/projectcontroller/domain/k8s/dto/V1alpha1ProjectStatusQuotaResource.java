package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

/**
 * Reported quota for a single compute resource, wrapping the {@code requests} / {@code limits}
 * limit/used metrics (e.g. cpu, memory).
 */
@Data
public class V1alpha1ProjectStatusQuotaResource {

  @Nullable
  private V1alpha1ProjectStatusQuotaMetric requests;
  @Nullable
  private V1alpha1ProjectStatusQuotaMetric limits;

}
