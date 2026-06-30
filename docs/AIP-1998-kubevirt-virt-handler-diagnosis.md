# AIP-1998 — KubeVirt virt-handler 파드 churn 진단 노트

> 퇴근 전 인계용 메모. 집에서 이어서 확인하기 위한 스냅샷.
> 진단 로그 커밋(`[AIP-1998-EXCLUDE]`)이 적용된 이미지로 배포한 뒤의 관찰 기록이다.

## TL;DR

- **project controller ↔ virt-operator 패치 루프 자체(AIP-1998 본래 문제)는 fix가 동작 중.** project controller 로그에 virt-handler를 skip하는 로그가 정상적으로 찍힌다.
- **그런데 virt-handler 파드는 여전히 약 30초 주기로 죽고, DaemonSet generation도 계속 오른다.**
- generation이 계속 오른다 = **누군가 DaemonSet 템플릿(spec)을 반복적으로 패치하고 있다.** project controller는 skip 중이므로 **그 writer는 project controller가 아니다.** → virt-operator 또는 coaster(GPU 오퍼레이터) 등 다른 주체가 유력.
- 즉 **남은 churn은 AIP-1998(project controller)과 별개의 원인**이며, 별도 진단이 필요하다. AIP-1998 PR 자체는 이대로 머지 가능하고, 진단 로그 커밋만 나중에 revert하면 된다.

## 확정된 사실 (fix 동작 증거)

project controller 로그:

```
[AIP-1998-EXCLUDE] reconcile skipped for excluded workload: kind=V1DaemonSet namespace=kubevirt name=virt-handler
```

→ `reconcile-excluded-label-selectors` 의 `kubevirt.io` 셀렉터가 적용되어, project controller의 DaemonSet reconciler가 virt-handler를 더 이상 패치하지 않는다. (scale-to-0 테스트 때도 generation 증가가 멈췄던 것과 일관)

## 미해결: 남은 30초 주기 파드 churn

관찰된 증상:

- virt-handler 파드가 Running 까지 갔다가 `Killing/Stopping` 되고 **새 파드 이름으로 재생성**된다 (컨테이너 in-place 재시작이 아님, RESTARTS=0).
- 이벤트:
  - `Warning FailedUpdate  daemonSet virt-handler rollout failed`
  - `Warning PodFailed     Pod is in Failed state` (반복)
- **DaemonSet generation 계속 증가** (project controller skip 중인데도) → 다른 writer가 템플릿을 패치 중.

유력 단서 — DaemonSet 템플릿의 nodeAffinity:

```yaml
affinity:
  nodeAffinity:
    requiredDuringSchedulingIgnoredDuringExecution:
      nodeSelectorTerms:
      - matchExpressions:
        - key: gpuconfig.coaster.ten1010.io/evict-ds
          operator: DoesNotExist
```

- `evict-ds`(DaemonSet 축출)라는 이름 + reservedNamespace 목록에 `coaster`가 포함됨.
- 가설: **coaster(GPU 오퍼레이터)가 노드에 `gpuconfig.coaster.ten1010.io/evict-ds` 라벨을 붙였다/뗐다 하며 virt-handler 파드를 축출·재생성**시키고 있을 가능성. 또는 virt-operator가 자신의 desired state와 어긋남을 감지해 파드를 지우는 루프.
- generation이 오르는 것까지 설명하려면, 누군가 **템플릿(affinity 등)을 주기적으로 바꾸는** 쪽을 의심해야 한다 (coaster ↔ virt-operator 간 또다른 2-writer 충돌 가능성).

## 집에서 확인할 진단 명령

```bash
# 1. 파드가 왜 죽는지 — Last State / Exit Code / 종료 사유
kubectl describe pod -n kubevirt <virt-handler-파드명> | grep -A15 "State\|Last State\|Events"

# 2. 직전 컨테이너 종료 로그
kubectl logs -n kubevirt <virt-handler-파드명> --previous

# 3. 노드에 evict-ds 라벨이 붙는지 (핵심 가설) — 시간차로 여러 번 확인
kubectl get node node02 --show-labels | tr ',' '\n' | grep -i "coaster\|evict\|gpu"
kubectl get node node01 --show-labels | tr ',' '\n' | grep -i "coaster\|evict\|gpu"

# 4. 누가 파드를 지우는지 — virt-operator가 지우는지 확인
kubectl logs -n kubevirt -l kubevirt.io=virt-operator --tail=200 | grep -i "virt-handler\|delete\|evict"

# 5. coaster 컴포넌트 확인
kubectl get pods -n coaster 2>/dev/null

# 6. DaemonSet generation이 정말 계속 오르는지 + 무엇이 바뀌는지
kubectl get ds virt-handler -n kubevirt -w
#   generation이 오르는 순간 spec diff를 떠서 어떤 필드가 바뀌는지 확인
kubectl get ds virt-handler -n kubevirt -o yaml > /tmp/ds-1.yaml
# (수 초 후)
kubectl get ds virt-handler -n kubevirt -o yaml > /tmp/ds-2.yaml
diff /tmp/ds-1.yaml /tmp/ds-2.yaml
```

**핵심 질문**: 6번 diff에서 generation이 오를 때 **어떤 spec 필드가 바뀌는가?** 그게 바뀌는 필드를 보면 어떤 writer(virt-operator vs coaster)인지 특정할 수 있다. 그리고 3번에서 node01/node02에 `evict-ds` 라벨이 들락거리는지 확인.

## 정리 표

| 항목 | 상태 |
|---|---|
| project controller ↔ virt-operator 패치 루프 (AIP-1998) | **해결** (skip 로그 확인) |
| AIP-1998 fix 동작 | **검증 완료** |
| 남은 30초 파드 churn + generation 증가 | **별개 원인 (project controller 아님)** — coaster `evict-ds` / virt-operator 재패치 추정, 추가 진단 필요 |

## 후속 작업

- AIP-1998 PR(#69 resource-group-controller, #140 aipub-installer)은 이대로 머지 가능.
- 진단 로그 커밋(`[AIP-1998-EXCLUDE]`)은 검증 종료 후 `git revert` 로 되돌린다.
- 남은 churn은 coaster/kubevirt 쪽 별도 티켓으로 분리해 다루는 것을 권장.
