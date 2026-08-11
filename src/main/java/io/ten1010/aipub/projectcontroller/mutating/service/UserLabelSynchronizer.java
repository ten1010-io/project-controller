package io.ten1010.aipub.projectcontroller.mutating.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.ApiClient;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@Slf4j
public class UserLabelSynchronizer {

  private static final long SYNC_INTERVAL_MS = 60_000; // 1 minute
  private static final String LOG_PREFIX = "[USER-LABEL-SYNC]";
  private static final int PAGE_LIMIT = 500;
  private static final int MAX_PAGES = 1_000;

  /**
   * Sync targets — must stay 1:1 with the workload-label webhook rules
   * (kubernetes/controller/project-controller/templates/java-webhook-configuration.yaml).
   * Intermediate workload objects come first and pods last: pods born mid-cycle copy
   * their parent's labels via the mutating webhook, so parents must be corrected first.
   */
  private static final List<SyncTarget> SYNC_TARGETS = List.of(
      new SyncTarget("", "v1", "replicationcontrollers"),
      new SyncTarget("apps", "v1", "statefulsets"),
      new SyncTarget("apps", "v1", "deployments"),
      new SyncTarget("apps", "v1", "replicasets"),
      new SyncTarget("apps", "v1", "daemonsets"),
      new SyncTarget("batch", "v1", "jobs"),
      new SyncTarget("batch", "v1", "cronjobs"),
      new SyncTarget("", "v1", "pods"));

  private final ApiResourceDiscovery apiResourceDiscovery;
  private final ApiClient apiClient;
  private final ObjectMapper mapper;
  private final ScheduledExecutorService scheduler;

