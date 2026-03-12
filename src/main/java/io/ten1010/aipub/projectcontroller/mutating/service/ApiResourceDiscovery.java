package io.ten1010.aipub.projectcontroller.mutating.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;

@Slf4j
public class ApiResourceDiscovery {

  private final ApiClient apiClient;
  private final ObjectMapper mapper;
  private final Map<String, String> plurals = new HashMap<>();
  private final Map<String, Boolean> namespacedInfo = new HashMap<>();

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

          this.plurals.put("v1/" + kind, name);
          this.namespacedInfo.put("/" + name, namespaced);
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

                  this.plurals.put(groupVersion + "/" + kind, name);
                  this.namespacedInfo.put(groupName + "/" + name, namespaced);
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
    return result != null && result;
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
