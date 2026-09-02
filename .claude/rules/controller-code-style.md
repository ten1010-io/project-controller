# Controller Code Style

`aipub-backend` 의 `.claude/rules/backend-code-style.md` 와 같은 자리의 문서다. 공통 정책(Lombok·DI·java.time·로깅 레벨·PR 원칙)은 그쪽과 같고, 아래는 **이 리포에 실제로 적용되는 것만** 남긴 것이다. JPA·gRPC·QueryDSL·Cucumber 관련 항목은 이 리포에 해당 스택이 없어 옮기지 않았다.

## 주석

- **짧게.** 1줄 위주로 쓰고 배경을 여러 줄로 늘어놓지 않는다. 핵심 이유 한 줄이 장문보다 낫다.
- **주석에 Jira 키(`AIP-####`)를 넣지 않는다.** 티켓 추적은 커밋 메시지·PR·Jira 에서 한다.
- 주석은 한국어로 쓴다. 단 아래 "로깅" 의 메시지 문자열은 영어다.
- 설명할 값이 있는 건 *무엇을* 하는지가 아니라 *왜* 그렇게 했는지다. 특히 "이렇게 안 하면 무엇이 깨지는지".

## 로깅 / 사용자 노출 메시지

- **메시지 문자열은 영어로 쓴다** — `log.*`, CR `status.message`, k8s Event message, 예외 메시지 전부. 운영자가 `kubectl describe`·로그 grep 으로 읽기 때문이다. (워크스페이스 전역 규칙)
- Slf4j `@Slf4j` 사용. 주기 작업은 `LOG_PREFIX` 상수로 로그를 묶는다 (예: `[CV-CHILD-LABEL-SYNC]`).
- 레벨: `DEBUG` 상세 진단 / `INFO` 주요 흐름 / `WARN` 멈추지 않는 이상 / `ERROR` 즉각 대응 필요.
- 컨텍스트를 붙인다: `log.warn("{} Sync cycle failed after {}ms", LOG_PREFIX, elapsedMs, e)`.

## Java 컨벤션

- 들여쓰기 2칸. 임포트는 알파벳 순. 자동 포맷터(Spotless 등)는 **없다** — 주변 코드에 맞춘다.
- 필드 접근은 `this.` 를 붙인다 (기존 코드가 일관되게 그렇다).
- Lombok `@Getter`/`@Data`(DTO)/`@Slf4j`/`@RequiredArgsConstructor` 사용. 도메인 객체에 `@Setter` 지양.
- nullable 은 `org.jspecify.annotations.Nullable` 로 표기한다.
- 정적 헬퍼는 `abstract class XxxUtils` 에 모은다 (`K8sObjectUtils`, `NodeUtils`, `PersistentVolumeUtils`). 같은 헬퍼를 두 곳 이상에서 쓰게 되면 그때 Utils 로 올린다.
- enum 분기는 `switch` **식**으로 쓰고 `default` 를 두지 않는다 — enum 에 값이 추가되면 컴파일 에러로 잡히게 한다 (`ProjectRoleEnum` 분기 참고).
- `java.util.Date` 대신 `java.time`.
- 값이 없으면 `null`(또는 빈 컬렉션)로 둔다. `-`·`N/A`·`0` 같은 placeholder 로 채우지 않는다 — 표시 방식은 소비자(FE 등)가 정한다.

## 리컨실러

