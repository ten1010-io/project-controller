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
    log.info("Application ready, starting UserLabelSynchronizer with interval {}ms", SYNC_INTERVAL_MS);
    this.scheduler.scheduleWithFixedDelay(this::sync, 0, SYNC_INTERVAL_MS, TimeUnit.MILLISECONDS);
  }

  private void sync() {
    try {
      run();
    } catch (Exception e) {
      log.warn("Failed to sync user labels", e);
    }
  }

  private void run() {
    JsonNode podList = listPodsWithWorkloadLabel();
    if (podList == null) {
      return;
    }
    JsonNode items = podList.path("items");
    if (!items.isArray()) {
      return;
    }

    Map<String, String @Nullable []> cache = new HashMap<>();

    for (JsonNode pod : items) {
      JsonNode labels = pod.path("metadata").path("labels");
      if (!labels.isObject()) {
        continue;
      }

      JsonNode kindNode = labels.get(LabelConstants.WORKLOAD_KIND_KEY);
      JsonNode nameNode = labels.get(LabelConstants.WORKLOAD_NAME_KEY);
      if (kindNode == null || nameNode == null) {
        continue;
      }
      String kind = kindNode.textValue();
      String name = nameNode.textValue();
      if (kind == null || name == null) {
        continue;
      }

      String namespace = pod.path("metadata").path("namespace").textValue();
      String podName = pod.path("metadata").path("name").textValue();
      if (namespace == null || podName == null) {
        continue;
      }

      String cacheKey = kind + "/" + name;
      if (cache.containsKey(cacheKey)) {
        String @Nullable [] cached = cache.get(cacheKey);
        if (cached == null) {
          continue;
        }
        syncPodIfNeeded(pod, labels, podName, namespace, cached);
      } else {
        String @Nullable [] ownerLabels = getOwnerUserLabels(kind, name, namespace);
        if (ownerLabels == null) {
          // Python: object not found or no labels → not cached, continue
          // To avoid repeated failed lookups, cache as null
          cache.put(cacheKey, null);
          continue;
        }
        cache.put(cacheKey, ownerLabels);
        syncPodIfNeeded(pod, labels, podName, namespace, ownerLabels);
      }
    }
  }

  private void syncPodIfNeeded(JsonNode pod, JsonNode labels, String podName, String namespace,
      String[] ownerLabels) {
    String currentUsername = getTextValue(labels, LabelConstants.OBJECT_OWN_USERNAME_KEY);
    String currentUserid = getTextValue(labels, LabelConstants.OBJECT_OWN_USERID_KEY);

    if (Objects.equals(ownerLabels[0], currentUsername)
        && Objects.equals(ownerLabels[1], currentUserid)) {
      return;
    }

    log.debug("Syncing user labels for pod {}/{}: username=[{}→{}], userid=[{}→{}]",
        namespace, podName, currentUsername, ownerLabels[0], currentUserid, ownerLabels[1]);
    patchPodLabels(podName, namespace, ownerLabels[0], ownerLabels[1]);
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
      log.debug("No API resources found for kind: {}", kind);
      return null;
    }

    for (ApiResourceDiscovery.ResourceInfo resourceInfo : resources) {
      String apiVersion = resourceInfo.apiVersion();
      String plural = resourceInfo.plural();
      String group = apiVersion.contains("/") ? apiVersion.split("/")[0] : "";
      String groupResource = group + "/" + plural;

      boolean namespaced;
      try {
        namespaced = this.apiResourceDiscovery.isNamespaced(groupResource);
      } catch (GroupResourceNotFoundException e) {
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

      JsonNode obj = fetchJson(path);
      if (obj == null) {
        continue;
      }

      JsonNode labels = obj.path("metadata").path("labels");
      if (!labels.isObject()) {
        return null;
      }

      String username = getTextValue(labels, LabelConstants.OBJECT_OWN_USERNAME_KEY);
      String userid = getTextValue(labels, LabelConstants.OBJECT_OWN_USERID_KEY);
      return new String[]{username, userid};
    }

    log.debug("Failed to find object {}/{}", kind, name);
    return null;
  }

  @Nullable
  private JsonNode listPodsWithWorkloadLabel() {
    String labelSelector = URLEncoder.encode(LabelConstants.WORKLOAD_KIND_KEY, StandardCharsets.UTF_8);
    String path = "/api/v1/pods?labelSelector=" + labelSelector;
    return fetchJson(path);
  }

  private void patchPodLabels(String name, String namespace,
      @Nullable String username, @Nullable String userid) {
    String path = "/api/v1/namespaces/" + namespace + "/pods/" + name;
    try {
      String patchBody = this.mapper.writeValueAsString(Map.of(
          "metadata", Map.of(
              "labels", Map.of(
                  LabelConstants.OBJECT_OWN_USERNAME_KEY, username != null ? username : "",
                  LabelConstants.OBJECT_OWN_USERID_KEY, userid != null ? userid : ""
              )
          )
      ));

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
        if (!response.isSuccessful()) {
          if (response.code() == 404) {
            log.debug("Pod not found for patch: {}/{}", namespace, name);
            return;
          }
          log.warn("Failed to patch pod {}/{}: status={}", namespace, name, response.code());
        }
      }
    } catch (Exception e) {
      log.warn("Failed to patch pod {}/{}", namespace, name, e);
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
          log.warn("Failed to fetch: {} status={}", path, response.code());
          return null;
        }
        if (response.body() == null) {
          return null;
        }
        return this.mapper.readTree(response.body().string());
      }
    } catch (Exception e) {
      log.warn("Failed to fetch: {}", path, e);
      return null;
    }
  }

}
