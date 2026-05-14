package io.ten1010.aipub.projectcontroller.mutating.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kubernetes.client.openapi.ApiClient;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;

@Slf4j
public class ApiResourceDiscovery {

  private final ApiClient apiClient;
  private final ObjectMapper mapper;
  private volatile Snapshot snapshot;

  public ApiResourceDiscovery(ApiClient apiClient) {
    this.apiClient = apiClient;
    this.mapper = new ObjectMapperFactory().createObjectMapper();
    log.info("Initializing API resource discovery");
    this.snapshot = buildSnapshot();
    updateConfigMap(this.snapshot);
  }

  public void refresh() {
    log.info("Refreshing API resource discovery");
    long startNanos = System.nanoTime();
    Snapshot newSnapshot = buildSnapshot();
    this.snapshot = newSnapshot;
    updateConfigMap(newSnapshot);
    long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
    log.info("API resource discovery refresh complete: plurals={}, namespacedInfo={}, kinds={}, "
            + "groupResources={}, durationMs={}",
        newSnapshot.plurals().size(),
        newSnapshot.namespacedInfo().size(),
        newSnapshot.kindDict().size(),
        newSnapshot.groupResources().size(),
        elapsedMs);
  }

  /**
   * Port of Python api_resource_manager._update_configmap().
   * kind → groupResource 매핑을 ConfigMap에 기록. 외부 시스템이 이 ConfigMap을 참조.
   */
  private void updateConfigMap(Snapshot snapshot) {
    String configMapName = "api-resources";
    String configMapNamespace = "aipub";
    int entryCount = snapshot.kindDict().size();

    try {
      ObjectNode body = this.mapper.createObjectNode();
      ObjectNode metadata = body.putObject("metadata");
      metadata.put("name", configMapName);
      metadata.put("namespace", configMapNamespace);
      ObjectNode data = body.putObject("data");
      for (Map.Entry<String, List<String>> entry : snapshot.kindDict().entrySet()) {
        data.put(entry.getKey(), String.join(",", entry.getValue()));
      }

      byte[] bodyBytes = this.mapper.writeValueAsBytes(body);
      String collectionPath = "/api/v1/namespaces/" + configMapNamespace + "/configmaps";
      String itemPath = collectionPath + "/" + configMapName;

      int putStatus = executeConfigMapWrite(itemPath, "PUT", bodyBytes);
      if (putStatus >= 200 && putStatus < 300) {
        log.info("api-resources ConfigMap updated: namespace={}, name={}, entries={}, payloadBytes={}",
            configMapNamespace, configMapName, entryCount, bodyBytes.length);
        return;
      }
      if (putStatus == 404) {
        int postStatus = executeConfigMapWrite(collectionPath, "POST", bodyBytes);
        if (postStatus >= 200 && postStatus < 300) {
          log.info("api-resources ConfigMap created: namespace={}, name={}, entries={}, payloadBytes={}",
              configMapNamespace, configMapName, entryCount, bodyBytes.length);
        } else {
          log.error("api-resources ConfigMap create failed after PUT 404: namespace={}, name={}, "
                  + "postStatus={}", configMapNamespace, configMapName, postStatus);
        }
        return;
      }
      log.error("api-resources ConfigMap update failed: namespace={}, name={}, putStatus={}, entries={}",
          configMapNamespace, configMapName, putStatus, entryCount);
    } catch (Exception e) {
      log.error("api-resources ConfigMap update threw exception: namespace={}, name={}, entries={}",
          configMapNamespace, configMapName, entryCount, e);
    }
  }

  private int executeConfigMapWrite(String path, String method, byte[] bodyBytes) throws Exception {
    Call call = this.apiClient.buildCall(
        this.apiClient.getBasePath(), path, method,
        List.of(), List.of(),
        bodyBytes,
        Map.of("Content-Type", "application/json"),
        Map.of(), Map.of(),
        new String[]{"BearerToken"}, null);
    try (Response response = call.execute()) {
      int code = response.code();
      if (response.isSuccessful()) {
        return code;
      }
      String errorBody = response.body() != null ? response.body().string() : "";
      if (code == 404 && "PUT".equals(method)) {
        log.info("api-resources ConfigMap not found on PUT, will fall back to POST: path={}, body={}",
            path, errorBody);
      } else {
        log.error("api-resources ConfigMap {} request failed: path={}, status={}, body={}",
            method, path, code, errorBody);
      }
      return code;
    }
  }