- **멱등성이 최우선이다.** 같은 입력에 두 번째 리컨실이 UPDATE 를 내면 안 된다.
- 리컨실 스킵 판정이 `List.equals` (순서 비교)인 경로가 있다. 따라서 룰·이름 목록을 만들 때 **정렬해서** 넣는다. 인포머 `list()` 순서는 보장되지 않으므로, 정렬을 빼면 매 주기 무의미한 UPDATE 가 돈다.
- 부분 정보로 파괴적 갱신을 하지 않는다. LIST 가 실패한 주기는 아무것도 patch 하지 않고 넘긴다.
- 새 리소스를 참조하려면 **인포머를 먼저 등록**한다. `SharedInformerFactory.getExistingSharedIndexInformer` 는 미등록 타입에 null 을 반환해 기동 시 NPE 가 된다.
- 컨트롤러에 watch 를 추가하면 `withReadyFunc` 도 같이 추가한다.
- `DefaultControllerWatch.onUpdate` 는 requestBuilder 를 old/new 양쪽에 적용해 합집합으로 큐잉한다 — 소유가 옮겨가는 변경에서 이전 소유자 쪽도 함께 갱신되는 근거다.

## RBAC

- 최소 권한. 클러스터 스코프 리소스는 가능하면 `resourceNames` 로 좁힌다.
- `list`/`watch`/`deletecollection` 은 `resourceNames` 로 좁혀지지 않는다. "걸러진 목록"이 필요하면 RBAC 로 풀 수 없는 요구다.
- 리컨실러가 만드는 ClusterRole 은 매 리컨실마다 룰을 전량 교체한다 — 코드에서 규칙을 지우면 클러스터에서도 사라진다. 정적 매니페스트는 그렇지 않으니, 회수 가능성이 필요하면 리컨실러 경로를 쓴다.
- 권한을 바꿨으면 `kubectl auth can-i ... --as=oidc:<유저>` 로 확인한다. 프론트의 권한 표시는 `UserAuthorityReview` 를 거치는 **별도 구현**이라 apiserver 판정과 어긋날 수 있다.

## 웹훅

- 어드미션 웹훅은 실패 시 워크로드 생성을 막는다. 인터셉트 대상을 넓힐 때는 시스템 컴포넌트가 만드는 오브젝트까지 걸리는지 확인한다.
- 라벨 낙인 대상 목록을 바꾸면 웹훅 `rules`(이 리포 + `aipub-installer` helm 차트)도 함께 갱신한다.

## 테스트

- 네이밍은 `<상황>_<기대>` 2단 (예: `ownerLabelChanged_enqueuesBothOwnerRoles`, `withUnsortedDuplicates_producesStableSortedNames`). backend 의 `givenX_whenY_thenZ` 는 이 리포 관례가 아니다.
- `@DisplayName` 은 필수가 아니다 (현재 24개 테스트 파일 중 14개만 사용). 메서드명으로 충분히 읽히면 생략한다.
- 인포머·인덱서가 필요한 로직은 순수 함수(`*Utils`, 룰 빌더)로 잘라내 단위 테스트한다. `SharedInformerFactory` 를 가짜로 세우려 하지 않는다.
- 멱등성을 테스트로 고정한다 — 같은 입력을 순서만 바꿔 두 번 넣고 결과가 같은지.
- 클러스터가 필요한 검증(`kubectl auth can-i`, 실제 리컨실 결과)은 단위 테스트로 흉내내지 말고 PR Test plan 의 사람 확인 항목으로 남긴다.
- 실행: `./gradlew compileJava compileTestJava test`. 단일 클래스는 `./gradlew :test --tests "*ClassName*"` — `common-*` 서브프로젝트에도 test 태스크가 있어 루트에 필터를 걸면 "No tests found" 로 실패한다.

## 커밋 / PR

- 커밋 메시지에 Jira 키를 포함한다 (주석 규칙과 별개 — 주석에는 넣지 않고 커밋에는 넣는다).
- 커밋 메시지에 `Co-Authored-By:` 트레일러, PR 본문에 `🤖 Generated with Claude Code` 푸터를 **넣지 않는다.**
- 최소 diff — 관련 없는 코드를 재포맷하지 않는다.
- 동작이 바뀌면 테스트를 추가/수정한다.
- 비자명한 결정·트레이드오프·하위 호환성 우려는 PR 본문에 적는다.
- `main`/`develop` 에 직접 커밋하지 않는다 (상세: `docs/branch-strategy.md`).
