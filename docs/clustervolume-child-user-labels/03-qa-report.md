# QA 보고서 — ClusterVolume 자식(PVC/PV)에 소유자 라벨 전파

브랜치 `feat/clustervolume-child-user-labels` (base `upstream/develop` @ `87011f8`). 독립 QA(qa-validator) 1회 + 권고 반영 후 재검증.

## 빌드/테스트 (권고 반영 후 최종)

| 항목 | 결과 |
|---|---|
| `./gradlew build` (컴파일 + 전체 테스트) | PASS, exit 0 |
| 루트 프로젝트 테스트 | 202건 / 실패 0 / 에러 0 (`build/test-results/test/TEST-*.xml` 케이스별 확인) |
| `UserLabelReviewHandlerTest` | 33건 (기존 21 + 신규 12) 전부 통과 |
| `UserOwnerReviewHandlerTest` | 19건 (기존 18 + 신규 1) 전부 통과 |
| 웹훅 yaml | `kubectl create --dry-run=client`(caBundle 더미 치환) 파싱 OK; QA 가 `--dry-run=server`(docker-desktop) 로 admissionregistration.k8s.io/v1 스키마 검증 PASS |

## 컨벤션 체크리스트
- [x] 2-space 인덴트, 탭/트레일링 공백 없음
- [x] import ASCII 순서 (`ProjectApiConstants`, `java.util.regex.Pattern` 삽입 위치 포함)
- [x] `@Nullable` 은 `org.jspecify.annotations.Nullable` 만
- [x] 한글 주석 스타일 인접 코드와 일치(설계 이유 서술)
- [x] 라벨 상수는 `LabelConstants` 에 `ProjectApiConstants` 조합으로 정의
- [x] JSON Patch 는 `common-jsonpatch` 빌더 사용
- [x] GVK 판별은 `V1AdmissionReviewUtils` 정적 메서드(기존 `isNamespaceRequest`/`isClusterVolumeRequest` 형태)

## 웹훅 안전성 — 기존 경로 회귀 분석

| 경로 | before → after | 판정 |
|---|---|---|
| allowlist ns 안의 namespaced 리소스 | 조회 없이 allow → 동일 | 변경 없음 |
| 멤버 생성(namespaced/Namespace/CV) | 본인 라벨 → 동일. owner 라벨이 있어도 CV 조회 없음(`verify(never())`) | 변경 없음 |
| 멤버인데 AipubUser 없음 | 400 → 동일 | 변경 없음 |
| 비멤버 + Namespace/CV | 무변경 allow → `resolveOwnerLabels` 의 cluster-scoped 분기에서 null → 동일 | 변경 없음 |
| 비멤버 + namespaced, owner 라벨 없음 | `getLabelsFromOwner` → 동일 메서드(라벨 추출만 `extractUserLabels` 로 분리, 로직 동일) | 변경 없음 |
| 비멤버 + owner 조회 non-404 오류 | 500 → 동일(`fetchObjectByPath` 가 기존 본문 그대로) | 변경 없음 |
| **신규** 비멤버 + PVC/PV + owner 라벨 | — → CV GET: 200 라벨 전파 / 404·라벨 없음 무변경 / 그 외 500 | 신규, 기존 owner 조회와 같은 계약 |
| **신규** 비멤버 + PV, owner 라벨 없음 | canHandle false(컨트롤러 allow) → canHandle true, 조회 없이 무변경 allow | 결과 동일 |

- 예외 삼킴 없음, 신규 reject 는 CV GET non-404 실패 한 가지.
- 핸들러 순서 `[UserOwnerReviewHandler, UserLabelReviewHandler]`: PV 는 owner 핸들러 `canHandle` false → label 핸들러만 실행. 패치 병합(`allowMerging`) 정상.
- 웹훅 재호출(`reinvocationPolicy: IfNeeded`) 시 같은 CV 를 다시 읽어 같은 `add` 연산 → 멱등.
- 배포 순서가 어긋나(rule 이 먼저) 구버전 컨트롤러가 PV 요청을 받아도 `canHandle` 이 false 라 allow → 가용성 사고 없음(라벨만 누락).

