# 01. 계획 — ImageReview 의 스캔 요약 제외와 목록 페이지 순회 (AIP-3742 · AIP-3732)

브랜치 `feat/AIP-3742` (develop `8cc75a8` 기준) · 2026-11-03 · fixVersion 6.1.0

## 배경

이미지 선택 모달은 `ImageReview` CR 을 CREATE 하고, mutating 웹훅(`ImageReviewReviewHandler`)이 aipub-backend 에서 목록을 받아 `status` 에 채운다.

- `spec.repo` 없음 → 허브의 리포지토리 목록 (`GET /api/v1alpha1/imagehubs/{hub}/repositories`)
- `spec.repo` 있음 → 리포지토리의 아티팩트 digest·tag 목록 (`GET /api/v1alpha1/imagehubs/{hub}/repositories/{repo}/artifacts`)

두 가지 문제가 있었다.

1. **스캔 요약 비용 (AIP-3742)** — 아티팩트 목록 API 는 Harbor 에 `with_scan_overview=true` 를 붙여, Harbor 가 아티팩트마다 취약점 레코드 전체를 읽어 요약을 다시 계산한다. ImageReview 는 digest·tag 만 쓰는데 이 비용을 그대로 냈다. 10월 cluster10 부하테스트에서 harbor-core·harbor-db 가 가장 무거웠고, 수용량 한계(초당 29~37건)도 ImageReview 경로였다(BL-80).
2. **목록 잘림 (AIP-3732)** — 핸들러가 pageOffset·pageSize 없이 한 번만 부른다. gateway `V1alpha1ListOptions.getPageSize()` 가 생략된 값을 50 으로 채우므로 **리포지토리·아티팩트 모두 50개에서 잘린다**. (티켓 처음 본문의 "10개", "리포지토리 목록은 전부 나온다" 는 틀렸고 2026-11-03 정정했다.)

backend 쪽 `withScanOverview` param 은 aipub-backend `feat/AIP-3742`(`00784a92`)에서 추가했다. 생략하면 true 다.

## 설계 결정

| # | 결정 | 이유 · 검토한 대안 |
|---|---|---|
| D1 | 아티팩트 조회에 `withScanOverview=false` 를 보낸다 | ImageReview 는 digest·tag 만 쓴다. `ArtifactListOptions.withScanOverview`(nullable) 를 두고 null 이면 보내지 않는다 — 다른 호출자가 생기면 백엔드 기본값을 따른다 |
| D2 | 페이지를 끝까지 순회한다 (`PagingUtils.listAllPages`) | pageSize 100(Harbor 상한, 백엔드가 그 이상은 잘라 보낸다), 받은 건수가 100 미만이면 멈춘다. 대안 "백엔드에 전체 조회 API 추가" 는 백엔드 계약을 하나 더 늘리고 결국 같은 순회를 백엔드가 하게 된다 |
| D3 | 페이지 상한 `MAX_PAGES = 10`(1,000건). 넘으면 WARN 로그 남기고 그때까지 받은 것만 돌려준다 | 웹훅은 `timeoutSeconds: 10`·`failurePolicy: Fail` 이고 페이지마다 백엔드 호출(callTimeout 5초)이 직렬로 붙는다. 상한이 없으면 큰 허브에서 CREATE 자체가 실패한다. 잘린 목록이 실패보다 낫다 |
| D4 | 순회 로직은 순수 함수 `PagingUtils`(domain/aipubbackend)로 빼고, 서비스 인터페이스(`ArtifactService`·`RepositoryService`)는 바꾸지 않는다 | 핸들러의 두 호출부가 같은 순회를 쓴다(규칙: 두 곳 이상이면 Utils). 인포머가 필요한 핸들러는 단위 테스트하기 어려워 로직을 떼어 테스트한다 |
| D5 | 정렬을 지정하지 않는다 | 모달의 표시 순서를 바꾸지 않으려고 Harbor 기본 정렬을 그대로 둔다. 순회 도중 push 가 끼면 항목이 밀릴 수 있지만 한 번의 CREATE 안에서 일어날 일이라 드물다 |

## 변경 범위

| 파일 | 변경 |
|---|---|
| `domain/aipubbackend/dto/ArtifactListOptions.java` | `@Nullable Boolean withScanOverview` |
| `domain/aipubbackend/impl/ListOptionsUtils.java` | `withScanOverview` 가 있으면 query param 으로 싣는다 |
| `domain/aipubbackend/PagingUtils.java` (신규) | `PAGE_SIZE = 100`, `MAX_PAGES = 10`, `listAllPages` |
| `mutating/service/ImageReviewReviewHandler.java` | 두 목록을 `PagingUtils.listAllPages` 로, 아티팩트는 `withScanOverview=false` |
| 테스트 | `PagingUtilsTest`(5), `ArtifactServiceImplTest`(2) |

RBAC·CRD·웹훅 rules 변경 없음.

## 배포 순서

어느 쪽을 먼저 배포해도 깨지지 않는다.

- 새 controller + 옛 backend: gateway 가 모르는 `withScanOverview` 를 무시한다 → 요약 비용은 남지만 순회(AIP-3732)는 효과가 있다.
- 옛 controller + 새 backend: param 을 안 보낸다 → 기존과 같다.
