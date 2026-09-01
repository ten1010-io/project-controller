package io.ten1010.aipub.projectcontroller.mutating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.informer.cache.Cache;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import okhttp3.Call;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * "자식(PVC/PV)의 소유자 라벨 = 부모 ClusterVolume 의 소유자 라벨" 주기 수렴을 검증한다.
 * 소유권 이전(transfer)은 CV 라벨이 바뀐 뒤의 주기와 같으므로 교체 케이스가 곧 transfer 케이스다.
 */
class ClusterVolumeChildLabelSynchronizerTest {

  private static final String CV = "cv-1";
  private static final String USERNAME_KEY = LabelConstants.OBJECT_OWN_USERNAME_KEY;
  private static final String USERID_KEY = LabelConstants.OBJECT_OWN_USERID_KEY;
  private static final String OWNER_KEY = LabelConstants.CLUSTER_VOLUME_OWNER_KEY;

  private static final String CV_LIST_PATH =
      "/apis/aipub.ten1010.io/v1alpha1/clustervolumes?limit=500";
  private static final String PVC_LIST_PATH = "/api/v1/persistentvolumeclaims?labelSelector="
      + URLEncoder.encode(OWNER_KEY, StandardCharsets.UTF_8) + "&limit=500";
  private static final String PV_LIST_PATH = "/api/v1/persistentvolumes?labelSelector="
      + URLEncoder.encode(OWNER_KEY, StandardCharsets.UTF_8) + "&limit=500";

  private record PatchCall(String path, JsonNode labels) {
  }

  private ApiClient mockApiClient;
  private ObjectMapper mapper;
  private ClusterVolumeChildLabelSynchronizer synchronizer;
  private List<PatchCall> patches;

  @BeforeEach
  void setUp() {
    this.mockApiClient = mock(ApiClient.class);
    when(this.mockApiClient.getBasePath()).thenReturn("https://localhost:6443");
    this.mapper = new ObjectMapperFactory().createObjectMapper();
    this.patches = new ArrayList<>();

    Cache<V1Namespace> namespaceCache = new Cache<>();
    namespaceCache.add(new V1Namespace().metadata(new V1ObjectMeta()
        .name("allowlisted-ns")
        .labels(Map.of(LabelConstants.ALLOWLISTED_KEY, "true"))));
    this.synchronizer = new ClusterVolumeChildLabelSynchronizer(this.mockApiClient,
        new NamespaceAllowlistResolver(namespaceCache));
  }

