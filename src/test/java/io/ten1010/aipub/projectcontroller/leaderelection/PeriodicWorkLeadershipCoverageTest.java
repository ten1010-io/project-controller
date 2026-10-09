package io.ten1010.aipub.projectcontroller.leaderelection;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 스스로 스케줄을 잡고 도는 작업이 새로 생기면 깨진다.
 *
 * <p>컨트롤러 워크큐는 {@link LeaderElectionRunner} 가 통째로 감싸지만, {@code @Scheduled} 나 {@code
 * ScheduledExecutorService} 로 스스로 도는 작업은 그 바깥이라 <b>레플리카마다 돈다.</b> 쓰기를 하는 작업이 그렇게 추가되면 같은
 * 오브젝트에 중복 쓰기가 되는데, 레플리카가 하나인 동안은 아무 증상이 없어 리뷰에서 놓치기 쉽다(실제로 한 번 놓쳤다).
 *
 * <p>그래서 목록을 여기 고정한다. 새로 추가하면 이 테스트가 깨지고, 작성자는 <b>"이건 리더만 돌아야 하나, 파드마다 돌아야 하나"</b> 를 반드시
 * 판단하게 된다. 답에 따라 아래 두 집합 중 한쪽에 넣는다.
 */
class PeriodicWorkLeadershipCoverageTest {

  private static final Path SOURCE_ROOT = Path.of("src/main/java");

  /** 스스로 도는 작업을 만드는 표현. 하나라도 쓰면 이 테스트의 대상이 된다. */
  private static final List<String> SELF_SCHEDULING_MARKERS = List.of(
      "@Scheduled",
      "scheduleWithFixedDelay",
      "scheduleAtFixedRate");

  /**
   * 리더에서만 돌아야 하는 것 — 클러스터 오브젝트를 쓰거나, 리더만 비우는 워크큐에 넣는다.
   *
   * <p>여기 넣었다면 그 클래스는 {@link LeadershipState#isLeader()} 를 보고 건너뛰어야 한다.
   */
  private static final Set<String> LEADER_ONLY = Set.of(
      "UserLabelSynchronizer",
      "ClusterVolumeChildLabelSynchronizer",
      "OwnedObjectRoleResweeper");

  /**
   * 레플리카마다 돌아야 하는 것 — 파드별 캐시·지표다.
   *
   * <p>⛔ 이쪽을 리더로 좁히면 안 된다. 리더가 아닌 파드도 어드미션 웹훅에 답해야 하고, 그러려면 자기 캐시가 최신이어야 한다. 캐시가 낡으면 웹훅이 틀린
   * 답을 하거나 요청을 거부한다.
   */
  private static final Set<String> EVERY_REPLICA = Set.of(
      "ApiResourceDiscoveryRefresher");

  @Test
  @DisplayName("스스로 도는 주기 작업은 전부 리더 전용/모든 레플리카 중 하나로 분류돼 있어야 한다")
  void selfSchedulingClasses_areAllClassified() throws IOException {
    Set<String> found = findSelfSchedulingClasses();
    Set<String> classified = new TreeSet<>(LEADER_ONLY);
    classified.addAll(EVERY_REPLICA);

    assertThat(found)
        .as("스스로 스케줄을 잡고 도는 클래스가 바뀌었습니다. 새로 추가했다면 \"리더만 돌아야 하는가\"를 먼저 판단하고,"
            + " 쓰기를 하면 LEADER_ONLY 에 넣은 뒤 LeadershipState.isLeader() 가드를 추가하세요."
            + " 파드별 캐시·지표라면 EVERY_REPLICA 에 넣으세요.")
        .isEqualTo(classified);
  }

  @Test
  @DisplayName("리더 전용으로 분류된 클래스는 실제로 LeadershipState 가드를 갖고 있어야 한다")
  void leaderOnlyClasses_haveLeadershipGuard() throws IOException {
    for (String className : LEADER_ONLY) {
      String source = readSource(className);
      assertThat(source)
          .as(className + " 가 LEADER_ONLY 로 분류돼 있는데 isLeader() 가드가 없습니다."
              + " 가드 없이 분류만 해 두면 레플리카가 늘어날 때 그대로 중복 실행됩니다.")
          .contains("isLeader()");
    }
  }

  /**
   * ⛔ 반대 방향도 고정한다. "안전하게 전부 리더로 묶자" 는 실수가 더 조용하다 — 캐시가 갱신되지 않는 파드가 웹훅에 틀린 답을 하고, 그건 테스트로도
   * 로그로도 잘 드러나지 않는다.
   */
  @Test
  @DisplayName("모든 레플리카에서 돌아야 하는 클래스에는 리더 가드가 없어야 한다")
  void everyReplicaClasses_haveNoLeadershipGuard() throws IOException {
    for (String className : EVERY_REPLICA) {
      String source = readSource(className);
      assertThat(source)
          .as(className + " 는 모든 레플리카에서 돌아야 하는데 isLeader() 가드가 붙었습니다."
              + " 리더가 아닌 파드의 캐시가 낡으면 어드미션 웹훅이 틀린 답을 합니다.")
          .doesNotContain("isLeader()");
    }
  }

  private static Set<String> findSelfSchedulingClasses() throws IOException {
    Set<String> result = new TreeSet<>();
    try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
      for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        String source = Files.readString(path, StandardCharsets.UTF_8);
        if (SELF_SCHEDULING_MARKERS.stream().anyMatch(source::contains)) {
          String fileName = path.getFileName().toString();
          result.add(fileName.substring(0, fileName.length() - ".java".length()));
        }
      }
    }
    return result;
  }

  private static String readSource(String className) throws IOException {
    try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
      Path path = paths
          .filter(p -> p.getFileName().toString().equals(className + ".java"))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException(className + " 소스를 찾지 못했습니다"));
      return Files.readString(path, StandardCharsets.UTF_8);
    }
  }

}
