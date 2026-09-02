---
description: 대상 파일/변경에 대한 테스트를 작성·실행·자기점검 (Java/JUnit, qa-validator)
---

대상 파일(또는 현재 변경)에 대한 JUnit 테스트를 작성하고, 실행해 통과를 확인하고, 컨벤션을 자기점검합니다.
규칙 출처는 `.claude/rules/backend-code-style.md` 의 **테스트 전략** 절.

## 절차

1. 병렬로 실행:
   - `git status` — 변경 파일 파악
   - `git diff --name-only` — 변경 목록
2. **대상 식별**
   - 인자 있음 → 그 경로(들).
   - 인자 없음 → 변경된 `*/src/main/java` 클래스 중 대응 테스트가 없는 것. (`*/src/test/java` 에 대응 `*Test` 부재)
3. **테스트 종류·위치 결정**
   - 단위 테스트(기본, 90% 이상) — Mockito 로 POJO 단위 테스트. `GrpcService` 구현체는 스텁/옵저버를 Mock 하여 일반 Java 객체로 테스트.
   - 슬라이스(`@DataJpaTest`/`@WebMvcTest`) 또는 통합(`@SpringBootTest`, Testcontainers K3s/ES)은 꼭 필요할 때만.
   - 위치: 소스와 같은 패키지의 `<module>/src/test/java/...` 에 `<대상>Test.java`. 기존 테스트가 있으면 보강 대상으로 표시.
4. **테스트 작성** — `qa-validator` 에이전트(또는 `project-controller-dev` 하네스의 implementer)에게 대상·요구 커버리지(happy-path + 엣지) 전달. 아래 컨벤션을 강제:
   - 모든 테스트 메서드에 `@DisplayName` **필수**.
   - 네이밍: `givenCondition_whenAction_thenExpectedResult`.
   - 리컨실 결과 검증은 **멱등성**까지 포함 — 같은 입력으로 두 번 돌려 결과가 동일한지.
   - 인포머/인덱서가 필요한 대상은 순수 함수(`*Utils`, `Reconciliation*` 의 룰 빌더)로 잘라내 단위 테스트한다.
5. **실행** — 통과 확인. 단일 프로젝트라 모듈 접두사가 없다:
   ```
   ./gradlew compileTestJava test
   ```
   단일 클래스만: `./gradlew :test --tests "*ClassName*"` — `:test` 로 루트만 지정한다
   (`common-*` 서브프로젝트에도 test 태스크가 있어 루트에 필터를 걸면 "No tests found" 로 실패).
6. **자기점검** — 단언 0 / try-catch 로 예외 흡수 / 과도한 Mock / `@DisplayName` 누락 / 네이밍 위반이 없는지 확인.
7. **정리** — 표로: 작성/보강 파일 · 종류(단위/슬라이스/통합) · 실행 결과 · 스킵한 파일.

## 규칙

- 기존 통과 테스트를 깨뜨리지 않는다 (보강은 추가 위주).
- happy-path 만으론 불충분 — 엣지(예외·경계값) 필수.
- 클러스터가 필요한 검증(실제 리컨실 결과, `kubectl auth can-i`)은 단위 테스트로 흉내내지 말고 PR Test plan 의 사람 확인 항목으로 남긴다.
- 전체 교차검증(빌드·정합성·경계면)은 범위 밖 — `/review` 로.

## 인자

- 인자 없음 → 현재 변경된 `*/src/main/java` 중 테스트 미동반분.
- `src/main/java/io/ten1010/aipub/projectcontroller/.../FooReconciler.java` → 해당 파일.

$ARGUMENTS
