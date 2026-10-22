# 업데이트된 워크로드 CRD 반영 — 계획

- Jira: [AIP-3585](https://ten1010.atlassian.net/browse/AIP-3585) (상위 에픽 AIP-2646 `[5.2.0][JA] AIPub Backend 개선`)
- 브랜치: `feat/AIP-3585` (base `develop` 3f7823a)
- 기준 스키마: **cluster10(c10-m1) 배포본** — `sudo kubectl get crd <name> -o yaml` (2026-10-22 조회)
- 관련: [AIP-3412](https://ten1010.atlassian.net/browse/AIP-3412) 워크로드 포트 공개 방식에 추론(INFERENCE) 추가 (FE 범위)

## 1. 배경

cluster10 에 배포된 워크로드 CRD 3종이 갱신됐다. 버전 이름은 그대로(`Operation` v1alpha1 ·
`Workspace` v1beta1 · `ServiceExposure` v1alpha1)고 스키마만 바뀌었다.

`Operation` 쪽은 어제(`623862f`, AIP-2269) 이미 `expose.inference` 를 반영해 develop 이 클러스터와
맞는다. **반영이 안 된 것은 `Workspace` 쪽**이다 — Operation 과 Workspace 는 같은 expose 구조를
공유하는데(AIP-2269 에서 통일) INFERENCE 는 Operation 에만 들어갔다.

## 2. 클러스터 ↔ develop 대조 결과

### Workspace (v1beta1) — 반영 필요 4건

| # | 클러스터 스키마 | develop DTO |
|---|---|---|
| 1 | `spec.ports[].expose.inference{host*, path}` · `expose.type` enum 에 `INFERENCE` | `inference` 없음 |
| 2 | `spec.suspendOption.commit{enabled, repository*, applyImage}` | `suspendOption` 없음 |
| 3 | `status.suspendCommit{image, prevImage, appliedImage}` | `suspendCommit` 없음 |
| 4 | `spec.tools[].type` enum 에 `vscode` · 예약 포트에 17682 | DTO 는 `String` 이라 변경 불필요 |

부가로 클러스터에만 있는 것(모두 CEL 검증·예약 목록이라 DTO 영향 없음):
`expose.inference` 조합 검증 3종, `securityContext` 의 `runAsUser==0`·`runAsNonRoot!=true`·
`readOnlyRootFilesystem!=true` 검증, 예약 포트 이름/번호에 `vscode`/`17682` 추가.

### Operation (v1alpha1) — DTO 반영 완료

`spec.autoScaling` · `spec.ports[].expose{enabled,type,tcp,http,inference}` · `status.ports[].type`
까지 develop DTO 와 클러스터가 **일치**한다. 단, `kubernetes/examples/crd.yaml` 의 참조 사본은
아직 구 계약(`ports[].external{enabled,host,path}`)이라 DTO 와 어긋나 있다.

### ServiceExposure (v1alpha1) — DTO 범위 밖

`spec.rules[].inference{host*, path}` 와 `type` enum 의 `INFERENCE` 가 추가됐다. 다만 이 컨트롤러는
ServiceExposure 를 **읽지도 쓰지도 않는다** — 인포머 등록 0건, RBAC 룰 문자열 0건, 웹훅 대상 0건
(`grep -ri serviceexposure` 가 `kubernetes/examples/crd.yaml` 주석 1건만 잡는다). 상위 컨트롤러
(workload-controller-manager)가 Workspace/Operation 의 expose 로부터 파생 생성하는 내부 CR 이다.
따라서 **DTO 를 만들지 않는다**(AIP-3419 의 Cueue 와 같은 판단).

## 3. 범위

| # | 항목 | 파일 |
|---|---|---|
| 1 | `expose.inference` | `V1beta1WorkspacePortExpose` (nested `Inference` 추가) |
| 2 | `spec.suspendOption` | `V1beta1WorkspaceSuspendOption` 신규 + `V1beta1WorkspaceSpec` 배선 |
| 3 | `status.suspendCommit` | `V1beta1WorkspaceSuspendCommitStatus` 신규 + `V1beta1WorkspaceStatus` 배선 |
| 4 | 예시 매니페스트 | `kubernetes/examples/crd.yaml` 의 `operations`·`workspaces` 문서를 클러스터 기준으로 재생성 |
| 5 | 회귀 방지 테스트 | `WorkloadDtoDeserializationTest` 신규 |

**범위 밖**

- **ServiceExposure DTO** — §2 참조. 참조처가 0건이라 dead code 가 된다.
- **리컨실 로직 변경** — 이 컨트롤러가 워크로드 CR 에서 쓰는 것은 `metadata.name`/`namespace`/
  소유자 라벨뿐이다(`AipubUserRoleReconciler` 가 인덱서로 이름만 모아 RBAC `resourceNames` 를 만든다).
  expose·suspend 필드는 어떤 분기도 타지 않는다.
- **RBAC 변경** — 리소스 plural(`workspaces`/`operations`)이 그대로라 규칙이 바뀌지 않는다.
- **웹훅 `rules` 변경** — 라벨 낙인 대상 목록(Workspace/Operation/ChainJob)이 그대로다.
- **aipub-load-prober 의 CRD 사본** — 같은 티켓이지만 별도 레포·별도 PR
  (`chore/AIP-3585-crd-sync`). 이 레포 PR 과 독립적으로 머지 가능하다.

## 4. 검증 계획

- `./gradlew clean compileJava compileTestJava test`
- 신규 DTO 가 인포머와 같은 Gson(`io.kubernetes.client.openapi.JSON`)으로 역직렬화되는지 —
  필드명 오타는 리컨실 경로가 안 건드려서 런타임에 드러나지 않는다
- `kubernetes/examples/crd.yaml` 이 8-doc 으로 파싱되고, `operations`·`workspaces` 스키마가
  `template` 축약본을 뺀 나머지에서 클러스터와 **완전 일치**하는지 (프로그램 대조)
- `kubectl apply --dry-run=server` 로 apiserver 가 받아들이는지
