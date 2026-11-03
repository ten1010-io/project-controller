package io.ten1010.aipub.projectcontroller.domain.aipubbackend.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.ten1010.aipub.projectcontroller.domain.aipubbackend.dto.ArtifactListOptions;
import io.ten1010.common.apiclient.ApiClient;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

/**
 * aipub-backend 와의 HTTP 계약을 고정한다. 쿼리 파라미터명이 어긋나면 백엔드는 모르는 파라미터를 무시하므로, 에러 없이 스캔 요약 비용이 그대로 남는다.
 */
class ArtifactServiceImplTest {

  private static final String BASE_PATH = "https://aipub-backend-gateway:8443/api/v1alpha1";

  private final AtomicReference<Request> sent = new AtomicReference<>();

  private ApiClient client() {
    ApiClient client = new ApiClient();
    client.setBasePath(BASE_PATH);
    client.setHttpClient(client.getHttpClient().newBuilder()
        .addInterceptor(chain -> {
          Request request = chain.request();
          this.sent.set(request);
          return new Response.Builder()
              .request(request)
              .protocol(Protocol.HTTP_1_1)
              .code(200)
              .message("stub")
              .body(ResponseBody.create("[]", MediaType.parse("application/json")))
              .build();
        })
        .build());
    return client;
  }

  @Test
  void withScanOverviewFalse_sendsItWithPageOptions() {
    ArtifactListOptions options = new ArtifactListOptions();
    options.setPageOffset(1);
    options.setPageSize(100);
    options.setWithScanOverview(false);

    new ArtifactServiceImpl(client()).listArtifacts("10", "nginx", options);

    Request request = this.sent.get();
    assertThat(request.method()).isEqualTo("GET");
    assertThat(request.url().encodedPath())
        .isEqualTo("/api/v1alpha1/imagehubs/10/repositories/nginx/artifacts");
    assertThat(request.url().queryParameter("withScanOverview")).isEqualTo("false");
    assertThat(request.url().queryParameter("pageOffset")).isEqualTo("1");
    assertThat(request.url().queryParameter("pageSize")).isEqualTo("100");
  }

  @Test
  void withScanOverviewUnset_omitsIt() {
    new ArtifactServiceImpl(client()).listArtifacts("10", "nginx", new ArtifactListOptions());

    assertThat(this.sent.get().url().queryParameterNames()).doesNotContain("withScanOverview");
  }

}
