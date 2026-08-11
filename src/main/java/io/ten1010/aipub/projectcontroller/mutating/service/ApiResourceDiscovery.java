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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Response;
import org.jspecify.annotations.Nullable;

@Slf4j
public class ApiResourceDiscovery {

  /** miss로 확인된 group/resource 조합을 짧게 기억하여 반복 조회를 막는 negative cache TTL. */
  private static final long NEGATIVE_CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(15);
  /**
   * 미스 1건당 CRD 단건 조회 대기 상한. 초과 시 기존 스냅샷 기준으로 판정한다.
   * 한 웹훅 요청 안에서 서로 다른 조합 미스가 순차 발생해도 누적 대기가 웹훅
   * timeoutSeconds(10초, failurePolicy: Fail) 예산을 넘지 않도록 짧게 유지한다.
   * 단일 오브젝트 GET은 정상적으로 수십 ms 내에 끝난다.
   */
  private static final long PER_MISS_LOOKUP_WAIT_TIMEOUT_MS = 2_000;

  private final ApiClient apiClient;
  private final ObjectMapper mapper;
  private volatile Snapshot snapshot;
  /** 스냅샷 참조 교체(전체 refresh 커밋, targeted 병합)를 직렬화하는 락. 읽기는 락 없이 volatile로. */
  private final Object snapshotWriteLock = new Object();
  /** group/resource 조합별 진행 중인 CRD 단건 조회. 동일 조합 동시 미스는 하나의 조회를 공유한다. */
  private final ConcurrentHashMap<String, CompletableFuture<Boolean>> inFlightResourceLookups =
      new ConcurrentHashMap<>();
  /** CRD가 없거나 서빙되지 않는 group/resource 조합의 negative cache. 값은 만료 시각(nanoTime 기준). */
  private final ConcurrentHashMap<String, Long> negativeGroupResourceCache =
      new ConcurrentHashMap<>();
  private final ExecutorService resourceLookupExecutor = createResourceLookupExecutor();

  public ApiResourceDiscovery(ApiClient apiClient) {
    this.apiClient = apiClient;
    this.mapper = new ObjectMapperFactory().createObjectMapper();
    log.info("Initializing API resource discovery");
    this.snapshot = buildSnapshot();
    updateConfigMap(this.snapshot);
  }

  private static ExecutorService createResourceLookupExecutor() {
    return Executors.newCachedThreadPool(runnable -> {
      Thread thread = new Thread(runnable, "api-resource-discovery-crd-lookup");
      thread.setDaemon(true);
      return thread;
    });
  }