  private Snapshot buildSnapshot() {
    Map<String, String> plurals = new HashMap<>();
    Map<String, Boolean> namespacedInfo = new HashMap<>();
    Map<String, String> groupVersions = new HashMap<>();
    Map<String, List<String>> kindDict = new HashMap<>();
    Set<String> groupResources = new HashSet<>();

    int coreCount = 0;
    int nonCoreGroupVersionCount = 0;
    int nonCoreResourceCount = 0;
    List<String> failedGroupVersions = new ArrayList<>();

    // Core API resources (/api/v1)
    try {
      JsonNode coreResources = fetchJson("/api/v1");
      if (coreResources != null) {
        for (JsonNode resource : coreResources.path("resources")) {
          String name = resource.path("name").textValue();
          if (name.contains("/")) {
            continue;
          }
          String kind = resource.path("kind").textValue();
          boolean namespaced = resource.path("namespaced").booleanValue();

          String groupResource = "/" + name;
          plurals.put("v1/" + kind, name);
          namespacedInfo.put(groupResource, namespaced);
          groupResources.add(groupResource);
          kindDict.computeIfAbsent(kind, k -> new ArrayList<>()).add(groupResource);
          coreCount++;
        }
      } else {
        log.error("Core API discovery returned null: path=/api/v1");
      }
    } catch (Exception e) {
      log.error("Core API discovery threw exception: path=/api/v1", e);
    }

    // Non-core API resources (/apis)
    try {
      JsonNode apiGroups = fetchJson("/apis");
      if (apiGroups != null) {
        for (JsonNode group : apiGroups.path("groups")) {
          String groupName = group.path("name").textValue();
          for (JsonNode version : group.path("versions")) {
            String groupVersion = version.path("groupVersion").textValue();
            nonCoreGroupVersionCount++;
            try {
              JsonNode resources = fetchJson("/apis/" + groupVersion);
              if (resources == null) {
                failedGroupVersions.add(groupVersion);
                continue;
              }
              int beforeCount = nonCoreResourceCount;
              for (JsonNode resource : resources.path("resources")) {
                String name = resource.path("name").textValue();
                if (name.contains("/")) {
                  continue;
                }
                String kind = resource.path("kind").textValue();
                boolean namespaced = resource.path("namespaced").booleanValue();

                String groupResource = groupName + "/" + name;
                plurals.put(groupVersion + "/" + kind, name);
                namespacedInfo.put(groupResource, namespaced);
                groupVersions.put(groupResource, groupVersion);
                groupResources.add(groupResource);
                kindDict.computeIfAbsent(kind, k -> new ArrayList<>()).add(groupResource);
                nonCoreResourceCount++;
              }
              if (log.isDebugEnabled()) {
                log.debug("Discovered API group/version: groupVersion={}, resources={}",
                    groupVersion, nonCoreResourceCount - beforeCount);
              }
            } catch (Exception e) {
              failedGroupVersions.add(groupVersion);
              log.error("API group/version discovery threw exception: groupVersion={}",
                  groupVersion, e);
            }
          }
        }
      } else {
        log.error("API groups discovery returned null: path=/apis");
      }
    } catch (Exception e) {
      log.error("API groups discovery threw exception: path=/apis", e);
    }

    if (!failedGroupVersions.isEmpty()) {
      log.error("API discovery completed with {} failed group/versions: {}",
          failedGroupVersions.size(), failedGroupVersions);
    }
    log.info("API discovery summary: coreResources={}, nonCoreGroupVersions={}, "
            + "nonCoreResources={}, plurals={}, namespacedInfo={}, kinds={}, groupResources={}, "
            + "failedGroupVersions={}",
        coreCount, nonCoreGroupVersionCount, nonCoreResourceCount,
        plurals.size(), namespacedInfo.size(), kindDict.size(), groupResources.size(),
        failedGroupVersions.size());

    return new Snapshot(plurals, namespacedInfo, groupVersions, kindDict, groupResources);
  }

