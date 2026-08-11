package io.ten1010.aipub.projectcontroller.informer.owned;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 개인 Role 을 주기적으로 전체 재큐잉하는 백스톱.
 * 소유권 반영은 이벤트 기반(level-based 리컨실)이 기본 경로지만, 이벤트가 유실되는
 * 예외 상황(기동 타이밍 레이스, 예기치 못한 드리프트)에서도 한 주기 안에 수렴을 보장한다.
 * 비용은 개인 Role 수에 비례하고 워크큐가 중복을 흡수하므로 주기를 짧게 잡을 필요가 없다.
 */
@Slf4j
public class OwnedObjectRoleResweeper {

  private static final long RESWEEP_INTERVAL_MS = 600_000; // 10분

  private final OwnedObjectInformerManager ownedObjectInformerManager;

  public OwnedObjectRoleResweeper(OwnedObjectInformerManager ownedObjectInformerManager) {
    this.ownedObjectInformerManager = ownedObjectInformerManager;
  }

  @Scheduled(fixedDelay = RESWEEP_INTERVAL_MS, initialDelay = RESWEEP_INTERVAL_MS)
  public void resweep() {
    try {
      this.ownedObjectInformerManager.resweepPersonalRoles();
    } catch (Exception e) {
      log.warn("Failed to resweep personal roles", e);
    }
  }

}
