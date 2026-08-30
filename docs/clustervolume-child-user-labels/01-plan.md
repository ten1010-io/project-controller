# 계획 — ClusterVolume 자식(PVC/PV)에 소유자 라벨 전파

## 개요
`ClusterVolume`(CV, `aipub.ten1010.io/v1alpha1`, cluster-scoped) 컨트롤러가 CV 하나당 여러 개 만드는 자식 오브젝트 — 대상 네임스페이스별 **복제 PVC/PV**, `volume.storageClassName` 경로의 **앵커 PVC/PV** — 에 부모 CV 의 소유자 라벨 `aipub.ten1010.io/username` / `aipub.ten1010.io/userid` 를 전파한다. #112 로 CV 자신에는 라벨이 붙지만 자식에는 아무 소유자 표식이 없어, GUI 가 "내 볼륨의 PVC" 를 찾을 수 없고 소유자가 자기 복제 PVC 를 관리(update/delete)할 RBAC 도 생기지 않았다.

## 배경 — CV 자식의 구조 (CRD `clustervolumes.aipub.ten1010.io` 기준)
| 자식 | 스코프 | 위치 | 생성자 | 표식 |
|---|---|---|---|---|
| 복제 PVC | namespaced | 각 대상 ns (`spec.namespaces`) | CV 컨트롤러 SA | `clustervolumes.aipub.ten1010.io/owner=<CV명>` + CV ownerReference |
| 복제 PV | cluster | — | CV 컨트롤러 SA (정적 PV, Retain) | 같음 |
| 앵커 PVC | namespaced | 시스템 ns | CV 컨트롤러 SA | 같음 |
| 앵커 PV | cluster | — | **CSI 프로비저너** (동적) | 컨트롤러가 사후 라벨링 |
| 편입 원본 PVC (`claimRef`) | namespaced | 원본 ns | 사용자/타 주체 | `claimed-by` 라벨 — **남의 오브젝트, 전파 대상 아님** |

