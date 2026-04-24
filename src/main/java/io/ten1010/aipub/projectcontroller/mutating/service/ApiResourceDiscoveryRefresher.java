package io.ten1010.aipub.projectcontroller.mutating.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ApiResourceDiscoveryRefresher {

  private static final long REFRESH_INTERVAL_MS = 300_000; // 5분

  private final ApiResourceDiscovery apiResourceDiscovery;

  public ApiResourceDiscoveryRefresher(ApiResourceDiscovery apiResourceDiscovery) {
    this.apiResourceDiscovery = apiResourceDiscovery;
  }

  @Scheduled(fixedDelay = REFRESH_INTERVAL_MS)
  public void refresh() {
    try {
      this.apiResourceDiscovery.refresh();
    } catch (Exception e) {
      log.warn("Failed to refresh API resource discovery", e);
    }
  }

}
