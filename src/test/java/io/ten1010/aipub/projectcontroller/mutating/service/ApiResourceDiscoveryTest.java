package io.ten1010.aipub.projectcontroller.mutating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import io.kubernetes.client.openapi.ApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ApiResourceDiscoveryTest {

  private ApiClient mockApiClient;
  private ApiResourceDiscovery discovery;

  @BeforeEach
  void setUp() throws Exception {
    this.mockApiClient = mock(ApiClient.class);
    when(this.mockApiClient.getBasePath()).thenReturn("https://localhost:6443");

    // buildSnapshot() 호출 시 /api/v1, /apis 응답 mock
    // /api/v1 — core resources: pods(namespaced), nodes(cluster-scoped)
    mockApiCall("/api/v1", """
        {
          "resources": [
            {"name": "pods", "kind": "Pod", "namespaced": true},
            {"name": "nodes", "kind": "Node", "namespaced": false},
            {"name": "services", "kind": "Service", "namespaced": true}
          ]
        }
        """);

    // /apis — apps/v1 group
    mockApiCall("/apis", """
        {
          "groups": [
            {
              "name": "apps",
              "versions": [{"groupVersion": "apps/v1"}]
            }
          ]
        }
        """);

    mockApiCall("/apis/apps/v1", """
        {
          "resources": [
            {"name": "deployments", "kind": "Deployment", "namespaced": true}
          ]
        }
        """);

    this.discovery = new ApiResourceDiscovery(this.mockApiClient);
  }

  private void mockApiCall(String path, String responseBody) throws Exception {
    Call call = mock(Call.class);
    Response response = new Response.Builder()
        .request(new Request.Builder().url("https://localhost:6443" + path).build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(ResponseBody.create(responseBody, MediaType.get("application/json")))
        .build();
    when(call.execute()).thenReturn(response);
    when(this.mockApiClient.buildCall(
        any(), eq(path), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(call);
  }

  // === buildSnapshot — booleanValue 테스트 ===

  @Nested
  class BooleanValueBehavior {

    // booleanValue()는 JSON boolean만 true로 인식.
    // 수정 전 asBoolean()은 문자열 "true"도 true로 변환했음.
    @Test
    void namespacedField_trueBoolean_isNamespacedReturnsTrue() {
      // pods는 namespaced: true (boolean)
      assertThat(discovery.isNamespaced("/pods")).isTrue();
    }

    @Test
    void namespacedField_falseBoolean_isNamespacedReturnsFalse() {
      // nodes는 namespaced: false (boolean)
      assertThat(discovery.isNamespaced("/nodes")).isFalse();
    }

    @Test
    void namespacedFieldMissing_booleanValueReturnsFalse() throws Exception {
      // "namespaced" 필드가 없는 리소스 → booleanValue()는 false 반환
      // asBoolean()도 false 반환하지만, booleanValue()는 명시적 boolean만 처리
      ApiClient client = mock(ApiClient.class);
      when(client.getBasePath()).thenReturn("https://localhost:6443");

      mockApiCallForClient(client, "/api/v1", """
          {
            "resources": [
              {"name": "testresources", "kind": "TestResource"}
            ]
          }
          """);
      mockApiCallForClient(client, "/apis", """
          {"groups": []}
          """);

      ApiResourceDiscovery disc = new ApiResourceDiscovery(client);
      // namespaced 필드 누락 → false로 분류 (cluster-scoped 취급)
      assertThat(disc.isNamespaced("/testresources")).isFalse();
    }

    private void mockApiCallForClient(ApiClient client, String path, String body)
        throws Exception {
      Call call = mock(Call.class);
      Response response = new Response.Builder()
          .request(new Request.Builder().url("https://localhost:6443" + path).build())
          .protocol(Protocol.HTTP_1_1)
          .code(200)
          .message("OK")
          .body(ResponseBody.create(body, MediaType.get("application/json")))
          .build();
      when(call.execute()).thenReturn(response);
      when(client.buildCall(
          any(), eq(path), any(), any(), any(), any(), any(), any(), any(), any(), any()))
          .thenReturn(call);
    }
  }

  // === getAllObjectNames — "core" group alias 테스트 ===

  @Nested
  class CoreGroupAlias {

    // 수정 후: "core" group도 /api/v1/ 경로 사용
    // 수정 전: "core"는 non-core 경로로 처리되어 groupVersion 조회 실패 → RuntimeException
    @Test
    void emptyGroup_usesCorePath() throws Exception {
      // "/pods" → /api/v1/pods
      mockApiCall("/api/v1/pods", """
          {
            "items": [
              {"metadata": {"name": "pod-1"}},
              {"metadata": {"name": "pod-2"}}
            ]
          }
          """);

      List<String> names = discovery.getAllObjectNames("/pods", null);
      assertThat(names).containsExactly("pod-1", "pod-2");
    }

    @Test
    void coreGroup_usesCorePath() throws Exception {
      // "core/pods" → /api/v1/pods (수정 후 동작)
      mockApiCall("/api/v1/pods", """
          {
            "items": [
              {"metadata": {"name": "pod-1"}},
              {"metadata": {"name": "pod-2"}}
            ]
          }
          """);

      List<String> names = discovery.getAllObjectNames("core/pods", null);
      assertThat(names).containsExactly("pod-1", "pod-2");
    }

    @Test
    void emptyGroup_withNamespace_usesNamespacedCorePath() throws Exception {
      // "/pods" + namespace "ns1" → /api/v1/namespaces/ns1/pods
      mockApiCall("/api/v1/namespaces/ns1/pods", """
          {
            "items": [
              {"metadata": {"name": "ns1-pod"}}
            ]
          }
          """);

      List<String> names = discovery.getAllObjectNames("/pods", "ns1");
      assertThat(names).containsExactly("ns1-pod");
    }

    // "core/pods"는 snapshot에 "/pods"로 저장되므로
    // isNamespaced("core/pods") 호출 시 GroupResourceNotFoundException 발생.
    // Python도 동일 동작 — core alias는 namespace 없는 경우에만 유효.
    @Test
    void coreGroup_withNamespace_throwsBecauseNotInSnapshot() {
      assertThatThrownBy(() -> discovery.getAllObjectNames("core/pods", "ns1"))
          .isInstanceOf(GroupResourceNotFoundException.class);
    }

    @Test
    void nonCoreGroup_usesApisPath() throws Exception {
      // "apps/deployments" → /apis/apps/v1/deployments
      mockApiCall("/apis/apps/v1/deployments", """
          {
            "items": [
              {"metadata": {"name": "deploy-1"}}
            ]
          }
          """);

      List<String> names = discovery.getAllObjectNames("apps/deployments", null);
      assertThat(names).containsExactly("deploy-1");
    }

    @Test
    void nonCoreGroup_unknownGroupVersion_throwsException() {
      // "unknown/resources" → groupVersion 없음 → RuntimeException
      assertThatThrownBy(() -> discovery.getAllObjectNames("unknown/resources", null))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Unknown groupVersion");
    }
  }

  // === isExist / isNamespaced 기본 동작 ===

  @Nested
  class BasicDiscovery {

    @Test
    void coreResource_exists() {
      assertThat(discovery.isExist("/pods")).isTrue();
      assertThat(discovery.isExist("/nodes")).isTrue();
    }

    @Test
    void nonCoreResource_exists() {
      assertThat(discovery.isExist("apps/deployments")).isTrue();
    }

    @Test
    void unknownResource_doesNotExist() {
      assertThat(discovery.isExist("unknown/resources")).isFalse();
    }

    // "core/pods"는 snapshot에 "/pods"로 저장되므로 isExist는 false
    // Python도 동일 동작
    @Test
    void coreAliasResource_doesNotExist() {
      assertThat(discovery.isExist("core/pods")).isFalse();
    }
  }
}
