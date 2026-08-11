package io.ten1010.aipub.projectcontroller.mutating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.ApiClient;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserLabelSynchronizerTest {

  private static final String ENCODED_SELECTOR =
      URLEncoder.encode(LabelConstants.WORKLOAD_KIND_KEY, StandardCharsets.UTF_8);
  // Owner LISTs match on label ABSENCE: referenced owners are always root objects
  // without the workload-kind label (stamping webhook invariant).
  private static final String ENCODED_OWNER_SELECTOR =
      URLEncoder.encode("!" + LabelConstants.WORKLOAD_KIND_KEY, StandardCharsets.UTF_8);
  // Must stay 1:1 with UserLabelSynchronizer.SYNC_TARGETS (intermediates first, pods last)
  private static final List<String> LIST_PATH_PREFIXES = List.of(
      "/api/v1/replicationcontrollers",
      "/apis/apps/v1/statefulsets",
      "/apis/apps/v1/deployments",
      "/apis/apps/v1/replicasets",
      "/apis/apps/v1/daemonsets",
      "/apis/batch/v1/jobs",
      "/apis/batch/v1/cronjobs",
      "/api/v1/pods");
  private static final String WORKSPACES_PREFIX = "/apis/aipub.ten1010.io/v1alpha1/workspaces";
  private static final String OPERATIONS_PREFIX = "/apis/aipub.ten1010.io/v1alpha1/operations";

  private UserLabelSynchronizer synchronizer;
  private ApiClient mockApiClient;
  private ApiResourceDiscovery mockDiscovery;
  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    this.mockApiClient = mock(ApiClient.class);
    this.mockDiscovery = mock(ApiResourceDiscovery.class);
    when(this.mockApiClient.getBasePath()).thenReturn("https://localhost:6443");
    this.synchronizer = new UserLabelSynchronizer(this.mockDiscovery, this.mockApiClient);
    this.mapper = new ObjectMapperFactory().createObjectMapper();
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

  private String listJson(List<Map<String, Object>> items) throws Exception {
    return this.mapper.writeValueAsString(Map.of(
        "apiVersion", "v1",
        "kind", "List",
        "items", items));
  }

  private String listJsonWithContinue(List<Map<String, Object>> items, String continueToken)
      throws Exception {
    return this.mapper.writeValueAsString(Map.of(
        "apiVersion", "v1",
        "kind", "List",
        "metadata", Map.of("continue", continueToken),
        "items", items));
  }

  private Map<String, Object> objectMap(String name, String namespace,
      Map<String, String> labels) {
    return Map.of(
        "metadata", Map.of(
            "name", name,
            "namespace", namespace,
            "labels", labels));
  }

  /**
   * First-page LIST path for a sync target — must match the exact query string
   * the synchronizer builds (labelSelector on workload-kind presence, then limit).
   */
  private String listPath(String prefix) {
    return prefix + "?labelSelector=" + ENCODED_SELECTOR + "&limit=500";
  }

  /** Continuation-page LIST path for a sync target (URL-encoded continue token). */
  private String listPath(String prefix, String continueToken) {
    return listPath(prefix) + "&continue="
        + URLEncoder.encode(continueToken, StandardCharsets.UTF_8);
  }

  /**
   * First-page owner LIST path — labelSelector on workload-kind ABSENCE, then limit.
   * Centralized so tests always match the implementation's query string exactly.
   */
  private String ownerListPath(String prefix) {
    return prefix + "?labelSelector=" + ENCODED_OWNER_SELECTOR + "&limit=500";
  }

  /** Continuation-page owner LIST path (URL-encoded continue token). */
  private String ownerListPath(String prefix, String continueToken) {
    return ownerListPath(prefix) + "&continue="
        + URLEncoder.encode(continueToken, StandardCharsets.UTF_8);
  }

  /** Returns list stubs for all 8 sync targets, each with an empty item list. */
  private Map<String, String> emptyListStubs() throws Exception {
    Map<String, String> stubs = new HashMap<>();
    String empty = listJson(List.of());
    for (String prefix : LIST_PATH_PREFIXES) {
      stubs.put(listPath(prefix), empty);
    }
    return stubs;
  }

  /**
   * Stubs all GET buildCalls keyed by request path. Unknown paths get 404,
   * paths in serverErrorPaths get 500. A fresh Call/Response is created per
   * invocation so bodies can be consumed independently.
   */
  private void stubGetByPath(Map<String, String> pathToBody, Set<String> serverErrorPaths)
      throws Exception {
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
          String body = pathToBody.get(path);
          if (body == null) {
            return mockCallWithResponse(buildResponse(404, ""));
          }
          return mockCallWithResponse(buildResponse(200, body));
        });
  }

  private void stubGetByPath(Map<String, String> pathToBody) throws Exception {
    stubGetByPath(pathToBody, Set.of());
  }

  private void stubPatchAlwaysOk() throws Exception {
    when(this.mockApiClient.buildCall(
        anyString(), anyString(), eq("PATCH"),
        anyList(), anyList(), any(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull()))
        .thenAnswer(invocation -> mockCallWithResponse(buildResponse(200, "{}")));
  }

  private void stubWorkspaceOwnerDiscovery() {
    when(this.mockDiscovery.getResourcesByKind("Workspace"))
        .thenReturn(List.of(new ApiResourceDiscovery.ResourceInfo(
            "aipub.ten1010.io/v1alpha1", "workspaces")));
    when(this.mockDiscovery.isNamespaced("aipub.ten1010.io/workspaces")).thenReturn(true);
  }

  private void stubOperationOwnerDiscovery() {
    when(this.mockDiscovery.getResourcesByKind("Operation"))
        .thenReturn(List.of(new ApiResourceDiscovery.ResourceInfo(
            "aipub.ten1010.io/v1alpha1", "operations")));
    when(this.mockDiscovery.isNamespaced("aipub.ten1010.io/operations")).thenReturn(true);
  }

  private void verifyNeverPatched() throws Exception {
    verify(this.mockApiClient, never()).buildCall(
        anyString(), anyString(), eq("PATCH"),
        anyList(), anyList(), any(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull());
  }

  private List<String> capturePatchPaths(int expectedCount) throws Exception {
    ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
    verify(this.mockApiClient, times(expectedCount)).buildCall(
        anyString(), pathCaptor.capture(), eq("PATCH"),
        anyList(), anyList(), any(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull());
    return pathCaptor.getAllValues();
  }

  private void verifyGetCount(String path, int expectedCount) throws Exception {
    verify(this.mockApiClient, times(expectedCount)).buildCall(
        anyString(), eq(path), eq("GET"),
        anyList(), anyList(), isNull(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull());
  }

  @Test
  void sync_noObjects_doesNothing() throws Exception {
    stubGetByPath(emptyListStubs());

    this.synchronizer.sync();

    verifyNeverPatched();
  }

  @Test
  void sync_podAlreadyInSync_doesNotPatch() throws Exception {
    Map<String, String> podLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "my-ws",
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "testuser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "user-123");
    Map<String, String> ownerLabels = Map.of(
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "testuser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "user-123");

    Map<String, String> stubs = emptyListStubs();
    stubs.put(listPath("/api/v1/pods"),
        listJson(List.of(objectMap("pod-1", "test-ns", podLabels))));
    stubs.put(ownerListPath(WORKSPACES_PREFIX),
        listJson(List.of(objectMap("my-ws", "test-ns", ownerLabels))));
    stubGetByPath(stubs);
    stubWorkspaceOwnerDiscovery();

    this.synchronizer.sync();

    verifyNeverPatched();
  }

  @Test
  void sync_podOutOfSync_patchesPod() throws Exception {
    Map<String, String> podLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "my-ws",
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "olduser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "old-123");
    Map<String, String> ownerLabels = Map.of(
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "newuser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "new-456");

    Map<String, String> stubs = emptyListStubs();
    stubs.put(listPath("/api/v1/pods"),
        listJson(List.of(objectMap("pod-1", "test-ns", podLabels))));
    stubs.put(ownerListPath(WORKSPACES_PREFIX),
        listJson(List.of(objectMap("my-ws", "test-ns", ownerLabels))));
    stubGetByPath(stubs);
    stubWorkspaceOwnerDiscovery();
    stubPatchAlwaysOk();

    this.synchronizer.sync();

    List<String> patchPaths = capturePatchPaths(1);
    assertThat(patchPaths).containsExactly("/api/v1/namespaces/test-ns/pods/pod-1");
  }

  @Test
  void sync_ownerNotFound_skipsPod() throws Exception {
    Map<String, String> podLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "missing-ws");

    Map<String, String> stubs = emptyListStubs();
    stubs.put(listPath("/api/v1/pods"),
        listJson(List.of(objectMap("pod-1", "test-ns", podLabels))));
    // Owner LIST loads fine but contains no matching owner → lookup miss → skip
    stubs.put(ownerListPath(WORKSPACES_PREFIX), listJson(List.of()));
    stubGetByPath(stubs);
    stubWorkspaceOwnerDiscovery();

    this.synchronizer.sync();

    verifyNeverPatched();
  }

  @Test
  void sync_multipleObjectsSameKind_ownerListedOnce() throws Exception {
    Map<String, String> staleLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "my-ws",
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "olduser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "old-123");
    Map<String, String> ownerLabels = Map.of(
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "newuser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "new-456");

    Map<String, String> stubs = emptyListStubs();
    stubs.put(listPath("/api/v1/pods"), listJson(List.of(
        objectMap("pod-1", "test-ns", staleLabels),
        objectMap("pod-2", "test-ns", staleLabels))));
    stubs.put(ownerListPath(WORKSPACES_PREFIX),
        listJson(List.of(objectMap("my-ws", "test-ns", ownerLabels))));
    stubGetByPath(stubs);
    stubWorkspaceOwnerDiscovery();
    stubPatchAlwaysOk();

    this.synchronizer.sync();

    // Owner kind listed only once despite two out-of-sync pods
    verifyGetCount(ownerListPath(WORKSPACES_PREFIX), 1);

    // Both pods patched
    capturePatchPaths(2);
  }

  @Test
  void sync_ownerHasNoLabels_skipsPod() throws Exception {
    Map<String, String> podLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "my-ws");

    // Owner item with no labels object → not stored in owner map → lookup miss → skip
    Map<String, Object> ownerWithoutLabels = Map.of(
        "metadata", Map.of("name", "my-ws", "namespace", "test-ns"));

    Map<String, String> stubs = emptyListStubs();
    stubs.put(listPath("/api/v1/pods"),
        listJson(List.of(objectMap("pod-1", "test-ns", podLabels))));
    stubs.put(ownerListPath(WORKSPACES_PREFIX), listJson(List.of(ownerWithoutLabels)));
    stubGetByPath(stubs);
    stubWorkspaceOwnerDiscovery();

    this.synchronizer.sync();

    verifyNeverPatched();
  }

  @Test
  void sync_exceptionDuringRun_doesNotPropagate() throws Exception {
    when(this.mockApiClient.buildCall(
        anyString(), anyString(), eq("GET"),
        anyList(), anyList(), isNull(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull()))
        .thenThrow(new RuntimeException("connection refused"));

    // Should not throw
    this.synchronizer.sync();
  }

  @Test
  void sync_staleStatefulSetLabels_patchesStatefulSet() throws Exception {
    Map<String, String> stsLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "my-ws",
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "olduser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "old-123");
    Map<String, String> ownerLabels = Map.of(
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "newuser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "new-456");

    Map<String, String> stubs = emptyListStubs();
    stubs.put(listPath("/apis/apps/v1/statefulsets"),
        listJson(List.of(objectMap("sts-1", "test-ns", stsLabels))));
    stubs.put(ownerListPath(WORKSPACES_PREFIX),
        listJson(List.of(objectMap("my-ws", "test-ns", ownerLabels))));
    stubGetByPath(stubs);
    stubWorkspaceOwnerDiscovery();
    stubPatchAlwaysOk();

    this.synchronizer.sync();

    List<String> patchPaths = capturePatchPaths(1);
    assertThat(patchPaths)
        .containsExactly("/apis/apps/v1/namespaces/test-ns/statefulsets/sts-1");
  }

  @Test
  void sync_patchBody_containsOnlyMetadataLabels() throws Exception {
    Map<String, String> stsLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "my-ws",
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "olduser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "old-123");
    Map<String, String> ownerLabels = Map.of(
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "newuser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "new-456");

    Map<String, String> stubs = emptyListStubs();
    stubs.put(listPath("/apis/apps/v1/statefulsets"),
        listJson(List.of(objectMap("sts-1", "test-ns", stsLabels))));
    stubs.put(ownerListPath(WORKSPACES_PREFIX),
        listJson(List.of(objectMap("my-ws", "test-ns", ownerLabels))));
    stubGetByPath(stubs);
    stubWorkspaceOwnerDiscovery();
    stubPatchAlwaysOk();

    this.synchronizer.sync();

    ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(this.mockApiClient).buildCall(
        anyString(), anyString(), eq("PATCH"),
        anyList(), anyList(), bodyCaptor.capture(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull());

    JsonNode body = this.mapper.readTree(bodyCaptor.getValue());
    List<String> rootFields = new ArrayList<>();
    body.fieldNames().forEachRemaining(rootFields::add);
    assertThat(rootFields).containsExactly("metadata");

    List<String> metadataFields = new ArrayList<>();
    body.get("metadata").fieldNames().forEachRemaining(metadataFields::add);
    assertThat(metadataFields).containsExactly("labels");

    JsonNode labels = body.path("metadata").path("labels");
    assertThat(labels.path(LabelConstants.OBJECT_OWN_USERNAME_KEY).textValue())
        .isEqualTo("newuser");
    assertThat(labels.path(LabelConstants.OBJECT_OWN_USERID_KEY).textValue())
        .isEqualTo("new-456");
  }

  @Test
  void sync_coreKindOwner_replicationControllerLookupSucceeds() throws Exception {
    // Pod stamped by a bare ReplicationController: workload-kind=ReplicationController.
    // Discovery resolves the core kind to ResourceInfo("v1", plural); the synchronizer
    // must LIST owners via the /api/v1/replicationcontrollers path (absence selector).
    Map<String, String> podLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "ReplicationController",
        LabelConstants.WORKLOAD_NAME_KEY, "my-rc",
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "olduser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "old-123");
    Map<String, String> ownerLabels = Map.of(
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "newuser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "new-456");

    Map<String, String> stubs = emptyListStubs();
    stubs.put(listPath("/api/v1/pods"),
        listJson(List.of(objectMap("pod-1", "test-ns", podLabels))));
    stubs.put(ownerListPath("/api/v1/replicationcontrollers"),
        listJson(List.of(objectMap("my-rc", "test-ns", ownerLabels))));
    stubGetByPath(stubs);

    when(this.mockDiscovery.getResourcesByKind("ReplicationController"))
        .thenReturn(List.of(new ApiResourceDiscovery.ResourceInfo(
            "v1", "replicationcontrollers")));
    when(this.mockDiscovery.isNamespaced("/replicationcontrollers")).thenReturn(true);
    stubPatchAlwaysOk();

    this.synchronizer.sync();

    verifyGetCount(ownerListPath("/api/v1/replicationcontrollers"), 1);
    List<String> patchPaths = capturePatchPaths(1);
    assertThat(patchPaths).containsExactly("/api/v1/namespaces/test-ns/pods/pod-1");
  }

  @Test
  void sync_intermediateObjectsPatchedBeforePods() throws Exception {
    Map<String, String> staleLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "my-ws",
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "olduser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "old-123");
    Map<String, String> ownerLabels = Map.of(
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "newuser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "new-456");

    Map<String, String> stubs = emptyListStubs();
    stubs.put(listPath("/apis/apps/v1/statefulsets"),
        listJson(List.of(objectMap("sts-1", "test-ns", staleLabels))));
    stubs.put(listPath("/api/v1/pods"),
        listJson(List.of(objectMap("pod-1", "test-ns", staleLabels))));
    stubs.put(ownerListPath(WORKSPACES_PREFIX),
        listJson(List.of(objectMap("my-ws", "test-ns", ownerLabels))));
    stubGetByPath(stubs);
    stubWorkspaceOwnerDiscovery();
    stubPatchAlwaysOk();

    this.synchronizer.sync();

    // Intermediate object (StatefulSet) must be patched before the pod
    List<String> patchPaths = capturePatchPaths(2);
    assertThat(patchPaths).containsExactly(
        "/apis/apps/v1/namespaces/test-ns/statefulsets/sts-1",
        "/api/v1/namespaces/test-ns/pods/pod-1");

    // Owner maps are shared across sync targets: single owner LIST for both objects
    verifyGetCount(ownerListPath(WORKSPACES_PREFIX), 1);
  }

  @Test
  void sync_oneTargetListFails_otherTargetsStillProcessed() throws Exception {
    Map<String, String> podLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "my-ws",
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "olduser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "old-123");
    Map<String, String> ownerLabels = Map.of(
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "newuser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "new-456");

    Map<String, String> stubs = emptyListStubs();
    stubs.put(listPath("/api/v1/pods"),
        listJson(List.of(objectMap("pod-1", "test-ns", podLabels))));
    stubs.put(ownerListPath(WORKSPACES_PREFIX),
        listJson(List.of(objectMap("my-ws", "test-ns", ownerLabels))));
    // statefulsets list fails with 500 — pods must still be processed
    stubGetByPath(stubs, Set.of(listPath("/apis/apps/v1/statefulsets")));
    stubWorkspaceOwnerDiscovery();
    stubPatchAlwaysOk();

    this.synchronizer.sync();

    List<String> patchPaths = capturePatchPaths(1);
    assertThat(patchPaths).containsExactly("/api/v1/namespaces/test-ns/pods/pod-1");
  }

  @Test
  void sync_ownerKindLoadThrows_otherKindsAndTargetsStillProcessed() throws Exception {
    // Owner kind resolution for Workspace blows up. The failure must be caught and
    // cached as a load-failure mark: only Workspace-referencing objects are skipped,
    // while other objects in the SAME target and later targets keep processing.
    Map<String, String> workspaceRefLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "my-ws",
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "olduser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "old-123");
    Map<String, String> operationRefLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Operation",
        LabelConstants.WORKLOAD_NAME_KEY, "my-op",
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "olduser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "old-123");
    Map<String, String> ownerLabels = Map.of(
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "newuser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "new-456");

    Map<String, String> stubs = emptyListStubs();
    stubs.put(listPath("/apis/apps/v1/statefulsets"), listJson(List.of(
        objectMap("sts-1", "test-ns", workspaceRefLabels),
        objectMap("sts-2", "test-ns", operationRefLabels))));
    stubs.put(listPath("/api/v1/pods"), listJson(List.of(
        objectMap("pod-1", "test-ns", workspaceRefLabels),
        objectMap("pod-2", "test-ns", operationRefLabels))));
    stubs.put(ownerListPath(OPERATIONS_PREFIX),
        listJson(List.of(objectMap("my-op", "test-ns", ownerLabels))));
    stubGetByPath(stubs);

    when(this.mockDiscovery.getResourcesByKind("Workspace"))
        .thenThrow(new RuntimeException("discovery snapshot corrupted"));
    stubOperationOwnerDiscovery();
    stubPatchAlwaysOk();

    this.synchronizer.sync();

    // Workspace-referencing objects skipped; Operation-referencing objects patched,
    // both in the same target as the failure (sts-2) and in a later target (pod-2)
    List<String> patchPaths = capturePatchPaths(2);
    assertThat(patchPaths).containsExactly(
        "/apis/apps/v1/namespaces/test-ns/statefulsets/sts-2",
        "/api/v1/namespaces/test-ns/pods/pod-2");

    // Load-failure mark is cached: the throwing kind is resolved only once per cycle
    verify(this.mockDiscovery, times(1)).getResourcesByKind("Workspace");
  }

  @Test
  void sync_targetListPaginated_processesAllPages() throws Exception {
    Map<String, String> staleLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "my-ws",
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "olduser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "old-123");
    Map<String, String> ownerLabels = Map.of(
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "newuser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "new-456");

    // Token with characters that require URL encoding ('=', '+', '/')
    String continueToken = "eyJydiI6MTIzNDU2Nzg5fQ+/==";

    Map<String, String> stubs = emptyListStubs();
    stubs.put(listPath("/api/v1/pods"), listJsonWithContinue(
        List.of(objectMap("pod-1", "test-ns", staleLabels)), continueToken));
    stubs.put(listPath("/api/v1/pods", continueToken),
        listJson(List.of(objectMap("pod-2", "test-ns", staleLabels))));
    stubs.put(ownerListPath(WORKSPACES_PREFIX),
        listJson(List.of(objectMap("my-ws", "test-ns", ownerLabels))));
    stubGetByPath(stubs);
    stubWorkspaceOwnerDiscovery();
    stubPatchAlwaysOk();

    this.synchronizer.sync();

    // Objects from both pages processed and patched
    List<String> patchPaths = capturePatchPaths(2);
    assertThat(patchPaths).containsExactly(
        "/api/v1/namespaces/test-ns/pods/pod-1",
        "/api/v1/namespaces/test-ns/pods/pod-2");
  }

  @Test
  void sync_ownerListPaginated_accumulatesAllPages() throws Exception {
    Map<String, String> podLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "my-ws",
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "olduser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "old-123");
    Map<String, String> otherOwnerLabels = Map.of(
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "otheruser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "other-789");
    Map<String, String> ownerLabels = Map.of(
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "newuser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "new-456");

    String continueToken = "b3duZXItcGFnZS0y==";

    Map<String, String> stubs = emptyListStubs();
    stubs.put(listPath("/api/v1/pods"),
        listJson(List.of(objectMap("pod-1", "test-ns", podLabels))));
    // Owner appears only on the second page of the owner LIST
    stubs.put(ownerListPath(WORKSPACES_PREFIX), listJsonWithContinue(
        List.of(objectMap("other-ws", "test-ns", otherOwnerLabels)), continueToken));
    stubs.put(ownerListPath(WORKSPACES_PREFIX, continueToken),
        listJson(List.of(objectMap("my-ws", "test-ns", ownerLabels))));
    stubGetByPath(stubs);
    stubWorkspaceOwnerDiscovery();
    stubPatchAlwaysOk();

    this.synchronizer.sync();

    verifyGetCount(ownerListPath(WORKSPACES_PREFIX), 1);
    verifyGetCount(ownerListPath(WORKSPACES_PREFIX, continueToken), 1);
    List<String> patchPaths = capturePatchPaths(1);
    assertThat(patchPaths).containsExactly("/api/v1/namespaces/test-ns/pods/pod-1");
  }

  @Test
  void sync_targetListPageFails_wholeTargetSkipped() throws Exception {
    // Page 1 succeeds with a continue token, page 2 fails with 500: the whole list
    // must be treated as failed — objects from page 1 must NOT be processed
    // (a partial result must never be treated as a complete one).
    Map<String, String> podLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "my-ws",
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "olduser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "old-123");
    Map<String, String> ownerLabels = Map.of(
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "newuser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "new-456");

    String continueToken = "cGFnZS0y==";

    Map<String, String> stubs = emptyListStubs();
    stubs.put(listPath("/api/v1/pods"), listJsonWithContinue(
        List.of(objectMap("pod-1", "test-ns", podLabels)), continueToken));
    stubs.put(ownerListPath(WORKSPACES_PREFIX),
        listJson(List.of(objectMap("my-ws", "test-ns", ownerLabels))));
    stubGetByPath(stubs, Set.of(listPath("/api/v1/pods", continueToken)));
    stubWorkspaceOwnerDiscovery();
    stubPatchAlwaysOk();

    this.synchronizer.sync();

    verifyNeverPatched();
  }

  @Test
  void sync_continueTokenRepeats_abortsInsteadOfLooping() throws Exception {
    // A misbehaving API server keeps returning the same continue token: the list
    // must abort (treated as failed) instead of looping forever and wedging the
    // single-threaded synchronizer.
    Map<String, String> podLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "my-ws",
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "olduser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "old-123");

    String loopToken = "bG9vcC10b2tlbg==";
    String pageWithLoopToken = listJsonWithContinue(
        List.of(objectMap("pod-1", "test-ns", podLabels)), loopToken);

    Map<String, String> stubs = emptyListStubs();
    stubs.put(listPath("/api/v1/pods"), pageWithLoopToken);
    // The continuation page returns the SAME token again
    stubs.put(listPath("/api/v1/pods", loopToken), pageWithLoopToken);
    stubGetByPath(stubs);
    stubWorkspaceOwnerDiscovery();
    stubPatchAlwaysOk();

    this.synchronizer.sync();

    // Aborted after detecting the repeated token: exactly two page GETs, no patches
    verifyGetCount(listPath("/api/v1/pods", loopToken), 1);
    verifyNeverPatched();
  }

}
