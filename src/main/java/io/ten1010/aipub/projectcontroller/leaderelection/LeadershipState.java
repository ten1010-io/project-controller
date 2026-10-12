package io.ten1010.aipub.projectcontroller.leaderelection;

/**
 * 이 파드가 리더인지. 리더 하나에서만 돌아야 하는 주기 작업이 읽는다.
 *
 * <p>컨트롤러 워크큐는 {@link LeaderElectionRunner#runWhenLeader} 가 통째로 감싸 막지만, 스스로 스케줄을 잡고 도는 작업은 그
 * 바깥에 있어 레플리카마다 돈다. 쓰기를 하는 쪽은 이 값을 보고 건너뛴다.
 *
 * <p>리스를 잃으면 프로세스가 끝나므로(같은 클래스의 {@code exit}) 이 값은 <b>한 방향으로만</b> 바뀐다. 강등을 다룰 필요가 없어
 * {@code volatile} 하나로 충분하다.
 */
public class LeadershipState {

  private volatile boolean leader;

  public boolean isLeader() {
    return this.leader;
  }

  /**
   * 리스를 잡았을 때 {@link LeaderElectionRunner} 가 한 번 부른다. 되돌리는 메서드는 두지 않는다 — 리스를 잃으면 프로세스가 끝나기
   * 때문이다.
   */
  public void markLeader() {
    this.leader = true;
  }

}
