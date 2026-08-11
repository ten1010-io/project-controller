package io.ten1010.aipub.projectcontroller.domain.aipubbackend;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * robot 생성 응답에만 담겨 오는 평문 secret 을, 생성 직후 K8s Secret 에 반영될 때까지 잠시 들고 있는 저장소.
 *
 * <p>robot 을 만드는 주체({@code ImageRegistryRobotReconciler})와 그 secret 을 K8s Secret 에 쓰는 주체
 * ({@code ImageRegistrySecretReconciler} → {@link AipubDockerConfigJsonResolver})가 서로 다른
 * reconciler 라서, 생성 응답의 secret 을 그대로 넘겨줄 호출 경로가 없다. 이 저장소가 그 사이를 잇는다.
 * 덕분에 robot 최초 생성 시 Harbor 왕복이 2회(create → refreshsecret)에서 1회로 줄어든다.
 *
 * <p>순수 최적화이며 저장소가 비어 있어도 동작은 정상이다. {@link #take} 가 비면 호출자는 기존대로
 * refreshsecret 으로 되돌아간다. 컨트롤러가 create 와 Secret 반영 사이에 재시작하면 저장소는 비고, 그때는
 * 예전과 같은 2회 왕복으로 처리된다.
 *
 * <p>보관 정책이 두 가지다. 첫째, {@link #take} 는 꺼내면서 지운다(consume-once). 같은 secret 을 두 번
 * 쓸 일이 없고, 남겨둘 이유도 없다. 둘째, {@link #DEFAULT_TTL} 이 지난 항목은 버린다. 외부(웹 UI 등)에서
 * refreshsecret 이 호출되면 여기 든 secret 은 조용히 무효가 되므로, 오래된 값을 K8s Secret 에 쓰는 것보다
 * refreshsecret 으로 새로 받는 편이 안전하다.
 */
public class ImageRegistryRobotSecretStore {

  private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

  private final Map<String, Entry> entries;
  private final Duration ttl;
  private final Clock clock;

  public ImageRegistryRobotSecretStore() {
    this(DEFAULT_TTL, Clock.systemUTC());
  }

  public ImageRegistryRobotSecretStore(Duration ttl, Clock clock) {
    this.entries = new ConcurrentHashMap<>();
    this.ttl = Objects.requireNonNull(ttl);
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * robot 생성 응답으로 받은 secret 을 보관한다. 같은 robotId 에 대한 기존 값은 덮어쓴다.
   */
  public void put(String robotId, String secret) {
    Objects.requireNonNull(robotId);
    Objects.requireNonNull(secret);
    purgeExpired();
    this.entries.put(robotId, new Entry(secret, this.clock.instant()));
  }

  /**
   * 보관된 secret 을 꺼내면서 지운다. 없거나 TTL 이 지났으면 빈 값을 준다.
   */
  public Optional<String> take(String robotId) {
    Objects.requireNonNull(robotId);
    Entry entry = this.entries.remove(robotId);
    if (entry == null || isExpired(entry)) {
      return Optional.empty();
    }
    return Optional.of(entry.secret());
  }

  private void purgeExpired() {
    this.entries.values().removeIf(this::isExpired);
  }

  private boolean isExpired(Entry entry) {
    return Duration.between(entry.storedAt(), this.clock.instant()).compareTo(this.ttl) > 0;
  }

  private record Entry(String secret, Instant storedAt) {
  }

}
