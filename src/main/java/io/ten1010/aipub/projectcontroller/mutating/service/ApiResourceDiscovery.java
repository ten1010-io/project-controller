package io.ten1010.aipub.projectcontroller.mutating.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  }

  public void refresh() {
    log.debug("Refreshing API resource discovery");
    this.snapshot = buildSnapshot();
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
          String name = resource.path("name").asText();
          if (name.contains("/")) {
            continue;
          }
          String kind = resource.path("kind").asText();
          boolean namespaced = resource.path("namespaced").asBoolean();

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
          String groupName = group.path("name").asText();
          for (JsonNode version : group.path("versions")) {
            String groupVersion = version.path("groupVersion").asText();
            try {
              JsonNode resources = fetchJson("/apis/" + groupVersion);
              if (resources != null) {
                for (JsonNode resource : resources.path("resources")) {
                  String name = resource.path("name").asText();
                  if (name.contains("/")) {
                    continue;
                  }
                  String kind = resource.path("kind").asText();
                  boolean namespaced = resource.path("namespaced").asBoolean();

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
          log.warn("Failed to fetch API resource: {} status={}", path, response.code());
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
