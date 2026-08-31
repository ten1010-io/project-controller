package io.ten1010.aipub.projectcontroller.configuration;

import io.ten1010.common.apiclient.ApiClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Objects;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * aipub-backend-gateway 의 머신 전용 포트(mTLS)에 붙는 {@link ApiClient} 를 만든다.
 *
 * <p>클라이언트 인증서가 유일한 인증 수단이다 — Basic Auth 헤더를 붙이지 않는다. 인증서 파일이
 * 없으면 기동을 실패시킨다. 인증서 Secret 은 차트 설치보다 먼저 존재해야 하므로, 조용히 인증
 * 없는 상태로 내려가는 것보다 못 뜨는 편이 낫다.
 */
@Slf4j
public final class AipubBackendClientFactory {

  private static final String API_BASE_PATH = "/api/v1alpha1";
  private static final String KEY_STORE_TYPE = "PKCS12";

  private AipubBackendClientFactory() {
  }

  public static ApiClient create(String serverUrl, boolean verifyingSsl,
      AipubProperties.MtlsProperty mtls) {
    String keyStoreFile = Objects.requireNonNull(mtls.getKeyStoreFile(),
        "app.aipub.mtls.key-store-file must be configured");
    String caCertificateFile = Objects.requireNonNull(mtls.getCaCertificateFile(),
        "app.aipub.mtls.ca-certificate-file must be configured");

    if (!verifyingSsl) {
      // 비상 스위치가 켜진 채 방치되면 서버 위장을 막지 못한다. 조용히 넘어가지 않게 한다.
      log.warn("Server certificate verification is disabled for aipub-backend."
          + " Client certificate is still presented, but the server is not verified."
          + " Set app.aipub.verifying-ssl=true once the certificate issue is resolved.");
    }

    ApiClient client = new ApiClient();
    client.setBasePath(serverUrl + API_BASE_PATH);
    // 서버 인증서는 내부 CA 만 신뢰한다. 게이트웨이 인증서 SAN 에 클러스터 내부 DNS 가
    // 들어 있어 호스트명 검증도 그대로 통과한다.
    client.setVerifyingSsl(verifyingSsl);
    client.setSslCaCert(readCaCertificate(caCertificateFile));
    client.setKeyManagers(loadKeyManagers(keyStoreFile, mtls.getKeyStorePassword()));
    return client;
  }

  /** {@link ApiClient#setSslCaCert(String)} 는 파일 경로가 아니라 PEM 내용을 받는다. */
  private static String readCaCertificate(String path) {
    try {
      return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read internal CA certificate: " + path, e);
    }
  }

  private static KeyManager[] loadKeyManagers(String path, String password) {
    char[] secret = password.toCharArray();
    try (InputStream in = Files.newInputStream(Path.of(path))) {
      KeyStore keyStore = KeyStore.getInstance(KEY_STORE_TYPE);
      keyStore.load(in, secret);
      KeyManagerFactory factory = KeyManagerFactory.getInstance(
          KeyManagerFactory.getDefaultAlgorithm());
      factory.init(keyStore, secret);
      return factory.getKeyManagers();
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalStateException("Failed to load client key store: " + path, e);
    }
  }

}