  public void refresh() {
    log.info("Refreshing API resource discovery");
    long startNanos = System.nanoTime();
    Snapshot newSnapshot = buildSnapshot();
    synchronized (this.snapshotWriteLock) {
      this.snapshot = newSnapshot;
    }
    // 전체 refresh가 최신 진실이므로 negative cache를 비워 즉시 재판정 가능하게 한다.
    this.negativeGroupResourceCache.clear();
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
          groupVersions.put(groupResource, "v1");
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
      refreshOnMiss(groupResource);
      result = this.snapshot.namespacedInfo().get(groupResource);
    }
    if (result == null) {
      throw new GroupResourceNotFoundException(groupResource);
    }
    return result;
  }

  public boolean isExist(String groupResource) {
    if (this.snapshot.groupResources().contains(groupResource)) {
      return true;
    }
    refreshOnMiss(groupResource);
    return this.snapshot.groupResources().contains(groupResource);
  }

  /**
   * 스냅샷 미스 시 해당 group/resource의 CRD만 이름({@code <plural>.<group>})으로 단건
   * 조회하여 스냅샷에 병합한다. CRD 생성 직후 주기적 전체 refresh(5분) 전에 들어온 웹훅
   * 요청이 캐시 미스로 거부되는 문제를, 그룹 전체 디스커버리 없이 요청된 리소스만 조회해서 해결.
   *
   * <p>동일 조합에 대한 동시 미스는 in-flight 맵으로 하나의 조회를 공유하고, miss로 확인된
   * 조합은 짧은 TTL의 negative cache로 반복 조회를 막는다. 대기 타임아웃/실패 시에는 기존
   * 스냅샷 기준으로 판정한다(호출부 재조회가 miss 처리).
   *
   * <p>CRD가 아닌 aggregated API 리소스는 이 경로로 발견되지 않으며 주기적 전체 refresh에서
   * 수렴한다.
   */
  private void refreshOnMiss(String groupResource) {
    String[] groupAndResource = parseCustomGroupResource(groupResource);
    if (groupAndResource == null) {
      // 코어 리소스("" 또는 "core" alias)와 형식 오류는 CRD 조회 대상이 아님. 기존 동작 유지.
      return;
    }
    if (isWithinTtl(this.negativeGroupResourceCache, groupResource)) {
      return;
    }
    CompletableFuture<Boolean> lookupFuture = this.inFlightResourceLookups.computeIfAbsent(
        groupResource,
        gr -> CompletableFuture
            .supplyAsync(
                () -> lookupCustomResourceDefinition(gr, groupAndResource[0], groupAndResource[1]),
                this.resourceLookupExecutor)
            .whenComplete((found, throwable) -> this.inFlightResourceLookups.remove(gr)));
    try {
      lookupFuture.get(PER_MISS_LOOKUP_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      log.warn("CRD lookup wait timed out, judging by current snapshot: groupResource={}, "
          + "timeoutMs={}", groupResource, PER_MISS_LOOKUP_WAIT_TIMEOUT_MS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Interrupted while waiting for CRD lookup: groupResource={}", groupResource);
    } catch (ExecutionException e) {
      log.error("CRD lookup failed: groupResource={}", groupResource, e.getCause());
    }
  }

  /**
   * CRD 단건 조회 대상인 커스텀 group/resource를 {group, resource}로 분해한다.
   * 코어 리소스(빈 그룹 "/pods" 형태), "core" alias, 형식 오류는 null 반환.
   */
  @Nullable
  private String[] parseCustomGroupResource(String groupResource) {
    int separatorIndex = groupResource.indexOf('/');
    if (separatorIndex <= 0 || separatorIndex == groupResource.length() - 1) {
      return null;
    }
    String group = groupResource.substring(0, separatorIndex);
    if ("core".equals(group)) {
      return null;
    }
    return new String[] {group, groupResource.substring(separatorIndex + 1)};
  }

  private boolean isWithinTtl(ConcurrentHashMap<String, Long> cache, String key) {
    Long deadlineNanos = cache.get(key);
    if (deadlineNanos == null) {
      return false;
    }
    if (System.nanoTime() - deadlineNanos < 0) {
      return true;
    }
    cache.remove(key, deadlineNanos);
    return false;
  }

  /**
   * CRD를 이름({@code <plural>.<group>})으로 단건 조회하여 해당 리소스만 스냅샷에 병합한다.
   * resourceLookupExecutor 스레드에서 실행되며, 웹훅 대기자들은 이 결과를 공유한다.
   *
   * @return CRD가 Established 상태로 존재하여 스냅샷에 병합되었으면 true
   */
  private boolean lookupCustomResourceDefinition(String groupResource, String group,
      String resource) {
    log.info("Looking up CRD for missed group/resource: groupResource={}", groupResource);
    JsonNode crd = fetchJson(
        "/apis/apiextensions.k8s.io/v1/customresourcedefinitions/" + resource + "." + group,
        true);
    if (crd == null) {
      return markResourceMissing(groupResource, "CRD not found on API server");
    }
    if (!isCrdEstablished(crd)) {
      return markResourceMissing(groupResource, "CRD not established");
    }

    JsonNode names = crd.path("status").path("acceptedNames");
    if (names.path("plural").textValue() == null) {
      names = crd.path("spec").path("names");
    }
    String plural = names.path("plural").textValue();
    String kind = names.path("kind").textValue();
    if (plural == null || kind == null) {
      return markResourceMissing(groupResource, "CRD names are incomplete");
    }
    boolean namespaced = "Namespaced".equals(crd.path("spec").path("scope").textValue());

    Map<String, String> plurals = new HashMap<>();
    String lastServedGroupVersion = null;
    for (JsonNode version : crd.path("spec").path("versions")) {
      if (!version.path("served").booleanValue()) {
        continue;
      }
      String versionName = version.path("name").textValue();
      if (versionName == null) {
        continue;
      }
      String groupVersion = group + "/" + versionName;
      plurals.put(groupVersion + "/" + kind, plural);
      lastServedGroupVersion = groupVersion;
    }
    if (lastServedGroupVersion == null) {
      return markResourceMissing(groupResource, "CRD has no served versions");
    }

    String mergedGroupResource = group + "/" + plural;
    mergeSnapshot(
        plurals,
        Map.of(mergedGroupResource, namespaced),
        Map.of(mergedGroupResource, lastServedGroupVersion),
        Map.of(kind, List.of(mergedGroupResource)),
        Set.of(mergedGroupResource));
    log.info("CRD lookup merged into snapshot: groupResource={}, servedVersions={}",
        mergedGroupResource, plurals.size());
    return true;
  }

  private boolean isCrdEstablished(JsonNode crd) {
    for (JsonNode condition : crd.path("status").path("conditions")) {
      if ("Established".equals(condition.path("type").textValue())) {
        return "True".equals(condition.path("status").textValue());
      }
    }
    return false;
  }

  private boolean markResourceMissing(String groupResource, String reason) {
    this.negativeGroupResourceCache.put(groupResource,
        System.nanoTime() + NEGATIVE_CACHE_TTL_NANOS);
    log.info("Group/resource judged missing, negative-cached: groupResource={}, reason={}, "
        + "ttlMs={}", groupResource, reason,
        TimeUnit.NANOSECONDS.toMillis(NEGATIVE_CACHE_TTL_NANOS));
    return false;
  }

  /**
   * targeted refresh 결과를 스냅샷에 병합한다.
   * 기존 스냅샷의 맵을 변경하지 않고 복사 + 병합한 새 Snapshot으로 참조를 교체한다.
   * snapshotWriteLock으로 전체 refresh 커밋 및 다른 그룹의 targeted 병합과 직렬화하여
   * lost-update를 막는다.
   */
  private void mergeSnapshot(
      Map<String, String> newPlurals,
      Map<String, Boolean> newNamespacedInfo,
      Map<String, String> newGroupVersions,
      Map<String, List<String>> newKindEntries,
      Set<String> newGroupResources) {
    synchronized (this.snapshotWriteLock) {
      Snapshot current = this.snapshot;
      Map<String, String> plurals = new HashMap<>(current.plurals());
      plurals.putAll(newPlurals);
      Map<String, Boolean> namespacedInfo = new HashMap<>(current.namespacedInfo());
      namespacedInfo.putAll(newNamespacedInfo);
      Map<String, String> groupVersions = new HashMap<>(current.groupVersions());
      groupVersions.putAll(newGroupVersions);
      Set<String> groupResources = new HashSet<>(current.groupResources());
      groupResources.addAll(newGroupResources);
      Map<String, List<String>> kindDict = new HashMap<>(current.kindDict());
      for (Map.Entry<String, List<String>> entry : newKindEntries.entrySet()) {
        List<String> merged = new ArrayList<>(kindDict.getOrDefault(entry.getKey(), List.of()));
        for (String groupResource : entry.getValue()) {
          if (!merged.contains(groupResource)) {
            merged.add(groupResource);
          }
        }
        kindDict.put(entry.getKey(), merged);
      }
      this.snapshot = new Snapshot(plurals, namespacedInfo, groupVersions, kindDict,
          groupResources);
    }
  }

  /** Spring @Bean inferred destroy method — 컨텍스트 종료 시 CRD lookup executor 정리. */
  public void shutdown() {
    this.resourceLookupExecutor.shutdownNow();
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
    return fetchJson(path, false);
  }

  @Nullable
  private JsonNode fetchJson(String path, boolean notFoundExpected) {
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
          if (notFoundExpected && code == 404) {
            log.debug("API fetch returned 404: path={}", path);
          } else {
            log.error("API fetch failed: path={}, status={}, body={}", path, code, errorBody);
          }
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
