package io.ten1010.aipub.projectcontroller.domain.k8s.dto;

import lombok.Data;
import org.jspecify.annotations.Nullable;

/**
 * A compute resource quota entry wrapping the {@code requests} / {@code limits} caps for a single
 * resource (e.g. cpu, memory). Mapped to the {@code requests.<name>} / {@code limits.<name>} keys
 * of the managed ResourceQuota.
 */
@Data
public class V1alpha1ProjectSpecQuotaResource {

  @Nullable
  private String requests;
  @Nullable
  private String limits;

}