## RBAC 최소 권한
- 복제 PVC(프로젝트 ns)는 `OwnershipPolicy.OWNED_TARGETS` → CV 소유자 개인 Role 에만 `resourceNames` 단위 update/patch/delete. 프로젝트 Role 불변 → 다른 멤버에게 권한 없음.
- 시스템 ns 의 앵커 PVC: namespaceSelector 제외/allowlist 라 라벨이 붙지 않고, 붙더라도 `AipubUserRoleReconciler` 가 allowlist·Project 없는 ns 를 스킵 → Role 없음.
- PV 는 OWNED_TARGETS 아님, `OwnershipPolicy`/`ReconciliationService` 변경 없음.
- PV 에 AipubUser ownerReference 미부착 — `canHandle_createPersistentVolume_returnsFalse` 로 고정(부착 시 사용자 삭제 → PV GC).

## 경계면 교차 비교
| 경계면 | 결과 |
|---|---|
| 웹훅 objectSelector 키 ↔ `LabelConstants.CLUSTER_VOLUME_OWNER_KEY` | 동일 문자열 `clustervolumes.aipub.ten1010.io/owner` |
| 웹훅 rule GVK ↔ `isPersistentVolumeRequest` | core `""` / `PersistentVolume` / CREATE / Cluster |
| CV GET 경로 ↔ `ProjectApiConstants` | `/apis/aipub.ten1010.io/v1alpha1/clustervolumes/{name}` (테스트 `CV_GET_PATH` 와 일치) |
| CRD ↔ DTO/리컨실러 | 해당 없음(metadata 만 읽는 웹훅 경로, DTO 미추가) |

## 테스트 품질
- `ApiClient.buildCall` 을 경로별 `thenAnswer` + okhttp `Response` 직접 생성으로 스텁 → `fetchObjectByPath` 의 404/500/body 파싱이 실제 실행됨. `getBasePath()` 스텁이 없으면 `anyString()` 매처가 null 에 안 걸려 스텁이 무시된다(시행착오 — 5건 실패 후 발견).
- 패치는 Base64 디코드 후 `/metadata/labels/aipub.ten1010.io~1username`·`~1userid` 값까지 검증.
- GET 경로는 자식 이름(`cv-test`)과 다른 라벨 값(`other-cv`)으로 구분해 "라벨 값으로 조회" 를 고정.
- 조회 없음은 `verify(never()).buildCall(...)` 로 검증(멤버 우선, owner 라벨 없는 PV, allowlist ns, 비PVC kind, 비DNS 라벨 값).

## 발견 및 반영된 이슈

| 심각도 | 이슈 | 반영 |
|---|---|---|
| 중간 | CV owner 라벨 경로가 kind 를 가리지 않아, 워크로드 템플릿 라벨을 상속한 RS/Pod 등에 이 라벨이 있으면 controller ownerReference 전파 대신 CV 조회로 바뀌어 소유권이 CV 소유자로 오귀속될 수 있음 | **반영** — `isClusterVolumeChildCandidate(request)`(core PVC 또는 PV) 일 때만 CV 경로. `isPersistentVolumeClaimRequest` 유틸 추가. 회귀 테스트 `handle_nonPvcKindWithOwnerLabel_skipsClusterVolumeLookup` |
| 낮음 | 라벨 값이 검증 없이 URL path 에 들어감(mutating admission 은 스키마 검증 전) | **반영** — `getClusterVolumeOwnerName` 에서 DNS-1123 label 정규식(`^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$`, CRD 가 CV 이름에 강제하는 형식) 불일치 시 null. 테스트 `handle_ownerLabelNotDnsLabel_skipsClusterVolumeLookup` |
| 정보 | 테스트 Javadoc 한 줄 102자 | 반영(축약) |
| 정보 | `aipub-installer` helm 차트에 PV 웹훅 엔트리 필요 | 별도 PR — `04-pr.md` 후속 항목에 기재 |

## 종합 판정
**PASS** — 권고 2건 반영 후 재빌드 통과(202/0/0). 미해결 이슈 없음. 남은 것은 저장소 밖의 후속(installer 차트 PR, 개발 클러스터 실측).

---

# 2차 QA — transfer 대응 리컨실러

독립 QA(qa-validator) 1회 + 권고 반영 후 재검증.

## 빌드/테스트
| 항목 | 결과 |
|---|---|
| `./gradlew clean build` (QA) | PASS — 전 모듈 251 / 실패 0 |
| `./gradlew build` (권고 반영 후, 리더) | PASS — 루트 221 / 실패 0 |
| `ClusterVolumeChildLabelReconcilerTest` | 11건 통과 |
| `ClusterVolumeChildLabelWatchTest` | 8건 통과 (QA 후 2건 추가: 소유자 라벨 제거 update, PV 워치) |

