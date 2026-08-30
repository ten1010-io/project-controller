package io.ten1010.aipub.projectcontroller.mutating.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.ApiClient;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import io.ten1010.aipub.projectcontroller.domain.k8s.ProjectApiConstants;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * ClusterVolume(CV) 의 소유자 라벨(username/userid)을 CV 컨트롤러가 만든 자식(복제/앵커 PVC·PV)에
 * 주기적으로 미러링한다. 자식은 {@code clustervolumes.aipub.ten1010.io/owner=<CV명>} 라벨로 부모를
 * 가리키며, 매 주기 "자식의 두 소유자 라벨 = 부모 CV 의 두 소유자 라벨" 로 수렴시킨다 — CV 에 없는
 * 키는 자식에서도 제거한다.
 *
 * <p>userrelationship 웹훅이 자식 CREATE 시점에 한 번 라벨을 찍지만, 그 뒤 CV 소유권이
 * 이전(transfer)되거나 자식이 사후 라벨링(CSI 가 만든 앵커 PV)·재생성(자가치유)되면 웹훅으로는
 * 따라갈 수 없다. {@link UserLabelSynchronizer} 와 같은 주기 전량 스캔 방식이라 이벤트 유실·기동
 * 타이밍과 무관하게 한 주기 안에 수렴이 보장되고, 기존 자식에도 소급 적용된다.
 *
 * <p>allowlist 네임스페이스 안의 PVC 는 웹훅·개인 Role 리컨실러와 같은 규칙으로 건너뛴다.
 * owner 라벨이 가리키는 CV 가 없으면(삭제 중·stale) 자식은 CV finalizer/GC 가 정리하므로
 * 건드리지 않는다. 편입 원본 PVC 는 owner 대신 claimed-by 라벨이라 LIST 셀렉터에 걸리지 않는다.
 */
@Slf4j
public class ClusterVolumeChildLabelSynchronizer {

  private static final long SYNC_INTERVAL_MS = 60_000; // 1 minute
  private static final String LOG_PREFIX = "[CV-CHILD-LABEL-SYNC]";
  private static final int PAGE_LIMIT = 500;
  private static final int MAX_PAGES = 1_000;

  private static final String CLUSTER_VOLUME_LIST_PATH =
      "/apis/" + ProjectApiConstants.AIPUB_API_VERSION + "/"
          + ProjectApiConstants.CLUSTER_VOLUME_RESOURCE_PLURAL;
  private static final String OWNER_LABEL_SELECTOR =
      URLEncoder.encode(LabelConstants.CLUSTER_VOLUME_OWNER_KEY, StandardCharsets.UTF_8);

  /** 자식 타깃. PVC 는 namespaced, PV 는 cluster-scoped — LIST/PATCH 경로가 갈린다. */
  private record SyncTarget(String plural, boolean namespaced) {
  }

  private static final List<SyncTarget> SYNC_TARGETS = List.of(
      new SyncTarget("persistentvolumeclaims", true),
      new SyncTarget("persistentvolumes", false));

  private final ApiClient apiClient;
  private final NamespaceAllowlistResolver namespaceAllowlistResolver;
  private final ObjectMapper mapper;
  private final ScheduledExecutorService scheduler;

