package io.ten1010.aipub.projectcontroller.informer.owned;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.ten1010.aipub.projectcontroller.leaderelection.LeadershipState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OwnedObjectRoleResweeperTest {

  @Test
  @DisplayName("리더가 아니면 재큐잉하지 않는다")
  void resweep_notLeader_doesNotEnqueue() {
    OwnedObjectInformerManager manager = mock(OwnedObjectInformerManager.class);
    OwnedObjectRoleResweeper resweeper =
        new OwnedObjectRoleResweeper(manager, new LeadershipState());

    resweeper.resweep();

    // 워크큐를 비우는 건 리더뿐이라, 리더가 아닌 파드에서 넣으면 꺼내는 쪽 없이 쌓이기만 한다
    verifyNoInteractions(manager);
  }

  @Test
  @DisplayName("리더면 평소대로 재큐잉한다")
  void resweep_leader_enqueues() {
    OwnedObjectInformerManager manager = mock(OwnedObjectInformerManager.class);
    LeadershipState leader = new LeadershipState();
    leader.markLeader();
    OwnedObjectRoleResweeper resweeper = new OwnedObjectRoleResweeper(manager, leader);

    resweeper.resweep();

    verify(manager).resweepPersonalRoles();
  }

}
