# AIP-1998 — KubeVirt virt-handler 파드 churn 진단/조치 노트

> 원인 확정 및 조치 완료 기록. (초기 인계 메모 → 클러스터 실측으로 결론 확정)

## TL;DR

- virt-handler 파드가 ~10~30초 주기로 재생성되고 DaemonSet generation이 무한 증가하던 현상의 원인은 **virt-handler DS 템플릿을 두고 벌어지는 두 개의 독립적인 패치 전쟁**이었다.
  - **War A** — `template.spec.affinity` 를 두고 **coaster ↔ virt-operator** 가 다툼.
  - **War B** — `template.spec.tolerations` 를 두고 **project-controller ↔ virt-operator** 가 다툼.
- 두 전쟁 모두 "제3의 writer가 virt-operator 소유 DS를 직접 패치 → virt-operator가 KubeVirt CR 기준 desired로 되돌림" 이라는 동일 구조다.
- 조치: **(A)** KubeVirt CR `spec.workloads.nodePlacement.affinity` 에 coaster의 evict-ds affinity를 동일하게 선언, **(B)** project-controller의 `kubevirt.io` reconcile 제외 유지. 두 조치는 서로 독립적이며 **둘 다 필요**하다(실험으로 확인).

## 근본 원인: virt-operator의 DaemonSet 소유/재조정

KubeVirt `pkg/virt-operator/resource/apply/apps.go` 의 `syncDaemonSet`:

- `DaemonSetIsUpToDate(...)` + generation 비교(`GetExpectedGeneration`)로 실제 DS가 desired와 다른지 감지한다.
- `placement.InjectPlacementMetadata(kv.Spec.Workloads, &daemonSet.Spec.Template.Spec, ...)` 로 **KubeVirt CR `spec.workloads` 기준의 desired 템플릿(affinity/nodeSelector/tolerations)을 강제**한다.
- 차이가 있으면 `patchDaemonSet` 으로 되돌린다.

즉 **누가 virt-handler DS 템플릿을 직접 패치하든, 그 값이 KubeVirt CR에 반영돼 있지 않으면 virt-operator가 매번 원복**한다. 상대 writer가 다시 쓰면 → 무한 패치 루프 → generation 증가 → rolling update → 파드 churn.

> 일반 DaemonSet(csi-nfs-node, dcgm-exporter 등)은 이런 능동적 소유자가 없어 외부 주입이 그대로 유지되므로 문제가 없다. **virt-handler만 소유자(virt-operator)가 지속 reconcile하기 때문에** 전쟁이 난다.

## War A — affinity (coaster ↔ virt-operator)

- coaster(GPU 오퍼레이터)는 GPU 재구성 시 노드에서 DaemonSet을 드레인하기 위해 **클러스터의 거의 모든 DaemonSet에 evict-ds nodeAffinity를 주입**한다. virt-handler에 주입하는 값은:

  ```yaml
  nodeAffinity:
    requiredDuringSchedulingIgnoredDuringExecution:
      nodeSelectorTerms:
      - matchExpressions:
        - key: gpuconfig.coaster.ten1010.io/evict-ds
          operator: DoesNotExist
  ```

- KubeVirt CR에는 affinity가 선언돼 있지 않았으므로(→ virt-operator의 desired에는 affinity 없음), virt-operator가 이 affinity를 drift로 보고 계속 제거 → coaster가 재주입 → 전쟁.
- managedFields상 affinity 필드 소유자는 `OpenAPI-Generator` (coaster의 Kubernetes 클라이언트 기본 field manager).

**직접 관찰(조치 전):**

```
gen=15853  affinityKey=[gpuconfig.coaster.ten1010.io/evict-ds]   ← coaster 주입
gen=15854  affinityKey=[]                                         ← virt-operator 제거 (gen++)
gen=15857  affinityKey=[gpuconfig.coaster.ten1010.io/evict-ds]    ← coaster 재주입 (gen++)
```

**조치 A:** KubeVirt CR에 coaster가 원하는 affinity를 **정확히 동일하게** 선언 → virt-operator의 desired에 포함되어 더 이상 원복하지 않음. 값이 다르면(예: 키 추가) 새 전쟁이 나므로 관찰된 단일 키를 그대로 사용해야 한다.

```yaml
# KubeVirt CR spec.workloads.nodePlacement
affinity:
  nodeAffinity:
    requiredDuringSchedulingIgnoredDuringExecution:
      nodeSelectorTerms:
      - matchExpressions:
        - key: gpuconfig.coaster.ten1010.io/evict-ds
          operator: DoesNotExist
tolerations:                       # 기존 유지
- { effect: NoSchedule, key: project.aipub.ten1010.io/project-managed, operator: Exists }
- { effect: NoExecute,  key: project.aipub.ten1010.io/project-managed, operator: Exists }
```

