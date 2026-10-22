# 업데이트된 워크로드 CRD 반영 — 구현

- Jira: [AIP-3585](https://ten1010.atlassian.net/browse/AIP-3585)
- 브랜치: `feat/AIP-3585` (base `develop` 3f7823a)
- 규모: 7개 파일, +469 / -512 (예시 CRD 재생성이 대부분)

## 1. 변경 파일

### 신규 (3)

| 파일 | 내용 |
|---|---|
| `domain/k8s/dto/V1beta1WorkspaceSuspendOption.java` | `spec.suspendOption` — nested `Commit{enabled, repository, applyImage}` |
| `domain/k8s/dto/V1beta1WorkspaceSuspendCommitStatus.java` | `status.suspendCommit{image, prevImage, appliedImage}` |
| `test/.../dto/WorkloadDtoDeserializationTest.java` | 역직렬화 회귀 테스트 4건 |

### 수정 (4)

| 파일 | 내용 |
|---|---|
| `domain/k8s/dto/V1beta1WorkspacePortExpose.java` | nested `Inference{host, path}` + 필드 추가 |
| `domain/k8s/dto/V1beta1WorkspaceSpec.java` | `suspendOption` 배선 (`suspend` 바로 뒤) |
| `domain/k8s/dto/V1beta1WorkspaceStatus.java` | `suspendCommit` 배선 (`ssh` 바로 뒤) |
| `kubernetes/examples/crd.yaml` | `operations`·`workspaces` 문서를 클러스터 기준 재생성 |

## 2. 설계 판단

### 2.1 Workspace 의 expose 를 Operation 과 같은 모양으로 맞췄다

`V1alpha1OperationPortExpose` 가 AIP-2269 에서 `enabled / type / tcp / http / inference` 순서로
정리돼 있다. Workspace 쪽도 같은 순서·같은 nested 클래스 이름으로 맞췄다 — 두 CRD 의 expose 는
클러스터에서 동일 스키마이고, 한쪽만 다른 모양이면 다음 개정 때 또 어긋난다.

`Inference` 에 `tls` 가 없는 것은 의도다. 추론 노출은 항상 HTTPS 라 CRD 에도 `tls` 가 없다.

### 2.2 `suspendOption` / `suspendCommit` 은 별도 최상위 DTO 로 뺐다

`V1beta1WorkspaceSsh` 가 nested `Password` 를 갖는 것과 같은 방식으로, 최상위 필드 1개당 DTO 1개 +
그 안의 nested 클래스로 구성했다. `suspendOption` 의 유일한 필드 `commit` 을 nested `Commit` 으로
둔 것은 CRD 가 "정지 시 동반 행위"라는 묶음을 유지하기 때문이고, 뒤에 커밋 외 행위가 추가돼도
배선이 안 바뀐다.

상태 DTO 이름은 기존 관례(`V1beta1WorkspaceSshStatus`·`PortStatus`·`ToolStatus`)에 맞춰
`V1beta1WorkspaceSuspendCommitStatus` 로 했다.

### 2.3 `tools[].type` 의 `vscode` 는 코드 변경이 없다

CRD enum 에 `vscode` 가 추가됐지만 DTO 는 `String` 이다. enum 으로 바꾸면 다음 tool 이 추가될 때
역직렬화가 깨지므로(컨트롤러가 값을 분기하지도 않는다) 그대로 뒀다. 대신 `vscode` 가 그대로
실리는지를 테스트로 고정했다.

### 2.4 예시 CRD 는 클러스터 덤프에서 기계 생성하고 `template` 만 축약했다

`kubernetes/examples/crd.yaml` 의 두 문서는 손으로 고치지 않고 **클러스터 CRD 를 그대로 옮긴 뒤
`spec.template`(PodTemplateSpec 전문, 8천 줄 남짓)만 기존 예시의 축약본으로 교체**했다. 기존 파일이
이미 그 축약 방식이었고, 전문을 실으면 예시 파일이 쓸 수 없게 커진다.

그 결과 드러난 것:

- `operations` 문서가 **구 계약(`ports[].external{enabled,host,path}`)** 을 그대로 들고 있었다.
  DTO 는 AIP-2269 에서 `expose` 로 옮겨갔는데 예시가 따라가지 않았다.
- `workspaces` 문서는 아예 **`v1` 스키마**였다(`ssh.publicKey` · `status.addresses/pods/
  readyCondition/sshEndpoint`). 클러스터에는 `v1` 이 없다 — 적용하면 현행과 충돌한다.
  `v1beta1` 로 교체했다.

`apiVersion`/`kind` 프로퍼티는 기존 예시 관례대로 싣지 않았다.

## 3. 리컨실 영향

없다. `AipubUserRoleReconciler` 가 Workspace/Operation 인덱서에서 꺼내 쓰는 것은
`K8sObjectUtils.getName()` 뿐이고, 추가한 필드는 어떤 분기에도 닿지 않는다. 멱등성 판정 경로
(`List.equals`)도 RBAC 룰 리스트만 비교하므로 영향이 없다.

## 4. 테스트

`WorkloadDtoDeserializationTest` 4건. 인포머와 같은 Gson 설정(`io.kubernetes.client.openapi.JSON`)
으로 CR JSON 을 읽어 필드가 실제로 매핑되는지 본다.

| 테스트 | 고정하는 것 |
|---|---|
| `workspaceExposeInference_isMapped` | `expose.inference{host,path}`, `type=INFERENCE`, tcp/http 는 null |
| `workspaceSuspendCommit_isMapped` | `spec.suspendOption.commit` 3필드, `status.suspendCommit` 3필드 |
| `operationExposeInference_isMapped` | Operation 쪽 대칭 + `status.ports[].type` |
| `workspaceVscodeTool_isMapped` | `tools[].type = vscode` 가 String 으로 그대로 실림 |

이 테스트가 필요한 이유: 리컨실 경로가 name/label 만 쓰기 때문에 필드명을 틀려도 **런타임에
아무 증상이 없다**. 나중에 누가 이 필드를 읽을 때 조용히 null 이 된다.
