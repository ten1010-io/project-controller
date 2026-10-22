# 업데이트된 워크로드 CRD 반영 — QA 보고서

- Jira: [AIP-3585](https://ten1010.atlassian.net/browse/AIP-3585)
- 브랜치: `feat/AIP-3585` (base `develop` 3f7823a)
- **종합 판정: PASS**

## 1. 빌드·테스트

| 항목 | 결과 |
|---|---|
| `./gradlew clean compileJava compileTestJava test` | **BUILD SUCCESSFUL** |
| 테스트 | **317 tests / 0 failures / 0 errors / 0 skipped** |
| 그중 이번 작업 | 4건 (`WorkloadDtoDeserializationTest`) |

`--tests` 필터는 `:test` 에 걸어야 한다 — 루트에 걸면 `common-*` 서브프로젝트의 test 태스크가
"No tests found" 로 실패한다(`controller-code-style.md` 의 알려진 함정). 캐시로 `UP-TO-DATE` 가
뜨길래 `clean` 과 `--rerun-tasks` 로 실제 실행을 확인했다.

## 2. 스키마 정합 — 클러스터 대조

`c10-m1` 의 CRD 덤프를 기준으로 프로그램 대조했다.

| 대상 | 결과 |
|---|---|
| `V1beta1WorkspaceSpec` ↔ 클러스터 `spec` 키 집합 | 일치 (`ports·replicas·ssh·suspend·suspendOption·template·tools`) |
| `V1beta1WorkspaceStatus` ↔ 클러스터 `status` 키 집합 | 일치 (`conditions·observedGeneration·ports·readyReplicas·replicas·ssh·suspendCommit·tools`) |
| `V1beta1WorkspacePortExpose` ↔ `spec.ports[].expose` | 일치 (`enabled·type·tcp·http·inference`) |
| `V1alpha1Operation*` ↔ 클러스터 | 일치 (이번 변경 없음 — AIP-2269 에서 반영됨) |
| `examples/crd.yaml` 의 `operations` 스키마 | `template` 축약분 제외 **완전 일치** |
| `examples/crd.yaml` 의 `workspaces` 스키마 | `template` 축약분 제외 **완전 일치** |

## 3. 매니페스트 검증

| 항목 | 결과 |
|---|---|
| `examples/crd.yaml` multi-doc 파싱 | PASS — 8건, 순서·이름 보존 |
| `workspaces` 문서 버전 | `v1` → **`v1beta1`** (클러스터와 일치) |
| 손대지 않은 6개 문서 | diff hunk 가 전부 operations·workspaces 범위 안 — 무변경 |
| `kubectl apply --dry-run=client` | 8건 전부 통과 |
| `kubectl apply --dry-run=server` (kind-local) | 통과 — apiserver 가 스키마를 받아들임 |

## 4. 컨벤션 체크리스트

`.claude/rules/controller-code-style.md` 기준.

| 항목 | 결과 |
|---|---|
| 들여쓰기 2칸, 탭 없음 | PASS |
| 후행 공백 없음 | PASS |
| 추가된 줄 100자 이하 | PASS |
| 주석 한국어, 1줄 위주 | PASS (테스트 머리 주석 2줄) |
| **주석에 Jira 키 없음** | PASS (0건) |
| nullable 은 `org.jspecify.annotations.Nullable` | PASS |
| Lombok `@Data`(DTO) | PASS — 주변 DTO 와 동일 |
| 테스트 네이밍 `<상황>_<기대>` 2단 | PASS |
| 최소 diff | PASS — 예시 CRD 외 재포맷 0 |

**직접 수정한 이슈: 없음.**

## 5. 리컨실 멱등성

이번 변경은 리컨실 경로에 닿지 않는다 — 추가한 필드를 읽는 코드가 0건이고, RBAC 룰·라벨 동기화는
워크로드 CR 에서 이름만 꺼내 쓴다. 기존 멱등성 테스트 전수 통과(317/317)로 회귀 없음을 확인했다.

## 6. 남은 확인 (사람이 클러스터에서)

- [ ] 배포 후 `Workspace` 인포머가 INFERENCE 포트를 가진 CR 을 받아도 에러 없이 동기화되는지
      (Gson 은 모르는 필드를 무시하므로 이전 빌드도 죽지는 않았다 — 조용히 누락됐을 뿐이다)
- [ ] 추론 포트를 가진 Workspace 생성 시 프로젝트 역할별 RBAC `resourceNames` 에 정상 편입되는지
- [ ] `kubectl apply -f kubernetes/examples/crd.yaml` 을 개발 클러스터에서 1회 (구 `v1` Workspace
      예시가 남은 환경이 있다면 교체 영향 확인)
