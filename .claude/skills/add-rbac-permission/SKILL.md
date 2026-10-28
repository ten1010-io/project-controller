---
name: add-rbac-permission
description: "project-controller 에 새 리소스(CRD·네이티브)에 대한 사용자 권한을 추가하는 반복 작업을 케이스별 레시피로 수행하는 스킬. '권한 추가', 'RBAC 추가', '403 이 난다', '새 CRD 권한', '생성자만 수정/삭제', '소유자 권한', '프로젝트에 속한 것만 조회', 'resourceNames 로 좁혀', 'PM 만', 'ClusterRole 에 규칙 추가' 등의 요청에 사용. 어떤 그릇(프로젝트 Role / 프로젝트 ClusterRole / 개인 Role / 개인 ClusterRole / 정적 매니페스트)에 어떤 규칙을 넣어야 하는지 분류부터 한다."
---

# RBAC 권한 추가

새 CRD·리소스가 생길 때마다 반복되는 "권한 추가" 작업을 **분류 → 레시피 → 검증** 순으로 수행한다.
코드 경로별 상세 절차는 [`references/recipes.md`](references/recipes.md) 에 있다.

## 0. 먼저 확인할 것 (모르면 사용자에게 묻는다)

| 질문 | 왜 필요한가 |
|---|---|
| 리소스 GVR (group / version / plural) 과 Kind | 규칙·인포머·`WorkloadResourceResolver` 매핑의 키 |
| scope — Namespaced / Cluster | 그릇이 달라진다 (Role vs ClusterRole) |
| **누구에게** — 프로젝트 멤버 전원 / PM 만 / 생성자 본인 / 로그인 사용자 전원 | 케이스 분기의 핵심 |
| verbs — 조회만 / create / 수정·삭제 | |
| cluster scope 라면 **이름을 어떻게 아는가** (프로젝트명? 노드명? 다른 오브젝트에서 파생?) | `resourceNames` 산정 방법 |
| 프론트가 `list` 를 쓰는가, 이름으로 `get` 하는가 | cluster scope `list` 는 좁힐 수 없다 |

관련 Jira 에픽·티켓을 먼저 조회한다 — 이 팀은 설계 근거를 티켓에 남긴다.

## 1. 권한이 만들어지는 그릇 5개

| 그릇 | 코드 | 대상 | 스코프 |
|---|---|---|---|
| **P-Role** 프로젝트 Role | `ReconciliationService.reconcileProjectRoleRules(project, role)` | 프로젝트 멤버 (PM / PD 분기) | 프로젝트 namespace |
| **P-CR** 프로젝트 ClusterRole | `ReconciliationService.reconcileClusterRoleRules(project, role, nodeGroups, nodes, resourceSets, users, pvs)` | 프로젝트 멤버 (PM / PD 분기) | 클러스터 |
| **U-Role** 개인 Role | `ReconciliationService.reconcileAipubUserRoleRules(...)` ← `AipubUserRoleReconciler` | 사용자 1명 | 프로젝트 namespace |
| **U-CR** 개인 ClusterRole | `ReconciliationService.reconcileClusterRoleRules(aipubUser)` | 사용자 1명 | 클러스터 |
| **Static** 정적 매니페스트 | `kubernetes/controller/project-controller/templates/cluster-role.yaml` 의 `aipub-member` (+ `aipub-installer` helm 차트) | `oidc:aipub-member` 그룹 전원 | 클러스터 |

리컨실러 그릇(P-Role/P-CR/U-Role/U-CR)은 매 리컨실마다 룰을 **전량 교체**한다 — 코드에서 지우면 클러스터에서도 회수된다. Static 은 그렇지 않다.

## 2. 케이스 분류

