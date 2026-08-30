# 구현 — ClusterVolume 자식(PVC/PV)에 소유자 라벨 전파

브랜치 `feat/clustervolume-child-user-labels`, base `upstream/develop` (`87011f8`, #112 직후).

## 변경 파일

| 파일 | 변경 |
|---|---|
| `src/main/java/.../domain/k8s/LabelConstants.java` | `CLUSTER_VOLUME_OWNER_KEY` = `clustervolumes.aipub.ten1010.io/owner` (= `CLUSTER_VOLUME_RESOURCE_PLURAL + "." + AIPUB_GROUP + "/owner"`) |
| `src/main/java/.../mutating/service/ClusterVolumeChildLabelSynchronizer.java` | **신규** — 자식 라벨 주기 동기화 (아래 상세) |
| `src/main/java/.../configuration/MutatingConfiguration.java` | synchronizer 빈 등록 (`userLabelSynchronizer` 옆) |
| `src/main/java/.../mutating/service/UserLabelReviewHandler.java` | admin 이 직접 만든 CV 에도 본인 라벨 주입(#112 는 member 만). 자식 관련 변경 없음 |
| `src/test/java/.../mutating/service/ClusterVolumeChildLabelSynchronizerTest.java` | **신규** 10건 |
| `src/test/java/.../mutating/service/UserLabelReviewHandlerTest.java` | admin CV 케이스 1건 동작 교체(라벨 값 검증) + AipubUser 없는 admin 케이스 1건 |

웹훅 yaml·CRD·리컨실러/인포머 변경 없음. `aipub-installer` 후속 PR 불필요.

## 책임 분리 (최종 구조)
```
CV 자체        → userrelationship 웹훅이 생성자(member/admin) 라벨 (즉시)  ← 소유자 기록의 정본
CV 자식 PVC/PV → ClusterVolumeChildLabelSynchronizer (≤60초)              ← 유일한 전파 경로
직접 만든 PVC  → 웹훅이 생성자 라벨 (기존 그대로, 이번 변경 없음)
```
CV 자식은 CV 컨트롤러 SA(비멤버)가 만들므로 웹훅 비멤버 경로에서 무변경 통과되고(기존 동작 그대로), 라벨은 동기화가 붙인다.

## `ClusterVolumeChildLabelSynchronizer` 상세

`UserLabelSynchronizer` 와 같은 구조: 단일 데몬 스레드, `ApplicationReadyEvent` 후 60초 `scheduleWithFixedDelay`, 페이지네이션 LIST(limit 500, 최대 1000페이지), `metadata.labels` 만 merge patch.

```
sync() — 매 60초, 예외는 catch 후 로그(스레드 유지)
  cvMap = LIST /apis/aipub.ten1010.io/v1alpha1/clustervolumes (전량)
          → { CV 이름 → [username, userid] (없는 키는 null) }
          실패 → 이번 주기 전체 스킵 (부분 정보로 라벨을 제거하지 않는다)
  for target in [persistentvolumeclaims(namespaced), persistentvolumes(cluster)]:
      children = LIST /api/v1/{plural}?labelSelector=clustervolumes.aipub.ten1010.io/owner
                 실패 → 해당 타깃만 스킵
      for child:
          terminating → skip; (PVC) allowlist ns → skip
          desired = cvMap[owner 라벨 값]; 없으면 skip (CV 삭제 중/stale — finalizer/GC 가 정리)
          현재 username/userid 와 둘 다 같으면 skip (멱등 — 정상 상태에서 주기당 PATCH 0회)
          PATCH …/{name} : {"metadata":{"labels":{username:…, userid:…}}}   # merge-patch, null=제거
                 실패 → 로그 후 계속, 다음 주기 재시도
```

- 편입(claimRef) 원본 PVC 는 `claimed-by` 라벨이라 LIST 셀렉터에 걸리지 않는다 — 남의 오브젝트를 건드리지 않는다.
- 커버 범위: 생성 직후 라벨링, **transfer**(CV 라벨 변경 추종), CV 라벨 제거 추종, 자가치유 재생성, CSI 가 만드는 앵커 PV(컨트롤러가 owner 라벨을 붙인 뒤 첫 주기), 배포 시 기존 자식 소급.

### transfer 흐름
```
백엔드 ─ CV cv-1 라벨 username: alice → bob ─▶ apiserver
  (≤60초) 동기화: 복제 PVC(각 대상 ns)·복제/앵커 PV 의 라벨 차이 감지 → merge patch
  ─▶ OwnedObjectInformerManager 가 PVC 라벨 변경 감지 (username 셀렉터 인포머)
  ─▶ alice 개인 Role 에서 해당 PVC 규칙 제거, bob 개인 Role 에 추가 (기존 RBAC 경로)
```

## 배포
컨트롤러 이미지 롤아웃만 하면 된다(웹훅 설정 변경 없음). 롤아웃 후 첫 주기에 기존 자식이 소급 라벨링된다.

## 이력 (검토 후 폐기한 구현)
1. 웹훅 비멤버 경로의 "owner 라벨 → CV GET → 라벨 복사"(PVC 즉시 라벨) + PV 전용 웹훅 엔트리 — 커밋 `902a377` 로 들어갔다가 제거. 즉시성의 실익이 없고 어드미션 경로에 CV GET(fail-closed) 결합이 비용.
2. 인포머+워크큐 리컨실러(`ClusterVolumeChildLabelReconciler`) — 커밋 `902a377` 로 들어갔다가 스케줄 방식으로 교체. 스냅샷 `_workspace/approach1-*`.