  public ClusterVolumeChildLabelSynchronizer(ApiClient apiClient,
      NamespaceAllowlistResolver namespaceAllowlistResolver) {
    this.apiClient = apiClient;
    this.namespaceAllowlistResolver = namespaceAllowlistResolver;
    this.mapper = new ObjectMapperFactory().createObjectMapper();
    this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "clustervolume-child-label-synchronizer");
      t.setDaemon(true);
      return t;
    });
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    log.debug("{} Application ready, starting ClusterVolumeChildLabelSynchronizer "
        + "with interval {}ms", LOG_PREFIX, SYNC_INTERVAL_MS);
    this.scheduler.scheduleWithFixedDelay(this::sync, 0, SYNC_INTERVAL_MS, TimeUnit.MILLISECONDS);
  }

  void sync() {
    long startNanos = System.nanoTime();
    log.debug("{} Sync cycle started", LOG_PREFIX);
    try {
      Counters c = run();
      long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
      log.debug("{} Sync cycle done: children={}, alreadyInSync={}, patched={}, patchFailed={}, "
              + "clusterVolumeNotFound={}, skippedAllowlisted={}, skippedTerminating={}, "
              + "listFailed={}, elapsedMs={}",
          LOG_PREFIX, c.totalChildren, c.alreadyInSync, c.patched, c.patchFailed,
          c.clusterVolumeNotFound, c.skippedAllowlisted, c.skippedTerminating, c.listFailed,
          elapsedMs);
    } catch (Exception e) {
      long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
      log.warn("{} Sync cycle failed after {}ms", LOG_PREFIX, elapsedMs, e);
    }
  }

  private Counters run() {
    Counters c = new Counters();

    // CV 전량을 한 번 LIST 해 이름 → {username, userid} 맵을 만든다 (자식마다 GET 하지 않는다).
    // LIST 실패 시 이번 주기는 아무것도 패치하지 않는다 — 부분 정보로 라벨을 제거하면 안 된다.
    Map<String, String @Nullable []> ownerLabelsByClusterVolume = loadClusterVolumeOwnerLabels();
    if (ownerLabelsByClusterVolume == null) {
      log.warn("{} ClusterVolume list fetch failed — cycle skipped", LOG_PREFIX);
      c.listFailed++;
      return c;
    }

    for (SyncTarget target : SYNC_TARGETS) {
      try {
        processTarget(target, ownerLabelsByClusterVolume, c);
      } catch (Exception e) {
        // 타깃 격리(UserLabelSynchronizer 와 동일) — 한 타깃의 예상외 예외가 남은 타깃 처리를
        // 막지 않는다. 다음 주기에 재시도된다
        log.warn("{} {} target processing failed unexpectedly — target skipped",
            LOG_PREFIX, target.plural(), e);
        c.listFailed++;
      }
    }
    return c;
  }

  private void processTarget(SyncTarget target,
      Map<String, String @Nullable []> ownerLabelsByClusterVolume, Counters c) {
    String path = "/api/v1/" + target.plural() + "?labelSelector=" + OWNER_LABEL_SELECTOR;
    List<JsonNode> children = listAllPages(path);
    if (children == null) {
      log.warn("{} {} list fetch failed — target skipped", LOG_PREFIX, target.plural());
      c.listFailed++;
      return;
    }
    c.totalChildren += children.size();

    for (JsonNode child : children) {
      JsonNode metadata = child.path("metadata");
      String name = metadata.path("name").textValue();
      String namespace = metadata.path("namespace").textValue();
      if (name == null || (target.namespaced() && namespace == null)) {
        continue;
      }
      if (metadata.path("deletionTimestamp").isTextual()) {
        c.skippedTerminating++;
        continue;
      }
      if (target.namespaced() && this.namespaceAllowlistResolver.isAllowlisted(namespace)) {
        c.skippedAllowlisted++;
        continue;
      }

      JsonNode labels = metadata.path("labels");
      String ownerName = getTextValue(labels, LabelConstants.CLUSTER_VOLUME_OWNER_KEY);
      if (ownerName == null) {
        // LIST 셀렉터가 owner 존재를 보장하므로 도달하지 않는 방어 분기
        continue;
      }
      String @Nullable [] desired = ownerLabelsByClusterVolume.get(ownerName);
      if (desired == null) {
        // 부모 CV 없음 — 삭제 중(자식은 finalizer/GC 가 정리)이거나 stale 라벨. 건드리지 않는다
        log.debug("{} Skip {} {}/{}: ClusterVolume {} not found",
            LOG_PREFIX, target.plural(), namespace, name, ownerName);
        c.clusterVolumeNotFound++;
        continue;
      }

      String currentUsername = getTextValue(labels, LabelConstants.OBJECT_OWN_USERNAME_KEY);
      String currentUserid = getTextValue(labels, LabelConstants.OBJECT_OWN_USERID_KEY);
      if (Objects.equals(desired[0], currentUsername)
          && Objects.equals(desired[1], currentUserid)) {
        c.alreadyInSync++;
        continue;
      }

      log.info("{} Patching {} {}/{}: clusterVolume={}, username=[{}→{}], userid=[{}→{}]",
          LOG_PREFIX, target.plural(), namespace, name, ownerName,
          currentUsername, desired[0], currentUserid, desired[1]);
      if (patchChildLabels(target, namespace, name, desired[0], desired[1])) {
        c.patched++;
      } else {
        c.patchFailed++;
      }
    }
  }

  /**
   * CV 이름 → {username, userid} (없는 키는 null 요소 — 자식에서 제거해야 하는 상태).
   *
   * @return LIST 실패 시 null
   */
  @Nullable
  private Map<String, String @Nullable []> loadClusterVolumeOwnerLabels() {
    List<JsonNode> clusterVolumes = listAllPages(CLUSTER_VOLUME_LIST_PATH);
    if (clusterVolumes == null) {
      return null;
    }
    Map<String, String @Nullable []> map = new HashMap<>();
    for (JsonNode clusterVolume : clusterVolumes) {
      String name = clusterVolume.path("metadata").path("name").textValue();
      if (name == null) {
        continue;
      }
      JsonNode labels = clusterVolume.path("metadata").path("labels");
      map.put(name, new String[]{
          getTextValue(labels, LabelConstants.OBJECT_OWN_USERNAME_KEY),
          getTextValue(labels, LabelConstants.OBJECT_OWN_USERID_KEY)});
    }
    return map;
  }

  private boolean patchChildLabels(SyncTarget target, @Nullable String namespace, String name,
      @Nullable String username, @Nullable String userid) {
    String path = target.namespaced()
        ? "/api/v1/namespaces/" + namespace + "/" + target.plural() + "/" + name
        : "/api/v1/" + target.plural() + "/" + name;
    try {
      // merge-patch 로 metadata.labels 의 두 키만 갱신한다(null = 제거) — CV 컨트롤러의
      // owner 라벨·spec 은 건드리지 않는다
      Map<String, Object> labels = new HashMap<>();
      labels.put(LabelConstants.OBJECT_OWN_USERNAME_KEY, username);
      labels.put(LabelConstants.OBJECT_OWN_USERID_KEY, userid);
      byte[] bodyBytes = this.mapper.writeValueAsBytes(
          Map.of("metadata", Map.of("labels", labels)));

      Call call = this.apiClient.buildCall(
          this.apiClient.getBasePath(), path, "PATCH",
          List.of(), List.of(),
          bodyBytes,
          Map.of("Content-Type", "application/merge-patch+json"),
          Map.of(), Map.of(),
          new String[]{"BearerToken"}, null);

      try (Response response = call.execute()) {
        if (response.isSuccessful()) {
          return true;
        }
        if (response.code() == 404) {
          log.debug("{} {} {}/{} not found for patch (already deleted?)",
              LOG_PREFIX, target.plural(), namespace, name);
          return false;
        }
        String errorBody = response.body() != null ? response.body().string() : "";
        log.warn("{} Failed to patch {} {}/{}: status={}, body={}",
            LOG_PREFIX, target.plural(), namespace, name, response.code(), errorBody);
        return false;
      }
    } catch (Exception e) {
      log.warn("{} Failed to patch {} {}/{}", LOG_PREFIX, target.plural(), namespace, name, e);
      return false;
    }
  }

  @Nullable
  private String getTextValue(JsonNode node, String key) {
    JsonNode value = node.get(key);
    if (value == null) {
      return null;
    }
    return value.textValue();
  }

  /**
   * 페이지네이션 LIST — {@link UserLabelSynchronizer} 와 동일한 계약: 한 페이지라도 실패하면
   * null(부분 결과를 완전한 결과로 취급하지 않는다), continue 토큰 반복·페이지 초과 시 중단.
   */
  @Nullable
  private List<JsonNode> listAllPages(String basePath) {
    List<JsonNode> items = new ArrayList<>();
    String continueToken = null;
    for (int page = 1; page <= MAX_PAGES; page++) {
      String path = basePath + (basePath.contains("?") ? "&" : "?") + "limit=" + PAGE_LIMIT;
      if (continueToken != null) {
        path = path + "&continue=" + URLEncoder.encode(continueToken, StandardCharsets.UTF_8);
      }
      JsonNode pageNode = fetchJson(path);
      if (pageNode == null) {
        return null;
      }
      JsonNode pageItems = pageNode.path("items");
      if (!pageItems.isArray()) {
        log.warn("{} listAllPages: items not array — path={}", LOG_PREFIX, path);
        return null;
      }
      for (JsonNode item : pageItems) {
        items.add(item);
      }
      String nextToken = pageNode.path("metadata").path("continue").textValue();
      if (nextToken == null || nextToken.isEmpty()) {
        return items;
      }
      if (nextToken.equals(continueToken)) {
        log.warn("{} listAllPages: continue token repeated — aborting to avoid a loop, path={}",
            LOG_PREFIX, basePath);
        return null;
      }
      continueToken = nextToken;
    }
    log.warn("{} listAllPages: exceeded {} pages — aborting, path={}",
        LOG_PREFIX, MAX_PAGES, basePath);
    return null;
  }

  @Nullable
  private JsonNode fetchJson(String path) {
    try {
      Call call = this.apiClient.buildCall(
          this.apiClient.getBasePath(), path, "GET",
          List.of(), List.of(),
          null,
          Map.of(), Map.of(), Map.of(),
          new String[]{"BearerToken"}, null);
      try (Response response = call.execute()) {
        if (!response.isSuccessful()) {
          log.warn("{} GET {} failed: status={}", LOG_PREFIX, path, response.code());
          return null;
        }
        if (response.body() == null) {
          return null;
        }
        return this.mapper.readTree(response.body().string());
      }
    } catch (Exception e) {
      log.warn("{} GET {} failed", LOG_PREFIX, path, e);
      return null;
    }
  }

  private static final class Counters {

    int totalChildren;
    int alreadyInSync;
    int patched;
    int patchFailed;
    int clusterVolumeNotFound;
    int skippedAllowlisted;
    int skippedTerminating;
    int listFailed;

  }

}