- CR 이름은 DNS label(63자) 로 제한되어 자식 이름·라벨 값으로 쓰인다 → `owner` 라벨 값 = CV `metadata.name`.
- 자식의 CV ownerReference 는 GC 백스톱이지만, CV 가 cluster-scoped 라 기존 `UserLabelReviewHandler.getLabelsFromOwner`(namespaced 부모만 조회) 경로로는 닿지 않는다.
- CV `create` 는 백엔드 SA 전용이지만 admin 은 cluster-admin 바인딩이라 직접 만들 수 있다. CV 의 소유자 라벨은 웹훅(멤버 생성 시 — #112, **admin 생성 시 — 이번 추가**) 또는 백엔드가 직접 찍는다. 자식 전파는 "CV 에 라벨이 있으면 복사" 만 책임진다.

## 설계 결정 (검토한 대안 포함)

### 최종 구조 — 책임 분리
```
CV 자체        → 웹훅이 생성자(member/admin) 라벨 (즉시)      ← 소유자 기록의 정본
CV 자식 PVC/PV → ClusterVolumeChildLabelSynchronizer (≤60초)  ← 유일한 전파 경로
직접 만든 PVC  → 웹훅이 생성자 라벨 (기존 그대로)
```
- 웹훅(userrelationship)은 원래 책임인 "생성자 신원 기록" 만 갖는다. CV 자식은 생성자가 CV 컨트롤러 SA(비멤버)라 웹훅의 신원 기록 대상이 아니며, "부모로부터의 전파" 는 전부 주기 동기화가 담당한다.
- 이력: 1차 구현에서는 웹훅 비멤버 경로에 "owner 라벨 → CV GET → 라벨 복사"(PVC 즉시 라벨)를 넣었으나 제거했다. PVC 라벨의 소비자(개인 Role 리컨실러·GUI)는 어차피 비동기라 즉시성의 실익이 없고, 복제 PVC CREATE 마다 어드미션 경로에 CV GET(fail-closed) 이 얹히는 결합이 비용이었다.

### 채택(2차): 주기 스케줄 동기화(ClusterVolumeChildLabelSynchronizer) — transfer 대응
- "소유권 이전(transfer)에도 대응해야 한다" 는 요구에 대해, 기존 `UserLabelSynchronizer` 와 같은 주기 전량 스캔 방식을 채택했다(60초). 매 주기: CV 전량 LIST → owner 라벨(`clustervolumes.aipub.ten1010.io/owner`) 셀렉터로 자식 PVC/PV LIST → "자식의 username/userid = 부모 CV 의 username/userid" 로 수렴(추가·교체·제거, `metadata.labels` 만 merge patch).
- 주기 전량 스캔이라 이벤트 유실·기동 타이밍과 무관하게 한 주기 안에 수렴이 보장되고, 기존 자식 소급·CSI 앵커 PV 사후 라벨링도 자연히 커버된다.
- 실패 처리: CV LIST 실패 → 그 주기 전체 스킵(부분 정보로 라벨을 제거하지 않는다), 자식 LIST 실패 → 해당 타깃만 스킵, 패치 실패 → 로그 후 다음 주기 재시도.
- 스킵: allowlist ns 의 PVC(웹훅·개인 Role 리컨실러와 같은 규칙), 부모 CV 없음(삭제 중·stale — finalizer/GC 가 자식 정리), terminating 자식.
- 대안(1차 구현 후 교체): 인포머+워크큐 리컨실러 — 반응은 초 단위지만 CV/PVC/PV 인포머 3개와 컨트롤러 배선이 필요하고, 라벨 셀렉터로 제한된 typed PVC/PV 인포머가 공유 팩토리에 남는 재사용 함정이 있었다. transfer 는 드물고 라벨은 60초 지연이 무해해 단순한 스케줄 방식으로 교체했다(구현 스냅샷 `_workspace/approach1-*`).
- 대안(기각): CV 컨트롤러 저장소에서 처리 — 저장소가 로컬에 없다.

### CV 자식은 웹훅을 타지 않는다 — 이 기능의 웹훅 설정 변경 0건
- PV 는 웹훅 rule 자체가 없고(1차의 별도 엔트리는 제거), PVC 는 기존 rule 로 웹훅에 들어오지만 비멤버(CV 컨트롤러 SA) 생성이라 무변경 통과된다 — 라벨은 동기화가 붙인다. PV/PVC 라벨의 60초 지연은 무해하고, fail-closed 웹훅 표면 증가와 aipub-installer 차트 후속 PR 이 없다.
- AipubUser ownerReference 는 CV 자식에 붙지 않는다: 비멤버 생성이라 핸들러가 통과시킨다 — 사용자 삭제가 CV 자식의 GC 로 이어질 경로가 없다.

## RBAC 영향 분석
- PVC 는 `OwnershipPolicy.OWNED_TARGETS` → 라벨이 붙은 복제 PVC 는 `AipubUserRoleReconciler` 가 소유자 개인 Role 에 `resourceNames` 단위 update/patch/delete 를 만든다. CV 소유자가 자기 볼륨의 PVC 를 관리하는 것으로 의도에 맞고, 복제 PVC 삭제는 컨트롤러 자가치유로 재생성되어 데이터 안전(CRD 계약).
- `AipubUserRoleReconciler` 는 allowlist ns·Project 없는 ns 를 스킵 → 시스템 ns 의 앵커 PVC 에는 라벨이 붙어도 Role 이 생기지 않는다(동기화도 allowlist ns 의 PVC 는 스킵).
- PV 는 OWNED_TARGETS 가 아니다 → 라벨은 조회/표시용, 권한 부여 없음. 프로젝트 Role 의 PV `get/list` 는 그대로.

## 알려진 한계
- 라벨 수렴은 최대 60초 지연된다. transfer·생성 직후 최대 60초간 구 소유자의 복제 PVC update/delete 권한이 남을 수 있다 — 직전까지 소유자였던 사용자이고, 삭제는 자가치유로 복구되므로 수용.
- allowlist 네임스페이스 안의 PVC 는 웹훅·동기화 모두 라벨을 붙이지 않는다(기존 정책 유지).
- CV 컨트롤러가 자식 update 시 `metadata.labels` 전체를 자기 것으로 덮어쓰는 구현이라면 동기화와 라벨 핑퐁(주기당 1회)이 생길 수 있다 — 개발 클러스터 실측 항목.
- 웹훅 설정 변경이 없으므로 `aipub-installer` 후속 PR 도 필요 없다.

## 변경 범위
- 웹훅: `mutating/service/UserLabelReviewHandler.java` — **admin CV 라벨만**(자식 관련 변경 없음, yaml 변경 없음)
- 동기화: `mutating/service/ClusterVolumeChildLabelSynchronizer.java`(신규), `domain/k8s/LabelConstants.java`(owner 키 상수), `configuration/MutatingConfiguration.java`(빈)
- 테스트 2개 파일(`UserLabelReviewHandlerTest` admin 케이스, `ClusterVolumeChildLabelSynchronizerTest` 10건)
