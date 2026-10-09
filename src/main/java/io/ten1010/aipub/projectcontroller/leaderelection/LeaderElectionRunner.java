package io.ten1010.aipub.projectcontroller.leaderelection;

import io.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.kubernetes.client.extended.leaderelection.LeaderElector;
import io.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import io.kubernetes.client.openapi.ApiClient;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * 컨트롤러 루프를 리더 파드 하나에서만 돌린다.
 *
 * <p>리컨실러가 둘 이상의 파드에서 같은 오브젝트를 처리하면 robot 비밀번호 재발급이 경쟁한다. 재발급은 직전 비밀번호를 무효화하므로, 한 파드가 받은 값이
 * 다른 파드의 값으로 덮이면 K8s Secret 과 Harbor 가 어긋난 채 고정된다. replica 가 1이어도 RollingUpdate 구간에는 두 파드가 겹친다.
 */
@Slf4j
public class LeaderElectionRunner {

  private static final String LEASE_NAME = "project-controller";

  /** k8s 컨트롤러 기본값. 리더가 죽으면 최대 leaseDuration 만큼 뒤에 다른 파드가 승격한다. */
  private static final Duration LEASE_DURATION = Duration.ofSeconds(15);

  private static final Duration RENEW_DEADLINE = Duration.ofSeconds(10);
  private static final Duration RETRY_PERIOD = Duration.ofSeconds(2);

  private static final String NAMESPACE_ENV = "POD_NAMESPACE";
  private static final String IDENTITY_ENV = "POD_NAME";

  /** ServiceAccount 토큰과 함께 마운트된다. 차트에 downward API 를 넣지 않아도 네임스페이스를 알 수 있다. */
  private static final Path SERVICE_ACCOUNT_NAMESPACE_PATH =
      Path.of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");

  private final ApiClient apiClient;
  private final String namespace;
  private final String identity;
  private final LeadershipState leadershipState;

  public LeaderElectionRunner(ApiClient apiClient, LeadershipState leadershipState) {
    this(apiClient,
        resolveNamespace(System.getenv(NAMESPACE_ENV), SERVICE_ACCOUNT_NAMESPACE_PATH),
        resolveIdentity(System.getenv(IDENTITY_ENV), LeaderElectionRunner::hostname),
        leadershipState);
  }

  LeaderElectionRunner(ApiClient apiClient, String namespace, String identity,
      LeadershipState leadershipState) {
    this.apiClient = Objects.requireNonNull(apiClient);
    this.namespace = Objects.requireNonNull(namespace);
    this.identity = Objects.requireNonNull(identity);
    this.leadershipState = Objects.requireNonNull(leadershipState);
  }

  /**
   * 리스를 잡을 때까지 블로킹하고, 잡으면 {@code leaderWork} 를 돌린다.
   *
   * <p>리스를 잃으면 프로세스를 끝낸다. {@link LeaderElector} 는 리스를 잃어도 돌던 작업을 취소해 주지 않으므로, 로그만 남기면 리스 없이 계속
   * 리컨실하게 돼 막으려던 경쟁이 그대로 일어난다.
   */
  public void runWhenLeader(Runnable leaderWork) {
    Objects.requireNonNull(leaderWork);
    LeaseLock lock = new LeaseLock(this.namespace, LEASE_NAME, this.identity, this.apiClient);
    LeaderElectionConfig config =
        new LeaderElectionConfig(lock, LEASE_DURATION, RENEW_DEADLINE, RETRY_PERIOD);

    log.info("Waiting for leader lease [namespace={}, name={}, identity={}]",
        this.namespace, LEASE_NAME, this.identity);
    try (LeaderElector elector = new LeaderElector(config)) {
      // onStartedLeading 은 elector 의 별도 스레드에서 돌아 블로킹해도 갱신이 멈추지 않는다.
      // onStoppedLeading 은 갱신 루프가 끝난 뒤 이 스레드에서 인라인으로 돈다.
      elector.run(
          () -> {
            log.info("Acquired leader lease, starting controllers [identity={}]", this.identity);
            // leaderWork 가 블로킹하므로 그 전에 세운다. 스스로 도는 주기 작업이 이걸 보고 쓰기를 시작한다
            this.leadershipState.markLeader();
            leaderWork.run();
          },
          () -> log.error("Lost leader lease [identity={}]", this.identity));
    } catch (RuntimeException e) {
      log.error("Leader election failed [identity={}]", this.identity, e);
    }
    exit();
  }

  /**
   * 리스 없이 계속 돌지 않도록 프로세스를 끝낸다. kubelet 이 컨테이너를 다시 띄우고, 그 파드는 처음부터 리스를 다시 겨룬다.
   */
  void exit() {
    log.error("Shutting down because the controller loop must not run without the leader lease");
    System.exit(1);
  }

  static String resolveNamespace(@Nullable String fromEnv, Path namespacePath) {
    if (fromEnv != null && !fromEnv.isBlank()) {
      return fromEnv.trim();
    }
    String fromFile;
    try {
      fromFile = Files.readString(namespacePath, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not read the namespace from " + namespacePath
              + ". Set " + NAMESPACE_ENV + " instead.", e);
    }
    if (fromFile.isBlank()) {
      throw new IllegalStateException(
          "Read an empty namespace from " + namespacePath
              + ". Set " + NAMESPACE_ENV + " instead.");
    }
    return fromFile.trim();
  }

  /** 파드 이름이면 충분하다. k8s 는 호스트명을 파드 이름으로 맞춰 주므로 차트 변경 없이 얻을 수 있다. */
  static String resolveIdentity(@Nullable String fromEnv, Supplier<String> hostnameSupplier) {
    if (fromEnv != null && !fromEnv.isBlank()) {
      return fromEnv.trim();
    }
    String hostname = hostnameSupplier.get();
    if (hostname == null || hostname.isBlank()) {
      throw new IllegalStateException(
          "Resolved an empty hostname for the leader election identity. Set "
              + IDENTITY_ENV + " instead.");
    }
    return hostname.trim();
  }

  private static String hostname() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      throw new IllegalStateException(
          "Could not resolve the hostname for the leader election identity. Set "
              + IDENTITY_ENV + " instead.", e);
    }
  }

}
