package io.ten1010.aipub.projectcontroller.domain.k8s;

import java.util.List;

/**
 * 생성자 소유권(creator-ownership) RBAC 정책의 단일 정의처: 어떤 타입을 추적하고
 * (OWNED_TARGETS) 무엇을 제외하는지(SYSTEM_DERIVED_LABEL_KEYS)를 정의한다.
 * 부여 verbs 는 기존 워크로드 경로와 완전히 동일하며(update/patch/delete),
 * ReconciliationService 의 공용 빌더(buildUpdateDeleteRoleRule)가 생성한다.
 * 관측 메커니즘(인포머)은 informer.owned 패키지가 담당한다.
 */
public final class OwnershipPolicy {

  /**
   * 시스템 파생물 표식 레이블. 이 레이블이 붙은 오브젝트는 소유자 레이블이 있어도
   * 소유권 추적에서 제외한다. 예: K8s endpoints 컨트롤러는 Service 의 레이블 전체를
   * 자신이 만드는 Endpoints 에 복사하므로, 사용자 Service 에서 파생된 시스템 Endpoints 가
   * username 레이블을 상속받는다 — 이런 파생물에 소유권 규칙을 만들 이유가 없다.
   * 사용자가 직접 만든 오브젝트(예: selectorless Endpoints)에는 이 표식이 없으므로
   * 소유권이 정상 부여된다.
   */
  public static final List<String> SYSTEM_DERIVED_LABEL_KEYS = List.of(
      "endpoints.kubernetes.io/managed-by");

  /**
   * 소유권 추적 대상 네이티브 네임스페이스 리소스 16종 (고정 목록).
   * 소유자 레이블(aipub.ten1010.io/username)이 있는 오브젝트만 추적되며,
   * 레이블은 낙인 웹훅(mutating-webhook-user-v2.yaml 의 rules)이 CREATE 를 인터셉트해
   * 찍는다 — 멤버가 만든 오브젝트가 레이블 없이 남으면 본인도 수정/삭제할 수 없게
   * 되기 때문이다. 목록을 바꿀 때는 웹훅 rules(이 저장소 + aipub-installer helm 차트)도
   * 함께 갱신할 것.
   * 예외: events 는 시스템 컴포넌트가 고빈도로 생성하므로 웹훅이 인터셉트하지 않는다
   * — 수동으로 레이블이 부여된 경우에만 소유로 취급된다. 시스템 컴포넌트가 만드는
   * 오브젝트는 비멤버 생성이라 레이블 없이 통과되며 소유권 추적 대상이 아니다.
   */
  public static final List<ResourceTarget> OWNED_TARGETS = List.of(
      new ResourceTarget("", "v1", "pods"),
      new ResourceTarget("", "v1", "configmaps"),
      new ResourceTarget("", "v1", "secrets"),
      new ResourceTarget("", "v1", "services"),
      new ResourceTarget("", "v1", "endpoints"),
      new ResourceTarget("", "v1", "serviceaccounts"),
      new ResourceTarget("", "v1", "events"),
      new ResourceTarget("", "v1", "persistentvolumeclaims"),
      new ResourceTarget("", "v1", "limitranges"),
      new ResourceTarget("apps", "v1", "deployments"),
      new ResourceTarget("apps", "v1", "replicasets"),
      new ResourceTarget("apps", "v1", "statefulsets"),
      new ResourceTarget("apps", "v1", "daemonsets"),
      new ResourceTarget("autoscaling", "v2", "horizontalpodautoscalers"),
      new ResourceTarget("policy", "v1", "poddisruptionbudgets"),
      new ResourceTarget("networking.k8s.io", "v1", "ingresses"));

  private OwnershipPolicy() {
  }

}