## 체크리스트
- [x] `AbstractReconciler` 상속, `*ControllerFactory` 배선, `ControllerConfiguration` 빈 — 인접 리컨실러와 구조 일치
- [x] 컨벤션(2-space, import 순서, jspecify `@Nullable`, 한글 주석)
- [x] **멱등성**: diff 가 비면 무패치. 패치 → 자식 update → `labelsFilter` 로 재큐잉 → 2회차 diff 비어 수렴. CV status/resync 는 `ownerLabelsFilter` 가 차단
- [x] **인포머 배선**: 라벨 셀렉터가 걸린 `V1PersistentVolumeClaim`/`V1PersistentVolume` 인포머의 다른 사용처 없음(main/test grep). `OwnedObjectInformerManager` 는 별도 팩토리 + `V1PartialObject` 라 충돌 없음. CV 인포머 cluster-scoped. watch 3종 모두 등록·readyFunc 포함
- [x] **패치 안전성**: merge patch 본문은 `metadata.labels` 의 명시 키만(null = 제거). `PatchUtils.patch(…, PATCH_FORMAT_JSON_MERGE_PATCH, apiClient)` 는 공유 `ApiClient` 상태를 바꾸지 않음(`StatusPatchHelper` 와 동일 패턴)
- [x] **RBAC**: transfer 시 리컨실러 패치 → `OwnedObjectInformerManager.onUpdate`(username 변경) → 구·신 소유자 Role 재큐잉으로 권한 회수/부여. 라벨 제거는 셀렉터 이탈(DELETED) → 구 소유자 재큐. PV 는 OWNED_TARGETS 아님
- [x] **예외**: 패치 404 무시, 그 외 전파 → `AbstractReconciler` 재큐잉(409 5초/그 외 60초). 삼킴 없음
- [x] 테스트: `Cache` 인덱스 실제 사용, 가짜 패처가 호출 인자(kind/ns/name/labels) 기록, 결과 `Result.isRequeue()` 검증
- [x] 문서(01/02/04) 2차 내용이 코드와 일치

## 발견 및 반영된 이슈
| 심각도 | 이슈 | 반영 |
|---|---|---|
| 낮음 | `ClusterVolumeChildLabelControllerFactory` 에 Namespace 인포머 readyFunc 누락 — `NamespaceAllowlistResolver` 가 캐시 미존재를 "비 allowlist" 로 보므로 기동 초기 소급 리컨실이 allowlist ns 의 PVC 에 라벨을 붙일 수 있음 | **반영** — `V1Namespace` readyFunc 추가(주석 포함) |
| 낮음 | 스킵 규칙 불일치: 웹훅은 namespaceSelector 이름 제외 목록 + allowlist, 리컨실러는 allowlist 라벨만 → 이름 제외 목록에만 있는 시스템 ns(예: `aipub`)의 앵커 PVC 는 리컨실러만 라벨. Project 없는 ns 라 Role 은 생기지 않음 | **정책 확정(코드 무변경)** — 코드 수준 정책은 allowlist 라벨 하나이며 이름 목록은 웹훅 설정에만 존재. 리컨실러가 이름 목록을 복제하면 두 곳 동기화 부담이 생긴다. RBAC 영향이 없으므로 라벨이 붙는 쪽을 허용하고 문서(01-plan 알려진 한계)에 기재 |
| 정보 | CV 컨트롤러가 자식 labels 를 통째로 덮어쓰면 핑퐁 가능 | 코드로 검증 불가 — 개발 클러스터 실측 항목(04-pr) |
| 정보 | 선택 테스트 누락(라벨 제거 필터, PV 워치, 패처 본문) | 필터·PV 워치 2건 **추가**. 패처 본문 테스트는 `PatchUtils` 목킹 비용 대비 가치가 낮아 생략(merge patch 구성은 단순 JSON) |
| 정보 | 100자 초과 줄(한글 Javadoc) — 기존 코드와 동일 관례 | 무변경 |

## 종합 판정
**PASS** — 권고 반영 후 재빌드 통과(221/0/0). 미해결 이슈 없음. 남은 것은 저장소 밖 후속(installer 차트 PR, 개발 클러스터 실측: 라벨 전파·transfer 추종·CV 컨트롤러와 핑퐁 여부).
