package io.ten1010.aipub.projectcontroller.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * installer 가 ConfigMap 으로 넣는 환경변수 이름이 실제로 프로퍼티에 바인딩되는지 확인한다.
 * 이름이 틀리면 기본값(null)이 들어가 기동 시점에야 드러나므로 여기서 못박아 둔다.
 */
class AipubPropertiesBindingTest {

  @Test
  void mtlsPropertiesBindFromInstallerEnvironmentVariableNames() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("APP_AIPUB_ENABLED", "true");
    variables.put("APP_AIPUB_SERVER_URL",
        "https://aipub-backend-gateway.aipub.svc.cluster.local:8443");
    variables.put("APP_AIPUB_MTLS_KEYSTOREFILE", "/etc/aipub/tls/tls.p12");
    variables.put("APP_AIPUB_MTLS_CACERTIFICATEFILE", "/certificates/aipub-internal-ca.crt");

    AipubProperties properties = bind(variables);

    assertThat(properties.getEnabled()).isTrue();
    assertThat(properties.getServerUrl())
        .isEqualTo("https://aipub-backend-gateway.aipub.svc.cluster.local:8443");
    assertThat(properties.getMtls().getKeyStoreFile()).isEqualTo("/etc/aipub/tls/tls.p12");
    assertThat(properties.getMtls().getCaCertificateFile())
        .isEqualTo("/certificates/aipub-internal-ca.crt");
    // installer 는 암호를 주입하지 않는다 — 발급되는 p12 가 빈 암호이기 때문이다.
    assertThat(properties.getMtls().getKeyStorePassword()).isEmpty();
    // 주입이 없으면 검증은 켜진 상태여야 한다.
    assertThat(properties.isVerifyingSsl()).isTrue();
  }

  @Test
  void verifyingSslCanBeTurnedOffByInstallerEnvironmentVariable() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("APP_AIPUB_VERIFYINGSSL", "false");

    assertThat(bind(variables).isVerifyingSsl()).isFalse();
  }

  private static AipubProperties bind(Map<String, Object> variables) {
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
    return new Binder(ConfigurationPropertySources.get(environment))
        .bind("app.aipub", Bindable.of(AipubProperties.class))
        .orElseThrow(() -> new AssertionError("app.aipub is not bound"));
  }

}
