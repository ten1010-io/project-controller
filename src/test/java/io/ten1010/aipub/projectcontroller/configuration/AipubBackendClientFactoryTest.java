package io.ten1010.aipub.projectcontroller.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.ten1010.common.apiclient.ApiClient;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AipubBackendClientFactoryTest {

  private static final String SERVER_URL = "https://aipub-backend-gateway.aipub.svc.cluster.local:8443";

  @Test
  void createdClientUsesInternalCaAndClientCertificateWithoutBasicAuth(@TempDir Path dir)
      throws Exception {
    AipubProperties.MtlsProperty mtls = mtlsProperty(writeCaCertificate(dir), writeKeyStore(dir));

    ApiClient client = AipubBackendClientFactory.create(SERVER_URL, true, mtls);

    assertThat(client.getBasePath()).isEqualTo(SERVER_URL + "/api/v1alpha1");
    assertThat(client.isVerifyingSsl()).isTrue();
    assertThat(client.getKeyManagers()).isNotNull();
    assertThat(client.getSslCaCert()).contains("BEGIN CERTIFICATE");
    // Basic Auth 를 대체한 것이므로 Authentication 이 남아 있으면 안 된다.
    assertThat(client.getAuthentication()).isNull();
  }

  @Test
  void verificationCanBeDisabledWhileStillPresentingClientCertificate(@TempDir Path dir)
      throws Exception {
    AipubProperties.MtlsProperty mtls = mtlsProperty(writeCaCertificate(dir), writeKeyStore(dir));

    ApiClient client = AipubBackendClientFactory.create(SERVER_URL, false, mtls);

    assertThat(client.isVerifyingSsl()).isFalse();
    // 비상 스위치는 서버 검증만 끈다 — 클라이언트 인증서는 계속 제시되므로 게이트웨이 쪽
    // mTLS 인증은 그대로 동작한다.
    assertThat(client.getKeyManagers()).isNotNull();
  }

  @Test
  void failsWhenKeyStoreFileIsMissing(@TempDir Path dir) throws Exception {
    AipubProperties.MtlsProperty mtls = mtlsProperty(
        writeCaCertificate(dir), dir.resolve("absent.p12"));

    assertThatThrownBy(() -> AipubBackendClientFactory.create(SERVER_URL, true, mtls))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to load client key store");
  }

  @Test
  void failsWhenCaCertificateFileIsMissing(@TempDir Path dir) throws Exception {
    AipubProperties.MtlsProperty mtls = mtlsProperty(
        dir.resolve("absent.crt"), writeKeyStore(dir));

    assertThatThrownBy(() -> AipubBackendClientFactory.create(SERVER_URL, true, mtls))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to read internal CA certificate");
  }

  @Test
  void failsWhenMtlsPropertiesAreNotConfigured() {
    AipubProperties.MtlsProperty mtls = new AipubProperties.MtlsProperty();

    assertThatThrownBy(() -> AipubBackendClientFactory.create(SERVER_URL, true, mtls))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("app.aipub.mtls.key-store-file");
  }

  private static AipubProperties.MtlsProperty mtlsProperty(Path caFile, Path keyStoreFile) {
    AipubProperties.MtlsProperty mtls = new AipubProperties.MtlsProperty();
    mtls.setCaCertificateFile(caFile.toString());
    mtls.setKeyStoreFile(keyStoreFile.toString());
    return mtls;
  }

  private static Path writeCaCertificate(Path dir) throws Exception {
    Path caFile = dir.resolve("aipub-internal-ca.crt");
    try (InputStream in = Objects.requireNonNull(
        AipubBackendClientFactoryTest.class.getResourceAsStream("/mtls/internal-ca.crt"))) {
      Files.write(caFile, in.readAllBytes());
    }
    return caFile;
  }

  /** installer 가 발급하는 것과 같은 빈 암호 PKCS#12. 키 엔트리는 로딩 검증에 필요하지 않다. */
  private static Path writeKeyStore(Path dir) throws Exception {
    Path keyStoreFile = dir.resolve("tls.p12");
    KeyStore keyStore = KeyStore.getInstance("PKCS12");
    keyStore.load(null, new char[0]);
    try (OutputStream out = Files.newOutputStream(keyStoreFile)) {
      keyStore.store(out, new char[0]);
    }
    return keyStoreFile;
  }

}
