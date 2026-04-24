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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.openapi.ApiClient;
import io.ten1010.aipub.projectcontroller.domain.k8s.LabelConstants;
import io.ten1010.aipub.projectcontroller.domain.k8s.ObjectMapperFactory;
import java.util.List;
import java.util.Map;
import okhttp3.Call;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserLabelSynchronizerTest {

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

  private String podListJson(List<Map<String, Object>> pods) throws Exception {
    return this.mapper.writeValueAsString(Map.of(
        "apiVersion", "v1",
        "kind", "PodList",
        "items", pods));
  }

  private Map<String, Object> podMap(String name, String namespace,
      Map<String, String> labels) {
    return Map.of(
        "metadata", Map.of(
            "name", name,
            "namespace", namespace,
            "labels", labels));
  }

  private String ownerObjectJson(Map<String, String> labels) throws Exception {
    return this.mapper.writeValueAsString(Map.of(
        "metadata", Map.of(
            "name", "test-workspace",
            "namespace", "test-ns",
            "labels", labels)));
  }

  @Test
  void sync_noPods_doesNothing() throws Exception {
    String emptyPodList = podListJson(List.of());
    Call listCall = mockCallWithResponse(buildResponse(200, emptyPodList));
    when(this.mockApiClient.buildCall(
        anyString(), anyString(), eq("GET"),
        anyList(), anyList(), isNull(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull()))
        .thenReturn(listCall);

    this.synchronizer.sync();

    verify(this.mockApiClient, never()).buildCall(
        anyString(), anyString(), eq("PATCH"),
        anyList(), anyList(), any(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull());
  }

  @Test
  void sync_podAlreadyInSync_doesNotPatch() throws Exception {
    Map<String, String> podLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "my-ws",
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "testuser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "user-123");
    String podList = podListJson(List.of(podMap("pod-1", "test-ns", podLabels)));

    Map<String, String> ownerLabels = Map.of(
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "testuser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "user-123");
    String ownerJson = ownerObjectJson(ownerLabels);

    Call listCall = mockCallWithResponse(buildResponse(200, podList));
    Call ownerCall = mockCallWithResponse(buildResponse(200, ownerJson));

    when(this.mockDiscovery.getResourcesByKind("Workspace"))
        .thenReturn(List.of(new ApiResourceDiscovery.ResourceInfo(
            "aipub.ten1010.io/v1alpha1", "workspaces")));
    when(this.mockDiscovery.isNamespaced("aipub.ten1010.io/workspaces")).thenReturn(true);

    when(this.mockApiClient.buildCall(
        anyString(), anyString(), eq("GET"),
        anyList(), anyList(), isNull(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull()))
        .thenReturn(listCall)
        .thenReturn(ownerCall);

    this.synchronizer.sync();

    verify(this.mockApiClient, never()).buildCall(
        anyString(), anyString(), eq("PATCH"),
        anyList(), anyList(), any(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull());
  }

  @Test
  void sync_podOutOfSync_patchesPod() throws Exception {
    Map<String, String> podLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "my-ws",
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "olduser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "old-123");
    String podList = podListJson(List.of(podMap("pod-1", "test-ns", podLabels)));

    Map<String, String> ownerLabels = Map.of(
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "newuser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "new-456");
    String ownerJson = ownerObjectJson(ownerLabels);

    Call listCall = mockCallWithResponse(buildResponse(200, podList));
    Call ownerCall = mockCallWithResponse(buildResponse(200, ownerJson));
    Call patchCall = mockCallWithResponse(buildResponse(200, "{}"));

    when(this.mockDiscovery.getResourcesByKind("Workspace"))
        .thenReturn(List.of(new ApiResourceDiscovery.ResourceInfo(
            "aipub.ten1010.io/v1alpha1", "workspaces")));
    when(this.mockDiscovery.isNamespaced("aipub.ten1010.io/workspaces")).thenReturn(true);

    when(this.mockApiClient.buildCall(
        anyString(), anyString(), eq("GET"),
        anyList(), anyList(), isNull(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull()))
        .thenReturn(listCall)
        .thenReturn(ownerCall);

    when(this.mockApiClient.buildCall(
        anyString(), anyString(), eq("PATCH"),
        anyList(), anyList(), any(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull()))
        .thenReturn(patchCall);

    this.synchronizer.sync();

    verify(this.mockApiClient).buildCall(
        anyString(), anyString(), eq("PATCH"),
        anyList(), anyList(), any(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull());
  }

  @Test
  void sync_ownerNotFound_skipsPod() throws Exception {
    Map<String, String> podLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "missing-ws");
    String podList = podListJson(List.of(podMap("pod-1", "test-ns", podLabels)));

    Call listCall = mockCallWithResponse(buildResponse(200, podList));
    Call ownerCall = mockCallWithResponse(buildResponse(404, ""));

    when(this.mockDiscovery.getResourcesByKind("Workspace"))
        .thenReturn(List.of(new ApiResourceDiscovery.ResourceInfo(
            "aipub.ten1010.io/v1alpha1", "workspaces")));
    when(this.mockDiscovery.isNamespaced("aipub.ten1010.io/workspaces")).thenReturn(true);

    when(this.mockApiClient.buildCall(
        anyString(), anyString(), eq("GET"),
        anyList(), anyList(), isNull(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull()))
        .thenReturn(listCall)
        .thenReturn(ownerCall);

    this.synchronizer.sync();

    verify(this.mockApiClient, never()).buildCall(
        anyString(), anyString(), eq("PATCH"),
        anyList(), anyList(), any(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull());
  }

  @Test
  void sync_cacheHit_doesNotFetchOwnerAgain() throws Exception {
    Map<String, String> podLabels1 = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "my-ws",
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "olduser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "old-123");
    Map<String, String> podLabels2 = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "my-ws",
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "olduser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "old-123");
    String podList = podListJson(List.of(
        podMap("pod-1", "test-ns", podLabels1),
        podMap("pod-2", "test-ns", podLabels2)));

    Map<String, String> ownerLabels = Map.of(
        LabelConstants.OBJECT_OWN_USERNAME_KEY, "newuser",
        LabelConstants.OBJECT_OWN_USERID_KEY, "new-456");
    String ownerJson = ownerObjectJson(ownerLabels);

    Call listCall = mockCallWithResponse(buildResponse(200, podList));
    Call ownerCall = mockCallWithResponse(buildResponse(200, ownerJson));
    Call patchCall1 = mockCallWithResponse(buildResponse(200, "{}"));
    Call patchCall2 = mockCallWithResponse(buildResponse(200, "{}"));

    when(this.mockDiscovery.getResourcesByKind("Workspace"))
        .thenReturn(List.of(new ApiResourceDiscovery.ResourceInfo(
            "aipub.ten1010.io/v1alpha1", "workspaces")));
    when(this.mockDiscovery.isNamespaced("aipub.ten1010.io/workspaces")).thenReturn(true);

    // GET: first call returns pod list, second returns owner (only once)
    when(this.mockApiClient.buildCall(
        anyString(), anyString(), eq("GET"),
        anyList(), anyList(), isNull(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull()))
        .thenReturn(listCall)
        .thenReturn(ownerCall);

    when(this.mockApiClient.buildCall(
        anyString(), anyString(), eq("PATCH"),
        anyList(), anyList(), any(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull()))
        .thenReturn(patchCall1)
        .thenReturn(patchCall2);

    this.synchronizer.sync();

    // Owner fetched only once (1 list + 1 owner = 2 GET calls)
    verify(this.mockApiClient, times(2)).buildCall(
        anyString(), anyString(), eq("GET"),
        anyList(), anyList(), isNull(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull());

    // Both pods patched
    verify(this.mockApiClient, times(2)).buildCall(
        anyString(), anyString(), eq("PATCH"),
        anyList(), anyList(), any(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull());
  }

  @Test
  void sync_ownerHasNoLabels_skipsPod() throws Exception {
    Map<String, String> podLabels = Map.of(
        LabelConstants.WORKLOAD_KIND_KEY, "Workspace",
        LabelConstants.WORKLOAD_NAME_KEY, "my-ws");
    String podList = podListJson(List.of(podMap("pod-1", "test-ns", podLabels)));

    // Owner object with no labels
    String ownerJson = this.mapper.writeValueAsString(Map.of(
        "metadata", Map.of("name", "my-ws", "namespace", "test-ns")));

    Call listCall = mockCallWithResponse(buildResponse(200, podList));
    Call ownerCall = mockCallWithResponse(buildResponse(200, ownerJson));

    when(this.mockDiscovery.getResourcesByKind("Workspace"))
        .thenReturn(List.of(new ApiResourceDiscovery.ResourceInfo(
            "aipub.ten1010.io/v1alpha1", "workspaces")));
    when(this.mockDiscovery.isNamespaced("aipub.ten1010.io/workspaces")).thenReturn(true);

    when(this.mockApiClient.buildCall(
        anyString(), anyString(), eq("GET"),
        anyList(), anyList(), isNull(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull()))
        .thenReturn(listCall)
        .thenReturn(ownerCall);

    this.synchronizer.sync();

    verify(this.mockApiClient, never()).buildCall(
        anyString(), anyString(), eq("PATCH"),
        anyList(), anyList(), any(),
        anyMap(), anyMap(), anyMap(),
        any(String[].class), isNull());
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

}
