package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

/**
 * Reported compute resource quota, grouped under {@code hard} to mirror the spec shape. Each entry
 * wraps the {@code requests} / {@code limits} limit/used metrics for a resource.
 */
@Data
public class V1alpha1ProjectStatusQuotaHard {

  @Nullable
  private V1alpha1ProjectStatusQuotaResource cpu;
  @Nullable
  private V1alpha1ProjectStatusQuotaResource memory;

}
