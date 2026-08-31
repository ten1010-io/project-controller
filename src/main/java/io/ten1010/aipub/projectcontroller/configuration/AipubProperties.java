package io.ten1010.aipub.projectcontroller.configuration;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.aipub")
@Data
public class AipubProperties {

  @Nullable
  private Boolean enabled;
  @Nullable
  private String serverUrl;
  /** 이미지 레지스트리(harbor)의 외부 접근 주소. (예: https://aipub-harbor.example.com) */
  @Nullable
  private String harborExternalUrl;
  /** aipub-backend-gateway 머신 전용 포트(mTLS) 접속에 쓰는 인증 재료. */
  private MtlsProperty mtls = new MtlsProperty();
  private List<String> reservedNamespace = new ArrayList<>();
  private List<String> addOwnerExceptGvkList = new ArrayList<>();
  /**
   * project controller가 reconcile/mutating 대상에서 제외할 워크로드의 라벨 셀렉터 목록.
   * {@code "key=value"}(값 일치) 또는 {@code "key"}(존재만 확인) 형태를 지원하며, 하나라도
   * 매칭되면 제외한다. 자체 워크로드를 직접 소유하는 인프라 오퍼레이터와의 소유권 충돌을 막기
   * 위한 용도다.
   *
   * <p>네임스페이스 전체를 제외하면서 그 파드를 project-managed 노드에도 올리려면, 이 셀렉터 대신
   * 네임스페이스 라벨 기반 allowlist를 쓴다.
   * {@link io.ten1010.aipub.projectcontroller.domain.k8s.NamespaceAllowlistResolver} 참고.
   */
  private List<String> reconcileExcludedLabelSelectors = new ArrayList<>();

  /**
   * 내부 서비스 간 통신(mTLS) 재료. 두 파일 모두 installer 가 Secret 으로 마운트하며,
   * 신원(keystore)과 신뢰(truststore)를 서로 다른 경로에 둔다.
   */
  @Data
  public static class MtlsProperty {

    /** 자기 신원. 서비스 전용 Secret 이 마운트된 PKCS#12 파일. */
    @Nullable
    private String keyStoreFile;
    /** installer 가 발급하는 PKCS#12 는 빈 암호다. */
    private String keyStorePassword = "";
    /** 신뢰. 내부 전용 CA 인증서(PEM). ingress CA 와는 다른 CA 다. */
    @Nullable
    private String caCertificateFile;

  }

}