  @Nullable
  public String getPlural(String apiVersion, String kind) {
    return this.snapshot.plurals().get(apiVersion + "/" + kind);
  }

  public boolean isNamespaced(String groupResource) {
    Boolean result = this.snapshot.namespacedInfo().get(groupResource);
    if (result == null) {
      throw new GroupResourceNotFoundException(groupResource);
    }
    return result;
  }

  public boolean isExist(String groupResource) {
    return this.snapshot.groupResources().contains(groupResource);
  }

  @Nullable
  public String getGroupVersion(String groupResource) {
    return this.snapshot.groupVersions().get(groupResource);
  }

  public List<ResourceInfo> getResourcesByKind(String kind) {
    Snapshot s = this.snapshot;
    List<ResourceInfo> resources = new ArrayList<>();
    for (String groupResource : s.kindDict().getOrDefault(kind, List.of())) {
      String groupVersion = s.groupVersions().get(groupResource);
      if (groupVersion == null) {
        continue;
      }
      String plural = s.plurals().get(groupVersion + "/" + kind);
      if (plural == null) {
        continue;
      }
      resources.add(new ResourceInfo(groupVersion, plural));
    }
    return resources;
  }

  /**
   * Port of Python api_resource_manager.get_all_object_names().
   * K8s API를 호출하여 해당 group/resource의 모든 object name을 반환.
   *
   * @throws RuntimeException groupVersion 조회 실패, namespaced 검증 실패, API 호출 실패 시
   */
  public List<String> getAllObjectNames(String groupResource, @Nullable String namespace) {
    String[] parts = groupResource.split("/");
    String group = parts[0];
    String resource = parts[1];

    if (namespace != null && !isNamespaced(groupResource)) {
      throw new RuntimeException(
          "Cannot get namespaced objects for non-namespaced resource: " + groupResource);
    }

    String path;
    boolean isCoreApi = group.isEmpty() || "core".equals(group);
    if (namespace == null) {
      if (isCoreApi) {
        path = "/api/v1/" + resource;
      } else {
        String version = this.snapshot.groupVersions().get(groupResource);
        if (version == null) {
          throw new RuntimeException("Unknown groupVersion for: " + groupResource);
        }
        path = "/apis/" + version + "/" + resource;
      }
    } else {
      if (isCoreApi) {
        path = "/api/v1/namespaces/" + namespace + "/" + resource;
      } else {
        String version = this.snapshot.groupVersions().get(groupResource);
        if (version == null) {
          throw new RuntimeException("Unknown groupVersion for: " + groupResource);
        }
        path = "/apis/" + version + "/namespaces/" + namespace + "/" + resource;
      }
    }

    JsonNode result = fetchJson(path);
    if (result == null) {
      throw new RuntimeException("Failed to list objects: " + path);
    }
    List<String> names = new ArrayList<>();
    for (JsonNode item : result.path("items")) {
      String name = item.path("metadata").path("name").textValue();
      if (name != null) {
        names.add(name);
      }
    }
    return names;
  }

  public record ResourceInfo(String apiVersion, String plural) {
  }

  private record Snapshot(
      Map<String, String> plurals,
      Map<String, Boolean> namespacedInfo,
      Map<String, String> groupVersions,
      Map<String, List<String>> kindDict,
      Set<String> groupResources) {
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
        int code = response.code();
        if (!response.isSuccessful()) {
          String errorBody = response.body() != null ? response.body().string() : "";
          log.error("API fetch failed: path={}, status={}, body={}", path, code, errorBody);
          return null;
        }
        if (response.body() == null) {
          log.error("API fetch returned empty body: path={}, status={}", path, code);
          return null;
        }
        return this.mapper.readTree(response.body().string());
      }
    } catch (Exception e) {
      log.error("API fetch threw exception: path={}", path, e);
      return null;
    }
  }

}
