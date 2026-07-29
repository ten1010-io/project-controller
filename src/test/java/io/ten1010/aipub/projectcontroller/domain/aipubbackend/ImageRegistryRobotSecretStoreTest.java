package io.ten1010.aipub.projectcontroller.domain.aipubbackend;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImageRegistryRobotSecretStoreTest {

  private static final Duration TTL = Duration.ofMinutes(5);

  private static MutableClock clock() {
    return new MutableClock(Instant.parse("2026-07-29T12:00:00Z"));
  }

  @Test
  @DisplayName("보관한 secret 을 robotId 로 꺼낼 수 있다")
  void take_returnsStoredSecret() {
    ImageRegistryRobotSecretStore store = new ImageRegistryRobotSecretStore(TTL, clock());
    store.put("42", "harbor-secret");

    assertThat(store.take("42")).contains("harbor-secret");
  }

  @Test
  @DisplayName("secret 은 한 번만 꺼내진다 - 두 번째 take 는 비어 있다")
  void take_consumesEntry() {
    ImageRegistryRobotSecretStore store = new ImageRegistryRobotSecretStore(TTL, clock());
    store.put("42", "harbor-secret");

    assertThat(store.take("42")).contains("harbor-secret");
    assertThat(store.take("42")).isEmpty();
  }

  @Test
  @DisplayName("보관되지 않은 robotId 는 빈 값이다 - 호출자는 refreshsecret 으로 되돌아간다")
  void take_returnsEmptyForUnknownRobotId() {
    ImageRegistryRobotSecretStore store = new ImageRegistryRobotSecretStore(TTL, clock());

    assertThat(store.take("42")).isEmpty();
  }

  @Test
  @DisplayName("다른 robot 이 재생성돼 id 가 바뀌면 이전 robot 의 secret 은 쓰이지 않는다")
  void take_isKeyedByRobotId() {
    ImageRegistryRobotSecretStore store = new ImageRegistryRobotSecretStore(TTL, clock());
    store.put("42", "old-robot-secret");

    assertThat(store.take("43")).isEmpty();
  }

  @Test
  @DisplayName("같은 robotId 로 다시 넣으면 최신 secret 으로 덮어쓴다")
  void put_overwritesPreviousSecret() {
    ImageRegistryRobotSecretStore store = new ImageRegistryRobotSecretStore(TTL, clock());
    store.put("42", "first");
    store.put("42", "second");

    assertThat(store.take("42")).contains("second");
  }

  @Test
  @DisplayName("TTL 이 지난 secret 은 꺼내지지 않는다 - 외부에서 재발급됐을 수 있어 신뢰하지 않는다")
  void take_returnsEmptyForExpiredEntry() {
    MutableClock clock = clock();
    ImageRegistryRobotSecretStore store = new ImageRegistryRobotSecretStore(TTL, clock);
    store.put("42", "harbor-secret");

    clock.advance(TTL.plusSeconds(1));

    assertThat(store.take("42")).isEmpty();
  }

  @Test
  @DisplayName("TTL 이내라면 시간이 조금 흘러도 꺼내진다")
  void take_returnsSecretWithinTtl() {
    MutableClock clock = clock();
    ImageRegistryRobotSecretStore store = new ImageRegistryRobotSecretStore(TTL, clock);
    store.put("42", "harbor-secret");

    clock.advance(TTL.minusSeconds(1));

    assertThat(store.take("42")).contains("harbor-secret");
  }

  @Test
  @DisplayName("put 시점에 만료된 다른 항목들이 정리된다")
  void put_purgesExpiredEntries() {
    MutableClock clock = clock();
    ImageRegistryRobotSecretStore store = new ImageRegistryRobotSecretStore(TTL, clock);
    store.put("42", "stale");

    clock.advance(TTL.plusSeconds(1));
    store.put("43", "fresh");

    assertThat(store.take("42")).isEmpty();
    assertThat(store.take("43")).contains("fresh");
  }

  private static final class MutableClock extends Clock {

    private Instant now;

    private MutableClock(Instant now) {
      this.now = now;
    }

    private void advance(Duration amount) {
      this.now = this.now.plus(amount);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return this.now;
    }

  }

}
