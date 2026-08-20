# 계획 — userrelationship 웹훅의 cluster-scoped Namespace 확장

## 개요
워크로드 생성 시 요청자 토큰에서 추출한 사용자 정보를 `aipub.ten1010.io/username`/`aipub.ten1010.io/userid` 라벨로 주입하는 기존 메커니즘(userrelationship 뮤테이팅 웹훅)을 cluster-scoped 리소스인 **Namespace CREATE**까지 확장한다.

## 배경
- 기존 라벨 주입은 `UserLabelReviewHandler`가 담당하며, 웹훅 rules(`java-webhook-configuration.yaml`)와 `canHandle` 모두 namespaced 리소스 전제였다.
- Namespace에도 생성자 정보를 남겨야 한다는 요구가 대두되었다(향후 다른 cluster-scoped 리소스 확장 가능성 포함).

## 요구사항 (Jira: AIP-2739)
1. Namespace CREATE 시 username/userid 라벨 주입.
2. allowlist(`project.ten1010.io/allowlisted: "true"`) 네임스페이스여도 **라벨은 주입**하고 allow 동작은 동일. (allowlist 스킵은 "allowlist 네임스페이스 안의 리소스"에 대한 규칙이지 네임스페이스 오브젝트 자체의 규칙이 아님)
3. `UserOwnerReviewHandler`의 AipubUser ownerReference 삽입은 Namespace에 적용하지 않음.

> 참고: "admin 아닌 member의 라벨 UPDATE 차단" 가드(`UserLabelGuardReviewHandler`)를 구현했다가 제거했다 — member/admin 권한 관리가 k8s RBAC로 이루어지고 member에게 namespace update 권한이 없어 웹훅 가드는 중복 방어라는 리뷰 결론.

## 설계 결정 (검토한 대안 포함)
- **채택: userrelationship 웹훅 확장** — 라벨 결정 로직(UserInfoAnalyzer, AipubUser 조회, allowlist 처리)의 단일 원천이 이미 `UserLabelReviewHandler`에 있음.
- 대안(기각): 레거시 웹훅(`/api/v1/admissionreviews`)의 `NamespaceReviewHandler`에 라벨 주입 추가 — 라벨 로직이 두 곳으로 갈라지고, 해당 웹훅은 `failurePolicy: Ignore`라 누락이 조용히 발생하며, 핸들러의 "가드 전용" 책임과 어긋남.
- Namespace 판별은 `request.namespace` 유무가 아니라 **kind 기반**(`""/v1/Namespace`)으로 한다. Namespace CREATE의 AdmissionReview는 `request.namespace`가 오브젝트 자신의 이름으로 채워지므로 namespace 필드로는 cluster-scoped를 구분할 수 없다. 이 사실 때문에 `UserOwnerReviewHandler`도 명시적 제외가 필요했다(제외하지 않으면 ownerReference가 붙고, AipubUser 삭제 시 네임스페이스가 GC로 함께 삭제되는 위험).
- ownerReference 제외는 `aipub.add-owner-except-gvk-list` 프로퍼티가 아니라 **코드 레벨**로 — 배포 설정 누락 위험 제거.

## 변경 범위
- `kubernetes/controller/project-controller/templates/java-webhook-configuration.yaml`
- `mutating/V1AdmissionReviewUtils.java`, `mutating/service/UserLabelReviewHandler.java`, `mutating/service/UserOwnerReviewHandler.java`
- 테스트 2개 파일