  private Response buildResponse(int code, String body) {
    return new Response.Builder()
        .request(new Request.Builder().url("https://localhost:6443/test").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("OK")
        .body(ResponseBody.create(body, okhttp3.MediaType.parse("application/json")))
        .build();
  }

  private Call mockCallWithResponse(Response response) throws Exception {
    Call call = mock(Call.class);
    when(call.execute()).thenReturn(response);
    return call;
  }

  /** GET 을 경로별로 스텁(모르는 경로 404, serverErrorPaths 500), PATCH 는 기록 후 성공/실패 응답. */
  private void stubApi(Map<String, String> getPathToBody, Set<String> serverErrorPaths,
      int patchStatus) throws Exception {
    when(this.mockApiClient.buildCall(
        anyString(), anyString(), eq("GET"),
        anyList(), anyList(), isNull(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull()))
        .thenAnswer(invocation -> {
          String path = invocation.getArgument(1);
          if (serverErrorPaths.contains(path)) {
            return mockCallWithResponse(buildResponse(500, "{}"));
          }
          String body = getPathToBody.get(path);
          if (body == null) {
            return mockCallWithResponse(buildResponse(404, ""));
          }
          return mockCallWithResponse(buildResponse(200, body));
        });
    when(this.mockApiClient.buildCall(
        anyString(), anyString(), eq("PATCH"),
        anyList(), anyList(), any(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull()))
        .thenAnswer(invocation -> {
          String path = invocation.getArgument(1);
          byte[] body = invocation.getArgument(5);
          this.patches.add(new PatchCall(path,
              this.mapper.readTree(body).path("metadata").path("labels")));
          return mockCallWithResponse(buildResponse(patchStatus, "{}"));
        });
  }

  private void stubApi(Map<String, String> getPathToBody) throws Exception {
    stubApi(getPathToBody, Set.of(), 200);
  }

  private String listJson(List<Map<String, Object>> items) throws Exception {
    return this.mapper.writeValueAsString(Map.of(
        "apiVersion", "v1",
        "kind", "List",
        "items", items));
  }

  private Map<String, Object> k8sObject(String name, @Nullable String namespace,
      Map<String, String> labels, boolean terminating) {
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("name", name);
    if (namespace != null) {
      metadata.put("namespace", namespace);
    }
    metadata.put("labels", labels);
    if (terminating) {
      metadata.put("deletionTimestamp", "2026-08-31T00:00:00Z");
    }
    return Map.of("metadata", metadata);
  }

  private Map<String, String> ownerLabels(String username, String userid) {
    return Map.of(USERNAME_KEY, username, USERID_KEY, userid);
  }

  private Map<String, String> childLabels(String owner, Map<String, String> extra) {
    Map<String, String> labels = new HashMap<>(extra);
    labels.put(OWNER_KEY, owner);
    return labels;
  }

  /** CV 1개(cv-1) + PVC/PV 목록으로 표준 스텁을 구성한다. */
  private void stubCluster(Map<String, String> cvLabels, List<Map<String, Object>> claims,
      List<Map<String, Object>> volumes) throws Exception {
    stubApi(Map.of(
        CV_LIST_PATH, listJson(List.of(k8sObject(CV, null, cvLabels, false))),
        PVC_LIST_PATH, listJson(claims),
        PV_LIST_PATH, listJson(volumes)));
  }

  @Test
  @DisplayName("라벨 없는 복제 PVC·PV 에 CV 의 username/userid 를 붙인다")
  void childrenMissingLabels_patchesBoth() throws Exception {
    stubCluster(ownerLabels("alice", "u-1"),
        List.of(k8sObject(CV, "proj-a", childLabels(CV, Map.of()), false)),
        List.of(k8sObject("pv-1", null, childLabels(CV, Map.of()), false)));

    this.synchronizer.sync();

    assertThat(this.patches).hasSize(2);
    assertThat(this.patches).extracting(PatchCall::path).containsExactlyInAnyOrder(
        "/api/v1/namespaces/proj-a/persistentvolumeclaims/" + CV,
        "/api/v1/persistentvolumes/pv-1");
    for (PatchCall patch : this.patches) {
      assertThat(patch.labels().path(USERNAME_KEY).textValue()).isEqualTo("alice");
      assertThat(patch.labels().path(USERID_KEY).textValue()).isEqualTo("u-1");
    }
  }

  @Test
  @DisplayName("이미 일치하는 자식은 패치하지 않는다 (멱등 — 다음 주기에 아무 일도 안 함)")
  void childrenInSync_noPatch() throws Exception {
    stubCluster(ownerLabels("alice", "u-1"),
        List.of(k8sObject(CV, "proj-a", childLabels(CV, ownerLabels("alice", "u-1")), false)),
        List.of(k8sObject("pv-1", null, childLabels(CV, ownerLabels("alice", "u-1")), false)));

    this.synchronizer.sync();

    assertThat(this.patches).isEmpty();
  }

  @Test
  @DisplayName("transfer: CV 소유자가 바뀌면 자식 라벨을 새 소유자로 교체한다")
  void ownerTransferred_replacesChildLabels() throws Exception {
    stubCluster(ownerLabels("bob", "u-2"),
        List.of(k8sObject(CV, "proj-a", childLabels(CV, ownerLabels("alice", "u-1")), false)),
        List.of());

    this.synchronizer.sync();

    assertThat(this.patches).hasSize(1);
    assertThat(this.patches.get(0).labels().path(USERNAME_KEY).textValue()).isEqualTo("bob");
    assertThat(this.patches.get(0).labels().path(USERID_KEY).textValue()).isEqualTo("u-2");
  }

  @Test
  @DisplayName("CV 에 소유자 라벨이 없으면 자식의 라벨을 제거한다 (merge patch null)")
  void clusterVolumeWithoutOwnerLabels_removesChildLabels() throws Exception {
    stubCluster(Map.of(),
        List.of(k8sObject(CV, "proj-a", childLabels(CV, ownerLabels("alice", "u-1")), false)),
        List.of());

    this.synchronizer.sync();

    assertThat(this.patches).hasSize(1);
    assertThat(this.patches.get(0).labels().path(USERNAME_KEY).isNull()).isTrue();
    assertThat(this.patches.get(0).labels().path(USERID_KEY).isNull()).isTrue();
  }

  @Test
  @DisplayName("owner 라벨이 가리키는 CV 가 없으면(삭제 중·stale) 자식을 건드리지 않는다")
  void clusterVolumeNotFound_noPatch() throws Exception {
    stubApi(Map.of(
        CV_LIST_PATH, listJson(List.of()),
        PVC_LIST_PATH, listJson(List.of(
            k8sObject(CV, "proj-a", childLabels("ghost-cv", Map.of()), false))),
        PV_LIST_PATH, listJson(List.of())));

    this.synchronizer.sync();

    assertThat(this.patches).isEmpty();
  }

  @Test
  @DisplayName("allowlist 네임스페이스의 PVC 는 건너뛴다; PV 는 처리한다")
  void claimInAllowlistedNamespace_skipped() throws Exception {
    stubCluster(ownerLabels("alice", "u-1"),
        List.of(k8sObject(CV, "allowlisted-ns", childLabels(CV, Map.of()), false)),
        List.of(k8sObject("pv-1", null, childLabels(CV, Map.of()), false)));

    this.synchronizer.sync();

    assertThat(this.patches).hasSize(1);
    assertThat(this.patches.get(0).path()).isEqualTo("/api/v1/persistentvolumes/pv-1");
  }

  @Test
  @DisplayName("삭제 중(deletionTimestamp)인 자식은 건너뛴다")
  void terminatingChild_skipped() throws Exception {
    stubCluster(ownerLabels("alice", "u-1"),
        List.of(k8sObject(CV, "proj-a", childLabels(CV, Map.of()), true)),
        List.of());

    this.synchronizer.sync();

    assertThat(this.patches).isEmpty();
  }

  @Test
  @DisplayName("CV LIST 가 실패하면 이번 주기는 아무것도 패치하지 않는다 — 부분 정보로 라벨을 지우지 않는다")
  void clusterVolumeListFailure_skipsCycle() throws Exception {
    stubApi(Map.of(
        PVC_LIST_PATH, listJson(List.of(
            k8sObject(CV, "proj-a", childLabels(CV, ownerLabels("alice", "u-1")), false)))),
        Set.of(CV_LIST_PATH), 200);

    this.synchronizer.sync();

    assertThat(this.patches).isEmpty();
  }

  @Test
  @DisplayName("자식 LIST 실패는 해당 타깃만 스킵한다 — PVC 실패여도 PV 는 처리")
  void childListFailure_skipsTargetOnly() throws Exception {
    stubApi(Map.of(
        CV_LIST_PATH, listJson(List.of(k8sObject(CV, null, ownerLabels("alice", "u-1"), false))),
        PV_LIST_PATH, listJson(List.of(
            k8sObject("pv-1", null, childLabels(CV, Map.of()), false)))),
        Set.of(PVC_LIST_PATH), 200);

    this.synchronizer.sync();

    assertThat(this.patches).hasSize(1);
    assertThat(this.patches.get(0).path()).isEqualTo("/api/v1/persistentvolumes/pv-1");
  }

  @Test
  @DisplayName("패치 실패는 예외 없이 넘어가고 다른 자식은 계속 처리한다 — 다음 주기에 재시도된다")
  void patchFailure_doesNotThrowAndContinues() throws Exception {
    stubApi(Map.of(
        CV_LIST_PATH, listJson(List.of(k8sObject(CV, null, ownerLabels("alice", "u-1"), false))),
        PVC_LIST_PATH, listJson(List.of(
            k8sObject(CV, "proj-a", childLabels(CV, Map.of()), false),
            k8sObject(CV, "proj-b", childLabels(CV, Map.of()), false))),
        PV_LIST_PATH, listJson(List.of())),
        Set.of(), 500);

    this.synchronizer.sync();

    // 두 자식 모두 패치를 시도했고(실패 응답), sync 는 예외 없이 끝났다
    assertThat(this.patches).hasSize(2);
  }

}
