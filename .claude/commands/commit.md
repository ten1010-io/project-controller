---
description: 한국어 Conventional Commits 메시지 초안 작성 후 커밋
---

현재 스테이징/워킹 트리 변경사항을 기반으로 한국어 [Conventional Commits v1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) 규격에 맞는 커밋 메시지를 작성하고 커밋합니다.

## 절차

1. 병렬로 실행:
   - `git status` (변경 파일 확인, `-uall` 금지)
   - `git diff --staged` + `git diff` (실제 변경 내용 파악)
   - `git log --oneline -20` (이 저장소의 메시지 스타일 파악)
   - `git branch --show-current` (브랜치명 — Jira 티켓 ID 추출용)
2. 변경 내용을 분석해서 메시지 초안 작성
3. 사용자에게 초안을 보여주고 확인받기 — **확인 전에는 절대 커밋하지 말 것**
4. 확인되면 HEREDOC 으로 커밋

## 메시지 규격

```
<type>: <한국어 제목>

[optional body]

[optional footer(s)]
```

**type (필수)**
- `feat` — 새 기능
- `fix` — 버그 수정
- `chore` — 그 외 유지보수
- `style` — 포맷/세미콜론 등 동작 무관
- `refactor` — 동작 유지하면서 구조 개선
- `docs` — 문서만 변경
- `test` — 테스트 추가/수정
- `setting` — 설정/환경 변경

**제목 (한국어)**
- **한국어** 로 작성
- **50자 이내**
- **마침표 없음**
- **동사로 마무리** (예: "추가", "수정", "제거", "변경")
- 소문자/대문자 구분 없음 (한국어)
- "왜" 가 드러나도록 — "what" 은 diff 로 이미 보임

**예시 (저장소 기존 스타일)**
```
feat: 사용자 즐겨찾기 gRPC API 추가
fix: SSE 알림 스트림 타임아웃 시 emitter 미완료 현상 수정
fix(gateway): access log level/logger/type 를 최상위 JSON 필드로 평탄화
refactor: ImageHub-Harbor DB 이중 저장 제거
```

**body (선택)**
- 제목과 한 줄 공백 후 작성
- 변경의 동기와 이전 동작과의 차이를 한국어로 설명
- 한 줄 80자 이내

**footer**
- **JIRA 키 포함 (권장)** — 브랜치명(`feat/AIP-1234-...`, `fix/AIP-2465` 등)에서 `AIP-\d+` 를 정규식으로 추출해
  footer 에 `Refs: AIP-1234` 또는 `Closes: AIP-1234` 로 넣는다. 브랜치에 없으면 본문/사용자에게 확인해 채운다.
- **Co-Authored-By 트레일러 (필수)** — 커밋 메시지 맨 끝(빈 줄 뒤)에 아래 트레일러를 붙인다:
  ```
  Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
  ```
- BREAKING CHANGE 가 있으면 type 뒤에 `!` 또는 footer 에 `BREAKING CHANGE: <설명>`

## 규칙

- 한 번에 하나의 논리적 변경만 커밋 (여러 개면 사용자에게 분리 제안)
- `.env`, 크리덴셜/키 파일이 스테이징되어 있으면 **경고하고 중단**
- `git add -A` / `git add .` 지양 — 파일명 명시 (서브에이전트 진단용 임시파일 등이 휩쓸려 커밋 오염된 사례 있음)
- 훅 우회 (`--no-verify`) 금지 — 훅이 실패하면 원인을 고친 뒤 재커밋
- 이미 커밋된 것 수정 (`--amend`) 금지 — 새 커밋 생성
- **브랜치 전략** — `main`/`develop` 에 직접 커밋 지양. 신규 작업은 `feature/*`·`feat/*`·`fix/*`·`chore/*`·`hotfix/*`
  브랜치에서 커밋한다. 현재 브랜치가 `main`/`develop` 이면 브랜치 생성을 권고하고 커밋 전에 확인받는다.
  (상세: `docs/branch-strategy.md`)
- **커밋 전 컨벤션 확인** — 이 리포에는 자동 포맷터(Spotless 등)가 없다. 주변 코드의 들여쓰기·임포트
  순서·주석 밀도를 맞춘다 (상세: `.claude/rules/controller-code-style.md`).
- 브랜치명에서 `AIP-\d+` 추출 → footer `Refs: AIP-1234` 자동 추가 제안

$ARGUMENTS