  public UserLabelSynchronizer(ApiResourceDiscovery apiResourceDiscovery, ApiClient apiClient) {
    this.apiResourceDiscovery = apiResourceDiscovery;
    this.apiClient = apiClient;
    this.mapper = new ObjectMapperFactory().createObjectMapper();
    this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "user-label-synchronizer");
      t.setDaemon(true);
      return t;
    });
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    log.debug("{} Application ready, starting UserLabelSynchronizer with interval {}ms",
        LOG_PREFIX, SYNC_INTERVAL_MS);
    this.scheduler.scheduleWithFixedDelay(this::sync, 0, SYNC_INTERVAL_MS, TimeUnit.MILLISECONDS);
  }

  void sync() {
    long startNanos = System.nanoTime();
    log.debug("{} Sync cycle started", LOG_PREFIX);
    try {
      CycleResult result = run();
      Counters counters = result.total();
      long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
      log.debug("{} Sync cycle done: objects={}, processed={}, skippedNoWorkloadLabel={}, "
              + "ownerLookupFailed={}, alreadyInSync={}, patched={}, patchFailed={}, "
              + "listFailed={}, targetFailed={}, elapsedMs={}, breakdown=[{}]",
          LOG_PREFIX, counters.totalObjects, counters.processed, counters.skippedNoWorkloadLabel,
          counters.ownerLookupFailed, counters.alreadyInSync, counters.patched,
          counters.patchFailed, counters.listFailed, counters.targetFailed, elapsedMs,
          result.breakdown());
    } catch (Exception e) {
      long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
      log.warn("{} Sync cycle failed after {}ms", LOG_PREFIX, elapsedMs, e);
    }
  }

  private CycleResult run() {
    Counters total = new Counters();
    // Owner maps shared across all sync targets within a single cycle: one paginated
    // LIST per owner kind instead of one GET per owner object.
    OwnerCache ownerCache = new OwnerCache();
    StringBuilder breakdown = new StringBuilder();

    for (SyncTarget target : SYNC_TARGETS) {
      Counters counters;
      try {
        counters = processTarget(target, ownerCache);
      } catch (Exception e) {
        log.warn("{} {} target processing failed unexpectedly — target skipped",
            LOG_PREFIX, target.gvr(), e);
        counters = new Counters();
        counters.targetFailed++;
      }
      total.add(counters);
      if (breakdown.length() > 0) {
        breakdown.append(", ");
      }
      breakdown.append(target.gvr()).append(counters.toBreakdownEntry());
    }

    log.debug("{} Owner kinds this cycle: loaded={}, loadFailed={}",
        LOG_PREFIX, ownerCache.mapsByKind.size(), ownerCache.failedKinds.size());
    return new CycleResult(total, breakdown.toString());
  }

  private Counters processTarget(SyncTarget target, OwnerCache ownerCache) {
    Counters c = new Counters();

    List<JsonNode> items = listObjectsWithWorkloadLabel(target);
    if (items == null) {
      log.warn("{} {} list fetch returned null — target skipped", LOG_PREFIX, target.gvr());
      c.listFailed++;
      return c;
    }
    c.totalObjects = items.size();
    log.debug("{} Listed {} {} with workload-kind label", LOG_PREFIX, c.totalObjects, target.gvr());

    for (JsonNode object : items) {
      String namespace = object.path("metadata").path("namespace").textValue();
      String objectName = object.path("metadata").path("name").textValue();
      if (namespace == null || objectName == null) {
        c.skippedNoWorkloadLabel++;
        continue;
      }

      JsonNode labels = object.path("metadata").path("labels");
      if (!labels.isObject()) {
        log.debug("{} Skip {} {}/{}: no labels object",
            LOG_PREFIX, target.gvr(), namespace, objectName);
        c.skippedNoWorkloadLabel++;
        continue;
      }

      JsonNode kindNode = labels.get(LabelConstants.WORKLOAD_KIND_KEY);
      JsonNode nameNode = labels.get(LabelConstants.WORKLOAD_NAME_KEY);
      if (kindNode == null || nameNode == null) {
        log.debug("{} Skip {} {}/{}: missing workload-kind/workload-name label",
            LOG_PREFIX, target.gvr(), namespace, objectName);
        c.skippedNoWorkloadLabel++;
        continue;
      }
      String kind = kindNode.textValue();
      String name = nameNode.textValue();
      if (kind == null || name == null) {
        log.debug("{} Skip {} {}/{}: workload-kind/workload-name label has null text",
            LOG_PREFIX, target.gvr(), namespace, objectName);
        c.skippedNoWorkloadLabel++;
        continue;
      }

      c.processed++;
      log.debug("{} Processing {} {}/{}: owner={}/{}",
          LOG_PREFIX, target.gvr(), namespace, objectName, kind, name);

      loadOwnerKindIfNeeded(kind, ownerCache);
      String @Nullable [] ownerLabels = ownerCache.lookup(kind, namespace, name);
      if (ownerLabels == null) {
        c.ownerLookupFailed++;
        if (ownerCache.isFailed(kind)) {
          log.debug("{} Owner lookup skipped for {} {}/{}: owner kind={} load failed — "
                  + "patch skipped",
              LOG_PREFIX, target.gvr(), namespace, objectName, kind);
        } else {
          log.warn("{} Owner lookup failed for {} {}/{}: owner={}/{} not found — patch skipped",
              LOG_PREFIX, target.gvr(), namespace, objectName, kind, name);
        }
        continue;
      }

      SyncResult result = syncObjectIfNeeded(target, labels, objectName, namespace, ownerLabels);
      switch (result) {
        case ALREADY_IN_SYNC -> c.alreadyInSync++;
        case PATCHED -> c.patched++;
        case PATCH_FAILED -> c.patchFailed++;
      }
    }

    return c;
  }

  private SyncResult syncObjectIfNeeded(SyncTarget target, JsonNode labels, String objectName,
      String namespace, String[] ownerLabels) {
    String currentUsername = getTextValue(labels, LabelConstants.OBJECT_OWN_USERNAME_KEY);
    String currentUserid = getTextValue(labels, LabelConstants.OBJECT_OWN_USERID_KEY);

    if (Objects.equals(ownerLabels[0], currentUsername)
        && Objects.equals(ownerLabels[1], currentUserid)) {
      log.debug("{} {} {}/{} already in sync (username={}, userid={})",
          LOG_PREFIX, target.gvr(), namespace, objectName, currentUsername, currentUserid);
      return SyncResult.ALREADY_IN_SYNC;
    }

    log.debug("{} Patching {} {}/{}: username=[{}→{}], userid=[{}→{}]",
        LOG_PREFIX, target.gvr(), namespace, objectName,
        currentUsername, ownerLabels[0], currentUserid, ownerLabels[1]);
    return patchObjectLabels(target, objectName, namespace, ownerLabels[0], ownerLabels[1]);
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
   * Loads the owner map for the given kind once per cycle via cluster-scoped LISTs.
   *
   * <p>Referenced owners are always root objects WITHOUT the workload-kind label: the
   * stamping webhook copies a parent's workload-kind/name labels verbatim when the parent
   * carries them, so a workload-kind/name reference can only ever point at an object that
   * has no workload-kind label itself. This lets us fetch all possible owners of a kind
   * with a single label-absence LIST (labelSelector=!workload-kind) instead of one GET
   * per owner object.
   *
   * <p>Any failure (all candidate LISTs failing, or an unexpected exception) marks the
   * kind as failed in the cache so later targets hitting the same kind neither retry nor
   * re-throw — objects referencing a failed kind are counted as ownerLookupFailed.
   */
  private void loadOwnerKindIfNeeded(String kind, OwnerCache ownerCache) {
    if (ownerCache.isLoaded(kind)) {
      return;
    }
    try {
      List<ApiResourceDiscovery.ResourceInfo> resources =
          this.apiResourceDiscovery.getResourcesByKind(kind);
      if (resources.isEmpty()) {
        log.warn("{} Owner load: no API resources found for kind={} (discovery snapshot miss)",
            LOG_PREFIX, kind);
        ownerCache.mapsByKind.put(kind, Map.of());
        return;
      }
      log.debug("{} Owner load for kind={}: trying {} candidate resource(s)",
          LOG_PREFIX, kind, resources.size());

      Map<String, String @Nullable []> ownerMap = new HashMap<>();
      boolean anyListed = false;
      for (ApiResourceDiscovery.ResourceInfo resourceInfo : resources) {
        String apiVersion = resourceInfo.apiVersion();
        String plural = resourceInfo.plural();
        String group = apiVersion.contains("/") ? apiVersion.split("/")[0] : "";
        String groupResource = group + "/" + plural;

        boolean namespaced;
        try {
          namespaced = this.apiResourceDiscovery.isNamespaced(groupResource);
        } catch (GroupResourceNotFoundException e) {
          log.debug("{} Owner load: groupResource={} not in discovery — skipping candidate",
              LOG_PREFIX, groupResource);
          continue;
        }
        if (!namespaced) {
          // Root workloads are always namespaced; owner references are namespace-scoped,
          // so a non-namespaced candidate can never be a referenced owner.
          log.debug("{} Owner load: groupResource={} is not namespaced — skipping candidate",
              LOG_PREFIX, groupResource);
          continue;
        }

        String prefix = group.isEmpty() ? "/api/" + apiVersion : "/apis/" + apiVersion;
        String basePath = prefix + "/" + plural + "?labelSelector="
            + URLEncoder.encode("!" + LabelConstants.WORKLOAD_KIND_KEY, StandardCharsets.UTF_8);
        log.debug("{} Owner load attempt: LIST {}", LOG_PREFIX, basePath);
        List<JsonNode> items = listAllPages(basePath);
        if (items == null) {
          log.debug("{} Owner load LIST failed: {}", LOG_PREFIX, basePath);
          continue;
        }
        anyListed = true;
        for (JsonNode item : items) {
          String namespace = item.path("metadata").path("namespace").textValue();
          String name = item.path("metadata").path("name").textValue();
          if (namespace == null || name == null) {
            continue;
          }
          JsonNode labels = item.path("metadata").path("labels");
          if (!labels.isObject()) {
            // Not stored → lookup returns null → same skip semantics as the previous
            // per-object GET path ("owner has no labels object").
            continue;
          }
          String username = getTextValue(labels, LabelConstants.OBJECT_OWN_USERNAME_KEY);
          String userid = getTextValue(labels, LabelConstants.OBJECT_OWN_USERID_KEY);
          // putIfAbsent: earlier candidates win, preserving the previous GET try-order.
          ownerMap.putIfAbsent(namespace + "::" + name, new String[]{username, userid});
        }
      }

      if (!anyListed) {
        log.warn("{} Owner load failed for kind={}: no candidate LIST succeeded — "
                + "objects referencing this kind are skipped this cycle",
            LOG_PREFIX, kind);
        ownerCache.failedKinds.add(kind);
        return;
      }
      log.debug("{} Owner map loaded for kind={}: {} entries", LOG_PREFIX, kind, ownerMap.size());
      ownerCache.mapsByKind.put(kind, ownerMap);
    } catch (Exception e) {
      log.warn("{} Owner load for kind={} threw unexpectedly — kind marked failed for this cycle",
          LOG_PREFIX, kind, e);
      ownerCache.failedKinds.add(kind);
    }
  }

  @Nullable
  private List<JsonNode> listObjectsWithWorkloadLabel(SyncTarget target) {
    String labelSelector = URLEncoder.encode(LabelConstants.WORKLOAD_KIND_KEY, StandardCharsets.UTF_8);
    String path = target.apiPrefix() + "/" + target.plural() + "?labelSelector=" + labelSelector;
    return listAllPages(path);
  }

  /**
   * Paginated LIST: repeatedly GETs the given path with limit={@value #PAGE_LIMIT}
   * (and continue=&lt;token&gt;, URL-encoded, from the second page on), accumulating
   * items until metadata.continue is empty.
   *
   * @param basePath list path including its query string (e.g. ?labelSelector=...)
   * @return all items across pages, or null if ANY page fails — a partial result must
   *     never be treated as a complete one
   */
  @Nullable
  private List<JsonNode> listAllPages(String basePath) {
    List<JsonNode> items = new ArrayList<>();
    String continueToken = null;
    // A misbehaving API server (e.g. an aggregated server backing an arbitrary owner
    // CRD) could return continue tokens forever; without a guard the single-threaded
    // synchronizer would wedge permanently and accumulate items unboundedly.
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

  private SyncResult patchObjectLabels(SyncTarget target, String name, String namespace,
      @Nullable String username, @Nullable String userid) {
    String path = target.apiPrefix() + "/namespaces/" + namespace + "/" + target.plural()
        + "/" + name;
    try {
      Map<String, Object> labels = new HashMap<>();
      labels.put(LabelConstants.OBJECT_OWN_USERNAME_KEY, username);
      labels.put(LabelConstants.OBJECT_OWN_USERID_KEY, userid);
      // Merge-patch of metadata.labels only — spec/spec.template must never be included
      // (patching spec.template would trigger rolling restarts of workloads).
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
          log.debug("{} Patched {} {}/{} (status={})",
              LOG_PREFIX, target.gvr(), namespace, name, response.code());
          return SyncResult.PATCHED;
        }
        if (response.code() == 404) {
          log.debug("{} {} {}/{} not found for patch (already deleted?)",
              LOG_PREFIX, target.gvr(), namespace, name);
          return SyncResult.PATCH_FAILED;
        }
        String errorBody = response.body() != null ? response.body().string() : "";
        log.warn("{} Failed to patch {} {}/{}: status={}, body={}",
            LOG_PREFIX, target.gvr(), namespace, name, response.code(), errorBody);
        return SyncResult.PATCH_FAILED;
      }
    } catch (Exception e) {
      log.warn("{} Failed to patch {} {}/{}", LOG_PREFIX, target.gvr(), namespace, name, e);
      return SyncResult.PATCH_FAILED;
    }
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
          if (response.code() == 404) {
            return null;
          }
          String errorBody = response.body() != null ? response.body().string() : "";
          log.warn("{} fetchJson failed: path={}, status={}, body={}",
              LOG_PREFIX, path, response.code(), errorBody);
          return null;
        }
        if (response.body() == null) {
          return null;
        }
        return this.mapper.readTree(response.body().string());
      }
    } catch (Exception e) {
      log.warn("{} fetchJson threw exception: path={}", LOG_PREFIX, path, e);
      return null;
    }
  }

  private record SyncTarget(String group, String version, String plural) {

    String apiPrefix() {
      if (this.group.isEmpty()) {
        return "/api/" + this.version;
      }
      return "/apis/" + this.group + "/" + this.version;
    }

    String gvr() {
      if (this.group.isEmpty()) {
        return this.version + "/" + this.plural;
      }
      return this.group + "/" + this.version + "/" + this.plural;
    }
  }

  private record CycleResult(Counters total, String breakdown) {
  }

  /**
   * Per-cycle owner state: user labels of root workload objects, loaded once per kind.
   * A kind is in exactly one of three states — not loaded (absent from both fields),
   * loaded ({@link #mapsByKind} entry, possibly empty), or load-failed
   * ({@link #failedKinds} entry).
   */
  private static final class OwnerCache {

    /** kind → (namespace::name → {username, userid}). */
    final Map<String, Map<String, String @Nullable []>> mapsByKind = new HashMap<>();
    /** Kinds whose owner map load failed — cached so later targets neither retry nor re-throw. */
    final Set<String> failedKinds = new HashSet<>();

    boolean isLoaded(String kind) {
      return this.mapsByKind.containsKey(kind) || this.failedKinds.contains(kind);
    }

    boolean isFailed(String kind) {
      return this.failedKinds.contains(kind);
    }

    String @Nullable [] lookup(String kind, String namespace, String name) {
      Map<String, String @Nullable []> ownerMap = this.mapsByKind.get(kind);
      if (ownerMap == null) {
        return null;
      }
      return ownerMap.get(namespace + "::" + name);
    }

  }

  private enum SyncResult {
    ALREADY_IN_SYNC,
    PATCHED,
    PATCH_FAILED,
  }

  private static final class Counters {
    int totalObjects;
    int processed;
    int skippedNoWorkloadLabel;
    int ownerLookupFailed;
    int alreadyInSync;
    int patched;
    int patchFailed;
    int listFailed;
    int targetFailed;

    void add(Counters other) {
      this.totalObjects += other.totalObjects;
      this.processed += other.processed;
      this.skippedNoWorkloadLabel += other.skippedNoWorkloadLabel;
      this.ownerLookupFailed += other.ownerLookupFailed;
      this.alreadyInSync += other.alreadyInSync;
      this.patched += other.patched;
      this.patchFailed += other.patchFailed;
      this.listFailed += other.listFailed;
      this.targetFailed += other.targetFailed;
    }

    String toBreakdownEntry() {
      return "{total=" + this.totalObjects
          + ", processed=" + this.processed
          + ", skipped=" + this.skippedNoWorkloadLabel
          + ", ownerLookupFailed=" + this.ownerLookupFailed
          + ", alreadyInSync=" + this.alreadyInSync
          + ", patched=" + this.patched
          + ", patchFailed=" + this.patchFailed
          + ", listFailed=" + this.listFailed
          + ", targetFailed=" + this.targetFailed + "}";
    }
  }

}
