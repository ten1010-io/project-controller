# QA 보고서 — PR #87(AIP-2389 allowlist) 리뷰 후속 수정

## 판정: PASS

## 빌드/테스트

- `./gradlew test` — BUILD SUCCESSFUL, 27개 테스트 클래스 144개 테스트, 실패 0
- 신규/확장 테스트 4클래스 실행 확인 (`PodReviewHandlerTest`, `DeploymentReviewHandlerTest`, `WorkloadControllerReconcilerTest`, `ProjectReviewHandlerTest`)

## 웹훅 안전성

- [x] projects 웹훅(`CREATE, UPDATE`, failurePolicy Fail)에서 UPDATE가 hard block을 건너뛰고 기존 group 기반 검사로 흐름 → finalizer 제거 UPDATE 통과 가능
- [x] reserved/allowlist 이름의 신규 CREATE는 여전히 예외 없이 409 거부 (회귀 테스트로 고정)
- [x] UPDATE 경로의 기존 가드(project-manager 권한, spec quota/binding 변경 거부) 무변경
- [x] `NamespaceReviewHandler`의 Project→allowlist 라벨 가드는 의도적으로 유지 — 라벨 부착만 막으므로 교착 없음

## 멱등성/동작 보존

- [x] toleration reconcile 로직 무변경 (호출 경로 정리만)
- [x] allowlist 판정 동작 동일 — 상수 치환, equalsIgnoreCase 유지 (기존 `NamespaceAllowlistResolverTest` 대소문자 케이스 통과)
- [x] PodReconciler/WorkloadControllerFactory 변경은 의존성 획득 방식만 — informer 조회 시점 동일

## 컨벤션

- [x] 생성자 주입/필드화가 기존 패턴(ReconciliationService 주입)과 일치
- [x] 테스트 스타일 기존 준수 (Cache 기반 resolver, mock informer, V1AdmissionReview 직접 구성)

## 발견 및 수정된 이슈

- `WorkloadControllerReconcilerTest` 초안에 중복 import(`SubjectResolver`) 1건 → 즉시 수정 후 통과