```
누구에게?
├─ 프로젝트 멤버 전원 ───────── Namespaced? ── 예 → [케이스 1] 프로젝트 namespace 공용
│                                           └ 아니오 → 이름 출처는? → [케이스 3] 프로젝트 귀속 조회 (3-a~3-d)
│                                                                    └ 좁힐 축이 없다 → [케이스 6] 공용 카탈로그
├─ PM 만 더 ───────────────────────────── [케이스 4] 역할 차등 (1·3 위에 얹음)
├─ 생성자 본인만 수정/삭제 ─── CRD? ── 예 → [케이스 2-a] 소유 CRD 워크로드  (Java 클래스 필요)
│                             └ 아니오 → [케이스 2-b] 소유 네이티브 리소스 (OWNED_TARGETS 한 줄)
├─ 사용자 본인 것 (이름 = username, cluster scope) ─ [케이스 5] 개인 cluster 리소스
└─ 로그인 사용자 전원, create 전용 Review/Validation API ─ [케이스 7] 정적 aipub-member
+ 서브리소스(pods/log, */status 등)가 필요하면 ─ [케이스 8] 상위 리소스와 같은 그릇에 명시
```

| # | 케이스 | 그릇 | 기존 예 | 대표 커밋 | 비용 |
|---|---|---|---|---|---|
| 1 | 프로젝트 namespace 공용 — PM `*` / PD `BASIC_VERBS` | P-Role | operations, workspaces, chainjobs, commits, imagebuilds, fileservers, workloadgroups | AIP-3518 (일부) | 규칙 2개 |
| 2-a | 생성자만 update/patch/delete — CRD | U-Role | Workspace, Operation, ChainJob, AipubVolume, SftpServer, ImageBuild, FileServer, WorkloadGroup (+ Job, CronJob) | AIP-3518 `772c9a6` | **파일 ~11개** |
| 2-b | 생성자만 update/patch/delete — 네이티브 | U-Role | pods, configmaps, secrets, services, deployments 등 16종 | `c28c91b`, `cf432d1` | 한 줄 + 웹훅 rules |
| 3-a | 프로젝트명 = 리소스명 | P-CR | projects, namespaces, cueues, ibnetworks | AIP-3631 `d15d83f` | 규칙 1개 |
| 3-b | 바인딩 노드명 = 리소스명 | P-CR | nodes, noderesources, gpuconfigs, nodegpuusages, sriovnodestates | AIP-3631 `d15d83f` | 규칙 1개 |
| 3-c | spec 바인딩 오브젝트 | P-CR | nodegroups, resourcesets | — | 규칙 1개 |
| 3-d | 다른 리소스에서 **계산**해 파생 | P-CR | persistentvolumes (PVC claimRef / Available) | AIP-34 `618ceef` | **watch·RequestBuilder·리졸버 추가** |
| 4 | PM 만 추가 권한 | P-Role / P-CR | cueues patch·update, 멤버 userresourcequotas·userworkspacereclaims get | AIP-3419 | `switch` 분기 |
| 5 | 사용자 본인 cluster 리소스 (`resourceNames=username`) | U-CR | aipubusers, userresourcequotas, gpuquotas, userworkspacereclaims | — | 규칙 1개 |
| 6 | 공용 카탈로그 (좁히지 않은 `get,list`) | P-CR | storageclasses, ingressclasses | — | 규칙 1개 — **노출해도 되는지 판단 필수** |
| 7 | create 전용 Review/Validation API | Static (또는 P-CR) | imagereviews, userauthorityreviews, hostpathvalidations, tcpportvalidations | — | 매니페스트 + installer 차트 |
| 8 | 서브리소스 | 상위와 같은 그릇 | pods/log, pods/exec | — | resources 에 추가 |

**자주 같이 간다:** 2-a/2-b 는 항상 **케이스 1과 짝**이다 (멤버 전원 `create/get/list/watch` + PM `*` + 생성자에게만 수정/삭제). FileServer·WorkloadGroup 이 이 계약이다.

## 3. 반드시 지킬 불변식

1. **cluster scope `list`/`watch` 는 `resourceNames` 로 좁혀지지 않는다.** 좁혀야 하는 리소스에는 `get` 만 주고, 프론트는 이름으로 get 하게 한다(실제 예: `useK8sPVsQuery` 의 폴백).
2. **빈 `resourceNames` 는 "전체 허용"이다.** 이름 목록이 비면 규칙 자체를 넣지 않는다 (`if (!names.isEmpty())`).
   - ⚠ 알려진 위반: `noderesources`·`gpuconfigs`·`nodegpuusages` 는 바인딩 노드가 없어도 규칙을 넣는다 — 새 코드에서 이 패턴을 복사하지 않는다.
