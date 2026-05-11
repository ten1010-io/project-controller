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
    this.snapshot = buildSnapshot();
    updateConfigMap(this.snapshot);
  }

  public void refresh() {
    log.debug("Refreshing API resource discovery");
    Snapshot newSnapshot = buildSnapshot();
    this.snapshot = newSnapshot;
    updateConfigMap(newSnapshot);
  }

  /**
   * Port of Python api_resource_manager._update_configmap().
   * kind → groupResource 매핑을 ConfigMap에 기록. 외부 시스템이 이 ConfigMap을 참조.
   */
  private void updateConfigMap(Snapshot snapshot) {
    String configMapName = "api-resources";
    String configMapNamespace = "aipub";

    try {
      ObjectNode body = this.mapper.createObjectNode();
      body.put("apiVersion", "v1");
      body.put("kind", "ConfigMap");
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
      if (putStatus == 404) {
        int postStatus = executeConfigMapWrite(collectionPath, "POST", bodyBytes);
        if (postStatus >= 200 && postStatus < 300) {
          log.debug("Created api-resources ConfigMap with {} entries", snapshot.kindDict().size());
        }
      } else if (putStatus >= 200 && putStatus < 300) {
        log.debug("Updated api-resources ConfigMap with {} entries", snapshot.kindDict().size());
      }
    } catch (Exception e) {
      log.warn("Failed to update api-resources ConfigMap", e);
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
      if (!response.isSuccessful()) {
        String errorBody = response.body() != null ? response.body().string() : "";
        log.warn("api-resources ConfigMap {} failed: status={} body={}",
            method, response.code(), errorBody);
      }
      return response.code();
    }
  }

  private Snapshot buildSnapshot() {
    Map<String, String> plurals = new HashMap<>();
    Map<String, Boolean> namespacedInfo = new HashMap<>();
    Map<String, String> groupVersions = new HashMap<>();
    Map<String, List<String>> kindDict = new HashMap<>();
    Set<String> groupResources = new HashSet<>();

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
        }
      }
    } catch (Exception e) {
      log.warn("Failed to discover core API resources", e);
    }

    // Non-core API resources (/apis)
    try {
      JsonNode apiGroups = fetchJson("/apis");
      if (apiGroups != null) {
        for (JsonNode group : apiGroups.path("groups")) {
          String groupName = group.path("name").textValue();
          for (JsonNode version : group.path("versions")) {
            String groupVersion = version.path("groupVersion").textValue();
            try {
              JsonNode resources = fetchJson("/apis/" + groupVersion);
              if (resources != null) {
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
                }
              }
            } catch (Exception e) {
              log.warn("Failed to discover API resources for {}", groupVersion, e);
            }
          }
        }
      }
    } catch (Exception e) {
      log.warn("Failed to discover API groups", e);
    }

    log.info("Discovered {} API resource plurals, {} namespaced info entries",
        plurals.size(), namespacedInfo.size());

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
        if (!response.isSuccessful()) {
          String errorBody = response.body() != null ? response.body().string() : "";
          log.warn("Failed to fetch API resource: {} status={} body={}",
              path, response.code(), errorBody);
          return null;
        }
        if (response.body() == null) {
          return null;
        }
        return this.mapper.readTree(response.body().string());
      }
    } catch (Exception e) {
      log.warn("Failed to fetch API resource: {}", path, e);
      return null;
    }
  }

}
