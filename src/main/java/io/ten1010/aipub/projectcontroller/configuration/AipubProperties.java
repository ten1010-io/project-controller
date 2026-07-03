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
  @Nullable
  private Boolean verifyingSsl;
  @Nullable
  private String username;
  @Nullable
  private String password;
  private List<String> reservedNamespace = new ArrayList<>();
  private List<String> addOwnerExceptGvkList = new ArrayList<>();
  /**
   * project controller가 reconcile/mutating 대상에서 제외할 워크로드의 라벨 셀렉터 목록.
   * {@code "key=value"}(값 일치) 또는 {@code "key"}(존재만 확인) 형태를 지원하며, 하나라도
   * 매칭되면 제외한다. virt-operator처럼 자체 워크로드를 직접 소유하는 인프라 오퍼레이터와의
   * 소유권 충돌을 막기 위한 용도다.
   */
  private List<String> reconcileExcludedLabelSelectors = new ArrayList<>();

}
