# QA 보고서 — userrelationship 웹훅의 cluster-scoped Namespace 확장

## 종합 판정: PASS

## 빌드/테스트
- `./gradlew build` 전체 통과 (전 모듈, 기존 테스트 포함 회귀 없음).
- 신규 단위 테스트 6건 통과:
  - `UserLabelReviewHandlerTest`: canHandle Namespace true / 멤버 → 라벨 주입 / **allowlist Namespace여도 라벨 주입** / allowlist 네임스페이스 안의 리소스는 기존대로 무변경(회귀 가드) / 비멤버 Namespace → 무변경 allow
  - `UserOwnerReviewHandlerTest`: Namespace CREATE(request.namespace가 자신 이름) → canHandle false

## 컨벤션
- 기존 핸들러 스타일(한국어 의도 주석, JSON Patch 빌더 패턴, reject 코드 정책) 준수.
- 공용 판별 로직은 `V1AdmissionReviewUtils` 정적 헬퍼로 중복 없이 공유.

## 웹훅 안전성
- failurePolicy Fail 하 예외 경로: `UserInfoAnalyzer` 실패 시 reject 500 + 사유 메시지 — 기존 namespaced 경로와 동일 정책, 신규 예외 경로 없음.
- 시스템 네임스페이스(kube-system 등): namespaceSelector가 Namespace 오브젝트 자신의 라벨에 평가되어 웹훅 미호출 (`kubernetes.io/metadata.name` 라벨은 apiserver가 admission 전에 부여).
- ownerReference 미삽입 보장: AipubUser 삭제 → Namespace GC 삭제 위험 차단 확인.

## 경계면 교차 비교 (yaml ↔ 코드 1:1)
- 신규 rule `""/v1/namespaces CREATE Cluster` ↔ `UserLabelReviewHandler.canHandle`(true) / `UserOwnerReviewHandler.canHandle`(false).
- `AipubAdmissionReviewController`는 canHandle인 핸들러를 전부 순회 적용 — 첫 핸들러가 false여도 라벨 핸들러 적용됨을 코드로 확인.

## 발견 및 수정된 이슈
- (설계 단계 발견) `UserOwnerReviewHandler`가 namespace 필드 유무만 검사해 Namespace CREATE가 그대로 통과되는 문제 → kind 기반 명시 제외로 수정.

## 알려진 한계
- `UserLabelSynchronizer`는 워크로드 8종 전용 — Namespace는 웹훅 누락 시 주기 보정 없음 (범위 외로 문서화).