**검증(조치 후):** apply 직후 gen +1(반영) 후 **고정**, affinity present 유지, 파드 안정(RESTARTS=0, AGE 지속 증가).

## War B — tolerations (project-controller ↔ virt-operator)

`kubevirt.io` 제외를 걷어내고 project-controller를 재기동해 **격리 실험**으로 확정했다.

- virt-handler DS는 **ownerReference가 없어서**, project-controller의 `WorkloadControllerReconciler` owner-ref skip에 걸리지 않는다. `kubevirt` 네임스페이스는 reserved 목록에 없어 project는 null.
- 제외가 없으면 reconciler가 tolerations를 재조정: CR의 `project-managed Exists` 를 **per-node `project-managed Equal value=node01/node02`** 로 치환(`buildProjectManagedTolerations`). virt-operator는 CR 기준 `Exists`로 원복 → 전쟁.
- managedFields상 tolerations를 쓰는 writer는 `Kubernetes Java Client` (**project-controller**의 field manager). → affinity를 쓰던 `OpenAPI-Generator`(coaster)와 **field manager 이름이 달라 둘이 명확히 구분**된다.

**직접 관찰(제외 제거 상태):** tolerations가 두 값 사이를 flip하며 generation 증가, 파드 ~7초 주기 롤링.

```
gen=16607 tolOperators=[Exists Equal Equal Equal Equal]   ← project-controller (per-node Equal)
gen=16608 tolOperators=[Exists Exists Exists]             ← virt-operator 원복 (gen++)
gen=16611 tolOperators=[Exists Equal Equal Equal Equal]   ← project-controller 재적용 (gen++)
gen=16614 tolOperators=[Exists Exists Exists]             ← virt-operator 원복 (gen++)
```

**조치 B:** project-controller의 `app.aipub.reconcile-excluded-label-selectors` 에 `kubevirt.io` 유지(운영에서는 `project-controller-envs` ConfigMap의 `APP_AIPUB_RECONCILE_EXCLUDED_LABEL_SELECTORS`). 제외가 걸리면 reconciler·Pod/Deployment webhook 3개 지점 모두 virt-handler를 건드리지 않는다.

**검증(제외 복구 후):** gen 고정, tolerations가 `[Exists Exists Exists]`(virt-operator desired)로 정착, 파드 안정. Pod webhook 로그에 `allowed without patch ... virt-handler-` 재확인.

## 두 전쟁 요약

| | War A | War B |
|---|---|---|
| 다투는 필드 | `template.spec.affinity` | `template.spec.tolerations` |
| 상대 writer | coaster (`OpenAPI-Generator`) | project-controller (`Kubernetes Java Client`) |
| 트리거 | coaster의 evict-ds affinity 주입 | project-controller의 per-node tolerations 재조정 |
| 조치 | KubeVirt CR에 affinity 동일 선언 | project-controller `kubevirt.io` 제외 유지 |
| 상태 | **해결** | **해결(제외 유지)** |

## 현재 적용 상태

- KubeVirt CR: `spec.workloads.nodePlacement.affinity` 에 evict-ds affinity 추가 적용됨. (설치 소스: `mdc-root:/root/kubevirt/pjw/kubevirt-cr.yaml`, 리포 사본: `kubernetes/kubevirt-cr-pjw.yaml`)
- project-controller: `kubevirt.io` 제외 유지, 재기동 완료.

## 남은 과제 / 주의

- **근본 해결(권장)**: coaster가 오퍼레이터 소유(virt-operator 등) DaemonSet을 직접 패치하지 않고, 해당 오퍼레이터의 API(KubeVirt CR nodePlacement 등)를 통해 배치를 구성하도록 개선. 별도 티켓 권장.
- **취약한 결합**: 조치 A는 coaster가 virt-handler에 주입하는 affinity 값과 CR 값이 **정확히 일치**해야 no-op이 된다. coaster가 주입 형태를 바꾸면(예: `tpc-discovery.coaster.ten1010.io/evict-ds` 키 추가) War A가 재발하므로 CR도 함께 갱신해야 한다.
- **Helm 관리**: `project-controller-envs`, KubeVirt CR 모두 배포 도구로 관리되므로, 위 조치는 각 설치 매니페스트/차트에도 반영해야 재배포 시 유지된다.
- 진단용 임시 로그 커밋(`[AIP-1998-EXCLUDE]`)은 검증 완료 후 히스토리에서 제거함(별도 revert 커밋 없이 정리).
