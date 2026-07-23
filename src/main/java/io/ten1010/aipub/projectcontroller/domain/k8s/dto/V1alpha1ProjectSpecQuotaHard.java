package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

/**
 * Compute resource caps, grouped under {@code hard} to mirror the native ResourceQuota
 * {@code spec.hard}. Each entry wraps the {@code requests} / {@code limits} caps for a resource.
 */
@Data
public class V1alpha1ProjectSpecQuotaHard {

  @Nullable
  private V1alpha1ProjectSpecQuotaResource cpu;
  @Nullable
  private V1alpha1ProjectSpecQuotaResource memory;

}
