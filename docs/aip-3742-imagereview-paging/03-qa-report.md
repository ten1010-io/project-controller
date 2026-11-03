# 03. QA 보고서 — ImageReview 의 스캔 요약 제외와 목록 페이지 순회

2026-11-03 · 종합 판정 **PASS** · 하네스 degraded 모드(TeamCreate 없음, 리더가 직접 분석·구현·검증)

## 빌드·테스트

| 명령 | 결과 |
|---|---|
| `./gradlew compileJava compileTestJava test` | **BUILD SUCCESSFUL** |
| 전체 테스트 | 334건 · 실패 0 · 오류 0 · 건너뜀 0 |

신규 7건:

| 테스트 | 검증 |
|---|---|
| `PagingUtilsTest.lastPageShorterThanPageSize_stopsWithoutExtraCall` | 150건 → 2페이지만 요청, 중복 없음 |
| `PagingUtilsTest.emptyFirstPage_returnsEmpty` | 0건 → 1번 요청 후 빈 목록 |
| `PagingUtilsTest.exactMultipleOfPageSize_stopsAtFollowingEmptyPage` | 200건 → 0·1·2 페이지(마지막 빈 페이지에서 멈춤) |
| `PagingUtilsTest.requestsZeroBasedPageIndexWithHarborMaxPageSize` | pageOffset 이 0·1·2(행 오프셋 아님), pageSize 100 |
| `PagingUtilsTest.alwaysFullPages_stopsAtPageCap` | 끝없이 가득 찬 페이지 → `MAX_PAGES` 에서 멈춤, 1,000건 |
| `ArtifactServiceImplTest.withScanOverviewFalse_sendsItWithPageOptions` | 경로 `/api/v1alpha1/imagehubs/10/repositories/nginx/artifacts`, `withScanOverview=false`·`pageOffset`·`pageSize` 실림 |
| `ArtifactServiceImplTest.withScanOverviewUnset_omitsIt` | null 이면 param 자체가 없음 |

`ImageReviewReviewHandler` 자체는 단위 테스트하지 않았다 — `SharedInformerFactory` 를 세워야 해서 규칙대로 순회 로직을 `PagingUtils` 로 떼어 테스트했다. 핸들러의 실제 동작은 아래 클러스터 확인 항목으로 남긴다.

## 컨벤션

| 항목 | 결과 |
|---|---|
| 주석 한국어·짧게, Jira 키 없음 | ✅ |
| 로그 메시지 영어 | ✅ `Stopped listing {} at the page cap {}` |
| `this.` 필드 접근, 2칸 들여쓰기, 임포트 알파벳 순 | ✅ |
| nullable 은 `org.jspecify.annotations.Nullable` | ✅ |
| 정적 헬퍼는 `abstract class XxxUtils` | ✅ `PagingUtils` |
| 테스트 이름 `<상황>_<기대>` | ✅ |
| 최소 diff | ✅ |

## 안전성

- **웹훅** — 인터셉트 대상·rules 변경 없음. 바뀐 것은 핸들러가 백엔드를 부르는 횟수다. 최악은 페이지 10번 직렬 호출이고, 각 호출 callTimeout 은 5초라 백엔드가 매우 느리면 웹훅 timeout(10초)을 넘을 수 있다. 이건 단건 호출이던 이전에도 같은 성격의 위험이었고(5초 1회), 정상 응답에서는 페이지 하나가 짧아 여유가 있다. 상한을 둬서 "허브가 커질수록 반드시 실패" 하는 경우는 막았다.
- **멱등성** — 리컨실러가 아니라 CREATE 시점 웹훅이라 해당 없음.
- **RBAC** — 변경 없음.
- **실패 처리** — 중간 페이지에서 예외가 나면 이전과 같이 예외가 그대로 올라간다(부분 목록을 내려보내지 않는다).

## 발견·수정한 이슈

| # | 내용 | 처리 |
|---|---|---|
| 1 | AIP-3732 본문의 숫자(10개)와 "리포지토리 목록은 전부 나온다" 가 틀렸다 — REST 경로에서 둘 다 50개에서 잘린다 | Jira 본문·제목 정정(2026-11-03) |

## 클러스터 확인 (사람)

- [ ] 아티팩트 51개 이상인 리포지토리에서 이미지 선택 모달에 전부 나온다
- [ ] 리포지토리 51개 이상인 허브에서 리포지토리 목록이 전부 나온다
- [ ] backend `feat/AIP-3742` 배포 후 gateway 접근 로그에 `withScanOverview=false` 가 보이고, ImageReview 응답이 이전과 같다
