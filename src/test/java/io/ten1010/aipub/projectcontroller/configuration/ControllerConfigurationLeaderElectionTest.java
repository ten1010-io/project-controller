package io.ten1010.aipub.projectcontroller.configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.kubernetes.client.extended.controller.Controller;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.ten1010.aipub.projectcontroller.controller.workload.WorkloadControllerFactory;
import io.ten1010.aipub.projectcontroller.informer.owned.OwnedObjectInformerManager;
import io.ten1010.aipub.projectcontroller.leaderelection.LeaderElectionRunner;
import java.util.List;
import org.junit.jupiter.api.Test;

class ControllerConfigurationLeaderElectionTest {

  /**
   * 인포머를 리더에게만 맡기면 리더가 아닌 파드의 어드미션 웹훅이 빈 캐시를 읽는다. 웹훅 failurePolicy 가 Fail 이라 파드·네임스페이스 생성이 거부된다.
   */
  @Test
  void informersStart_evenBeforeTheLeaderLeaseIsAcquired() {
    SharedInformerFactory sharedInformerFactory = mock(SharedInformerFactory.class);
    LeaderElectionRunner leaderElectionRunner = mock(LeaderElectionRunner.class);

    buildControllerManager(sharedInformerFactory, leaderElectionRunner);

    verify(sharedInformerFactory).startAllRegisteredInformers();
  }

  /** 쓰기를 내는 워크큐는 리더에게만 맡긴다 — 겹침 구간의 재발급 경쟁을 막는 지점이다. */
  @Test
  void controllerLoop_isHandedToTheLeaderElectionRunner() {
    SharedInformerFactory sharedInformerFactory = mock(SharedInformerFactory.class);
    LeaderElectionRunner leaderElectionRunner = mock(LeaderElectionRunner.class);

    buildControllerManager(sharedInformerFactory, leaderElectionRunner);

    // 별도 스레드에 넘기므로 잠깐 기다린다
    verify(leaderElectionRunner, timeout(5_000)).runWhenLeader(any());
  }

  private void buildControllerManager(
      SharedInformerFactory sharedInformerFactory, LeaderElectionRunner leaderElectionRunner) {
    List<Controller> controllers = List.of();
    List<WorkloadControllerFactory<?>> workloadControllerFactories = List.of();
    OwnedObjectInformerManager ownedObjectInformerManager = mock(OwnedObjectInformerManager.class);

    new ControllerConfiguration().controllerManager(
        sharedInformerFactory, controllers, workloadControllerFactories,
        ownedObjectInformerManager, leaderElectionRunner);
  }

}
