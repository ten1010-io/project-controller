# 계획 — PR #87(AIP-2389 allowlist) 리뷰 후속 수정

## 개요

PR #87(네임스페이스 allowlist + 런타임 toleration 주입) 코드 리뷰에서 도출된 4개 항목을 수정한다.

## 배경

리뷰에서 확인된 핵심 문제: `ProjectReviewHandler`의 reserved/allowlist 이름 hard block이 admission operation을 구분하지 않는다. projects 웹훅은 `CREATE, UPDATE`를 가로채고 `failurePolicy: Fail`이며, Project는 finalizer를 사용하므로 삭제가 "deletionTimestamp 설정 → 컨트롤러 정리 → finalizer 제거 **UPDATE**"로 진행된다. hard block이 UPDATE까지 거부하면:

- 과거 system-admin escape hatch로 생성된 legacy reserved-name Project → finalizer 제거 불가 → 영구 terminating
- 웹훅 우회로 생긴 "동명 Project + allowlist 공존" backstop 상태 → 해소 경로(Project 삭제)가 스스로 막히는 교착

## 설계 결정

- **hard block을 CREATE에만 적용** (채택). 대안으로 "deletionTimestamp 있는 객체의 UPDATE만 통과"도 검토했으나, hard block의 목적이 "그런 이름의 project가 새로 생기는 것 방지"이므로 CREATE 한정이 의미상 정확하고 단순하다. UPDATE는 기존 group 기반 검사(system admin / project-manager)로 흐른다.
- **NamespaceReviewHandler(Project→allowlist 라벨 가드)는 유지**. 라벨 부착은 신규 의미 부여 행위라 UPDATE 거부가 교착을 만들지 않는다(라벨 없는 UPDATE는 통과).

## 변경 범위

1. `ProjectReviewHandler` hard block CREATE 한정
2. 임시 분석 문서 `aip-2389-allowlist-scheduling.html` 삭제
3. minor 정리: `NamespaceAllowlistValueEnum` dead code 제거, `PodReconciler` resolver 주입 통일, `WorkloadControllerFactory` 인라인 팩토리 제거, `ClusterRole(Binding)Reconciler`의 `NamespaceNameResolver` 필드화
4. 테스트 보강: allowlist-우선 순서(웹훅 2종 + 워크로드 리컨실러), UPDATE 통과 회귀
