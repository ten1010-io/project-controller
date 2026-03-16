package io.ten1010.aipub.projectcontroller.mutating.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;

@Slf4j
public class ApiResourceDiscovery {

  // TODO check: Python(api_resource_manager.py)은 run() 메서드로 300초마다 주기적으로 리소스를 재탐색함.
  //  Java는 생성자에서 1회만 init(). 런타임에 새 CRD 추가 시 반영 안 됨.

  private final ApiClient apiClient;
  private final ObjectMapper mapper;
  private final Map<String, String> plurals = new HashMap<>();
  private final Map<String, Boolean> namespacedInfo = new HashMap<>();
  private final Map<String, String> groupVersions = new HashMap<>();
  private final Map<String, List<String>> kindDict = new HashMap<>();
  // TODO check: List.contains()는 O(n). Python도 동일하지만, 리소스 수가 많을 경우 Set 고려.
  private final List<String> groupResources = new ArrayList<>();

  public ApiResourceDiscovery(ApiClient apiClient) {
    this.apiClient = apiClient;
    this.mapper = new ObjectMapperFactory().createObjectMapper();
    init();
  }

  private void init() {
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

          // TODO check: Python도 동일하지만, core API 리소스(Pod, Service 등)가 groupVersions에 저장되지 않음.
          //  getResourcesByKind("Pod"), getGroupVersion("/pods") 호출 시 null 반환됨.
          String groupResource = "/" + name;
          this.plurals.put("v1/" + kind, name);
          this.namespacedInfo.put(groupResource, namespaced);
          this.groupResources.add(groupResource);
          this.kindDict.computeIfAbsent(kind, k -> new ArrayList<>()).add(groupResource);
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
                  this.plurals.put(groupVersion + "/" + kind, name);
                  this.namespacedInfo.put(groupResource, namespaced);
                  this.groupVersions.put(groupResource, groupVersion);
                  this.groupResources.add(groupResource);
                  this.kindDict.computeIfAbsent(kind, k -> new ArrayList<>()).add(groupResource);
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

    log.info("Discovered {} API resource plurals, {} namespaced info entries", this.plurals.size(), this.namespacedInfo.size());
    if (this.plurals.containsKey("apps/v1/Deployment")) {
      log.info("Deployment plural: {}", this.plurals.get("apps/v1/Deployment"));
    } else {
      log.info("WARNING: apps/v1/Deployment NOT found in plurals map");
    }
  }

  @Nullable
  public String getPlural(String apiVersion, String kind) {
    return this.plurals.get(apiVersion + "/" + kind);
  }

  public boolean isNamespaced(String groupResource) {
    Boolean result = this.namespacedInfo.get(groupResource);
    if (result == null) {
      throw new GroupResourceNotFoundException(groupResource);
    }
    return result;
  }

  public boolean isExist(String groupResource) {
    return this.groupResources.contains(groupResource);
  }

  @Nullable
  public String getGroupVersion(String groupResource) {
    return this.groupVersions.get(groupResource);
  }

  public List<ResourceInfo> getResourcesByKind(String kind) {
    List<ResourceInfo> resources = new ArrayList<>();
    for (String groupResource : this.kindDict.getOrDefault(kind, List.of())) {
      String groupVersion = this.groupVersions.get(groupResource);
      // TODO check: groupVersion이 null일 때(core API 리소스) 아래 문자열 연결에서 "null/Pod" 됨.
      //  Python도 동일한 패턴이지만, null check를 먼저 해야 안전함.
      String apiVersionKind = groupVersion + "/" + kind;
      String plural = this.plurals.get(apiVersionKind);
      if (groupVersion == null || plural == null) {
        continue;
      }
      resources.add(new ResourceInfo(groupVersion, plural));
    }
    return resources;
  }

  public record ResourceInfo(String apiVersion, String plural) {
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
