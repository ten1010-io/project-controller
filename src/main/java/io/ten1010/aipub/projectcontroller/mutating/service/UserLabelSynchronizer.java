package io.ten1010.aipub.projectcontroller.mutating.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.ApiClient;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@Slf4j
public class UserLabelSynchronizer {

  private static final long SYNC_INTERVAL_MS = 300_000; // 5 minutes
  private static final String LOG_PREFIX = "[USER-LABEL-SYNC]";

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
    log.info("{} Application ready, starting UserLabelSynchronizer with interval {}ms",
        LOG_PREFIX, SYNC_INTERVAL_MS);
    this.scheduler.scheduleWithFixedDelay(this::sync, 0, SYNC_INTERVAL_MS, TimeUnit.MILLISECONDS);
  }

  void sync() {
    long startNanos = System.nanoTime();
    log.info("{} Sync cycle started", LOG_PREFIX);
    try {
      Counters counters = run();
      long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
      log.info("{} Sync cycle done: pods={}, processed={}, skippedNoWorkloadLabel={}, "
              + "ownerLookupFailed={}, alreadyInSync={}, patched={}, patchFailed={}, elapsedMs={}",
          LOG_PREFIX, counters.totalPods, counters.processed, counters.skippedNoWorkloadLabel,
          counters.ownerLookupFailed, counters.alreadyInSync, counters.patched,
          counters.patchFailed, elapsedMs);
    } catch (Exception e) {
      long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
      log.warn("{} Sync cycle failed after {}ms", LOG_PREFIX, elapsedMs, e);
    }
  }

  private Counters run() {
    Counters c = new Counters();

    JsonNode podList = listPodsWithWorkloadLabel();
    if (podList == null) {
      log.warn("{} Pod list fetch returned null — sync cycle aborted", LOG_PREFIX);
      return c;
    }
    JsonNode items = podList.path("items");
    if (!items.isArray()) {
      log.warn("{} Pod list items not array — sync cycle aborted", LOG_PREFIX);
      return c;
    }
    c.totalPods = items.size();
    log.info("{} Listed {} pods with workload-kind label", LOG_PREFIX, c.totalPods);

    Map<String, String @Nullable []> cache = new HashMap<>();

    for (JsonNode pod : items) {
      String namespace = pod.path("metadata").path("namespace").textValue();
      String podName = pod.path("metadata").path("name").textValue();
      if (namespace == null || podName == null) {
        c.skippedNoWorkloadLabel++;
        continue;
      }

      JsonNode labels = pod.path("metadata").path("labels");
      if (!labels.isObject()) {
        log.info("{} Skip pod {}/{}: no labels object", LOG_PREFIX, namespace, podName);
        c.skippedNoWorkloadLabel++;
        continue;
      }

      JsonNode kindNode = labels.get(LabelConstants.WORKLOAD_KIND_KEY);
      JsonNode nameNode = labels.get(LabelConstants.WORKLOAD_NAME_KEY);
      if (kindNode == null || nameNode == null) {
        log.info("{} Skip pod {}/{}: missing workload-kind/workload-name label",
            LOG_PREFIX, namespace, podName);
        c.skippedNoWorkloadLabel++;
        continue;
      }
      String kind = kindNode.textValue();
      String name = nameNode.textValue();
      if (kind == null || name == null) {
        log.info("{} Skip pod {}/{}: workload-kind/workload-name label has null text",
            LOG_PREFIX, namespace, podName);
        c.skippedNoWorkloadLabel++;
        continue;
      }

      c.processed++;
      log.info("{} Processing pod {}/{}: owner={}/{}", LOG_PREFIX, namespace, podName, kind, name);

      String cacheKey = namespace + "::" + kind + "/" + name;
      String @Nullable [] ownerLabels;
      if (cache.containsKey(cacheKey)) {
        ownerLabels = cache.get(cacheKey);
        log.info("{} Owner cache hit for {} → username={}, userid={}",
            LOG_PREFIX, cacheKey,
            ownerLabels == null ? null : ownerLabels[0],
            ownerLabels == null ? null : ownerLabels[1]);
      } else {
        ownerLabels = getOwnerUserLabels(kind, name, namespace);
        cache.put(cacheKey, ownerLabels);
      }

      if (ownerLabels == null) {
        c.ownerLookupFailed++;
        log.warn("{} Owner lookup failed for pod {}/{}: owner={}/{} returned null — patch skipped",
            LOG_PREFIX, namespace, podName, kind, name);
        continue;
      }

      SyncResult result = syncPodIfNeeded(labels, podName, namespace, ownerLabels);
      switch (result) {
        case ALREADY_IN_SYNC -> c.alreadyInSync++;
        case PATCHED -> c.patched++;
        case PATCH_FAILED -> c.patchFailed++;
      }
    }

    return c;
  }

  private SyncResult syncPodIfNeeded(JsonNode labels, String podName, String namespace,
      String[] ownerLabels) {
    String currentUsername = getTextValue(labels, LabelConstants.OBJECT_OWN_USERNAME_KEY);
    String currentUserid = getTextValue(labels, LabelConstants.OBJECT_OWN_USERID_KEY);

    if (Objects.equals(ownerLabels[0], currentUsername)
        && Objects.equals(ownerLabels[1], currentUserid)) {
      log.info("{} Pod {}/{} already in sync (username={}, userid={})",
          LOG_PREFIX, namespace, podName, currentUsername, currentUserid);
      return SyncResult.ALREADY_IN_SYNC;
    }

    log.info("{} Patching pod {}/{}: username=[{}→{}], userid=[{}→{}]",
        LOG_PREFIX, namespace, podName,
        currentUsername, ownerLabels[0], currentUserid, ownerLabels[1]);
    return patchPodLabels(podName, namespace, ownerLabels[0], ownerLabels[1]);
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
   * Fetches the username/userid labels from the owner object identified by kind/name.
   * Uses ApiResourceDiscovery.getResourcesByKind to resolve kind to API paths,
   * matching the Python _get_object behavior.
   *
   * @return String[]{username, userid} or null if not found/no labels
   */
  @Nullable
  private String[] getOwnerUserLabels(String kind, String name, String namespace) {
    List<ApiResourceDiscovery.ResourceInfo> resources =
        this.apiResourceDiscovery.getResourcesByKind(kind);
    if (resources.isEmpty()) {
      log.warn("{} Owner lookup: no API resources found for kind={} (discovery snapshot miss)",
          LOG_PREFIX, kind);
      return null;
    }
    log.info("{} Owner lookup {}/{} ns={}: trying {} candidate resource(s)",
        LOG_PREFIX, kind, name, namespace, resources.size());

    for (ApiResourceDiscovery.ResourceInfo resourceInfo : resources) {
      String apiVersion = resourceInfo.apiVersion();
      String plural = resourceInfo.plural();
      String group = apiVersion.contains("/") ? apiVersion.split("/")[0] : "";
      String groupResource = group + "/" + plural;

      boolean namespaced;
      try {
        namespaced = this.apiResourceDiscovery.isNamespaced(groupResource);
      } catch (GroupResourceNotFoundException e) {
        log.info("{} Owner lookup: groupResource={} not in discovery — skipping candidate",
            LOG_PREFIX, groupResource);
        continue;
      }

      String path;
      if (namespaced) {
        if (group.isEmpty()) {
          path = "/api/v1/namespaces/" + namespace + "/" + plural + "/" + name;
        } else {
          path = "/apis/" + apiVersion + "/namespaces/" + namespace + "/" + plural + "/" + name;
        }
      } else {
        if (group.isEmpty()) {
          path = "/api/v1/" + plural + "/" + name;
        } else {
          path = "/apis/" + apiVersion + "/" + plural + "/" + name;
        }
      }

      log.info("{} Owner lookup attempt: GET {}", LOG_PREFIX, path);
      JsonNode obj = fetchJson(path);
      if (obj == null) {
        log.info("{} Owner lookup attempt returned null (404 or fetch error): {}",
            LOG_PREFIX, path);
        continue;
      }

      JsonNode labels = obj.path("metadata").path("labels");
      if (!labels.isObject()) {
        log.warn("{} Owner object {}/{} has no labels object — returning null",
            LOG_PREFIX, kind, name);
        return null;
      }

      String username = getTextValue(labels, LabelConstants.OBJECT_OWN_USERNAME_KEY);
      String userid = getTextValue(labels, LabelConstants.OBJECT_OWN_USERID_KEY);
      log.info("{} Owner labels resolved {}/{} ns={}: username={}, userid={}",
          LOG_PREFIX, kind, name, namespace, username, userid);
      return new String[]{username, userid};
    }

    log.warn("{} Owner lookup exhausted all candidates for {}/{} ns={} — returning null",
        LOG_PREFIX, kind, name, namespace);
    return null;
  }

  @Nullable
  private JsonNode listPodsWithWorkloadLabel() {
    String labelSelector = URLEncoder.encode(LabelConstants.WORKLOAD_KIND_KEY, StandardCharsets.UTF_8);
    String path = "/api/v1/pods?labelSelector=" + labelSelector;
    return fetchJson(path);
  }

  private SyncResult patchPodLabels(String name, String namespace,
      @Nullable String username, @Nullable String userid) {
    String path = "/api/v1/namespaces/" + namespace + "/pods/" + name;
    try {
      Map<String, Object> labels = new HashMap<>();
      labels.put(LabelConstants.OBJECT_OWN_USERNAME_KEY, username);
      labels.put(LabelConstants.OBJECT_OWN_USERID_KEY, userid);
      String patchBody = this.mapper.writeValueAsString(
          Map.of("metadata", Map.of("labels", labels)));

      RequestBody body = RequestBody.create(
          patchBody,
          MediaType.parse("application/merge-patch+json"));

      Call call = this.apiClient.buildCall(
          this.apiClient.getBasePath(), path, "PATCH",
          List.of(), List.of(),
          body,
          Map.of("Content-Type", "application/merge-patch+json"),
          Map.of(), Map.of(),
          new String[]{"BearerToken"}, null);

      try (Response response = call.execute()) {
        if (response.isSuccessful()) {
          log.info("{} Patched pod {}/{} (status={})",
              LOG_PREFIX, namespace, name, response.code());
          return SyncResult.PATCHED;
        }
        if (response.code() == 404) {
          log.info("{} Pod {}/{} not found for patch (already deleted?)",
              LOG_PREFIX, namespace, name);
          return SyncResult.PATCH_FAILED;
        }
        String errorBody = response.body() != null ? response.body().string() : "";
        log.warn("{} Failed to patch pod {}/{}: status={}, body={}",
            LOG_PREFIX, namespace, name, response.code(), errorBody);
        return SyncResult.PATCH_FAILED;
      }
    } catch (Exception e) {
      log.warn("{} Failed to patch pod {}/{}", LOG_PREFIX, namespace, name, e);
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

  private enum SyncResult {
    ALREADY_IN_SYNC,
    PATCHED,
    PATCH_FAILED,
  }

  private static final class Counters {
    int totalPods;
    int processed;
    int skippedNoWorkloadLabel;
    int ownerLookupFailed;
    int alreadyInSync;
    int patched;
    int patchFailed;
  }

}
