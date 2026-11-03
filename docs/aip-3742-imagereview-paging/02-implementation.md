# 02. 구현 — ImageReview 의 스캔 요약 제외와 목록 페이지 순회

## 변경 파일

| 파일 | 변경 |
|---|---|
| `src/main/java/.../domain/aipubbackend/dto/ArtifactListOptions.java` | `@Nullable Boolean withScanOverview` 필드 |
| `src/main/java/.../domain/aipubbackend/impl/ListOptionsUtils.java` | `applyArtifactListOptions` 가 non-null 이면 `withScanOverview` query param 을 싣는다 |
| `src/main/java/.../domain/aipubbackend/PagingUtils.java` | 신규 — `PAGE_SIZE`·`MAX_PAGES`·`PageFetcher`·`listAllPages` |
| `src/main/java/.../mutating/service/ImageReviewReviewHandler.java` | `doService` 가 두 목록을 순회로 받는다. `repositoryListOptions`·`artifactListOptions` 정적 헬퍼 |
| `src/test/java/.../domain/aipubbackend/PagingUtilsTest.java` | 신규 5건 |
| `src/test/java/.../domain/aipubbackend/impl/ArtifactServiceImplTest.java` | 신규 2건 — HTTP 계약 |

CRD 스펙 변경 없음 → `kubernetes/examples/*.yaml` 갱신 불필요.

## 흐름

```
FE ── CREATE ImageReview ──▶ apiserver ── mutating webhook (timeout 10s, Fail) ──▶ ImageReviewReviewHandler.doService
                                                                                     │
       spec.repo 없음                                                                │ spec.repo 있음
       PagingUtils.listAllPages("repositories of hub {id}")                          │ PagingUtils.listAllPages("artifacts of {id}/{repo}")
         pageOffset 0,1,2… × pageSize 100                                            │   pageOffset 0,1,2… × pageSize 100, withScanOverview=false
         GET /imagehubs/{id}/repositories?pageOffset=&pageSize=                      │   GET /imagehubs/{id}/repositories/{repo}/artifacts?pageOffset=&pageSize=&withScanOverview=false
                                                                                     ▼
                                       받은 건수 < 100 이면 멈춤 · 10페이지(1,000건)에 닿으면 WARN 후 멈춤
                                                                                     ▼
                                       status.repositories / status.artifacts(digest·tags) 를 JSON patch 로 채움
```

`pageOffset` 은 **0부터 세는 페이지 번호**다(행 오프셋이 아니다). 백엔드가 `page = pageOffset + 1` 로 Harbor 에 보낸다. aipub-web 이 행 오프셋을 넘겨 101번째부터 잃은 결함(AIP-3733)과 같은 함정이라 `PageFetcher` javadoc 과 테스트로 고정했다.

## 로그

상한에 닿으면:

```
WARN  i.t.a.p.domain.aipubbackend.PagingUtils : Stopped listing artifacts of 10/team-a/train at the page cap 10
```

## 설정

없다. `PAGE_SIZE`·`MAX_PAGES` 는 상수다.