3. **이름 목록은 `distinct().sorted()`** 로 넣는다 — 일부 리컨실러가 `List.equals`(순서 비교)로 갱신 여부를 판단해, 정렬하지 않으면 매 주기 UPDATE 가 돈다.
4. **enum 분기는 `switch` 식, `default` 없음** — `ProjectRoleEnum` 에 값이 추가되면 컴파일 에러로 잡힌다.
5. **새 리소스를 참조하면 인포머를 먼저 등록**하고, 컨트롤러에 watch 를 추가하면 **`withReadyFunc` 도 추가**한다 (미등록이면 `getExistingSharedIndexInformer` 가 null → 기동 시 NPE).
6. **소유 판정은 label(`aipub.ten1010.io/username`) 기준**이다. ownerReference(AipubUser)는 GC 용도로만 주입되고 RBAC 판정에는 쓰이지 않는다.
7. 소유 label 은 `mutating-webhook-user-v2.yaml` 이 CREATE 때 찍는다. `aipub.ten1010.io` 그룹의 Namespaced 리소스는 `*` 로 이미 걸려 있다 — **새 API group 이거나 cluster scope 일 때만** 웹훅 rules(이 repo + `aipub-installer` helm 차트)를 고친다.
8. 컨트롤러 자신의 ClusterRole(`cluster-role.yaml` 의 `project-controller`)은 와일드카드다 — 인포머 권한은 보통 손대지 않는다.

## 4. 작업 순서

1. **분류** — §0 질문에 답하고 §2 표에서 케이스를 고른다. 여러 개가 겹치는 게 보통이다(예: 신규 namespaced CRD + 생성자 전용 = 1 + 2-a).
2. **사용자에게 분류 결과를 한 줄로 확인**받는다 — "X 는 케이스 1+2-a 로 처리: 멤버 전원 생성·조회, 수정·삭제는 생성자와 PM".
3. **레시피 적용** — [`references/recipes.md`](references/recipes.md) 의 해당 절을 따른다. 대표 커밋의 diff 를 `git show <sha> -- src/main` 으로 열어 그대로 미러링하는 게 가장 빠르다.
4. **테스트** — 케이스별 테스트 템플릿은 recipes.md 의 "테스트" 절. 이름은 `<상황>_<기대>`.
5. **검증**
   ```bash
   ./gradlew compileJava compileTestJava test
   ./gradlew :test --tests "*ReconciliationService<Xxx>RulesTest*"   # 단일 클래스 (루트 :test 지정 필수)
   ```
6. **PR Test plan 에 사람 확인 항목**을 남긴다 (단위 테스트로 흉내내지 않는다):
   - `kubectl get clusterrole|role <이름> -o yaml` 로 규칙 반영 확인
   - `kubectl auth can-i <verb> <resource>[/<name>] [-n <ns>] --as=oidc:<유저>` — 허용 / **비허용 양쪽** (다른 프로젝트 이름, 다른 사용자 소유 오브젝트)
   - 프론트 권한 표시는 `UserAuthorityReview` 를 거치는 별도 구현이라 apiserver 판정과 어긋날 수 있다 → 화면에서도 확인
7. **커밋 메시지에 Jira 키**, 코드 주석에는 넣지 않는다. 주석은 "왜 이렇게 좁혔는지 / 안 하면 무엇이 열리는지" 한 줄.

## 5. 산출물 체크리스트

- [ ] `ProjectApiConstants` 에 `*_RESOURCE_PLURAL` (필요하면 `*_RESOURCE_KIND`) 상수
- [ ] 해당 그릇의 규칙 (PM/PD 양쪽 분기 확인)
- [ ] (2-a) DTO·API·인포머·인덱서·watch·필터·RequestBuilder·`WorkloadResourceResolver`
- [ ] (2-b) `OwnershipPolicy.OWNED_TARGETS` + 웹훅 rules
- [ ] (3-d) 리졸버 + watch + RequestBuilder 게이트
- [ ] (7) 정적 매니페스트 + `aipub-installer` helm 차트
- [ ] 단위 테스트 (규칙 존재·resourceNames·verbs·빈 목록·역할 차등·정렬 멱등성)
- [ ] PR 본문: 왜 이 케이스인지, 의도적으로 주지 않은 verb(`list` 등)와 그 이유, 사람 확인 항목
