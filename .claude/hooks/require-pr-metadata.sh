#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Repo convention — require, before opening a PR:
#   1. "## 🚀 Release Version" with an actual version (not the template example)
#   2. for large diffs, "## 🔍 여기만은 꼭 봐주세요" with actual content — a
#      reviewer can't read the whole diff, so the author has to say where to
#      look first
#
# Event : PreToolUse (matcher: Bash). Denies via permissionDecision:"deny".
# Deps  : none beyond git (pure grep/awk/sed, no jq) so it runs everywhere,
#         including Git Bash on Windows. Fails OPEN — anything it cannot
#         confidently read is allowed through, because a missed check is
#         cheap and a false block is not.
#
# Why   : in aipub-backend this complements pr-auto.yml's `milestone` /
#         `review-focus` jobs, which fail the PR's checks after creation for
#         the same reasons. THIS repo has no pr-auto.yml yet (no
#         `secrets.SECRET`), so for now the hook is the ONLY enforcement of
#         those two conventions — and it only covers PRs authored through
#         Claude Code. A teammate opening a PR by hand (web UI / bare
#         `gh pr create`) is not checked at all until pr-auto.yml lands.
#
# Scope : inspects ONLY `gh pr create`. Skips --web (body is written/reviewed
#         in the browser form) and --dry-run (creates nothing). `gh pr edit`
#         is untouched — that's the documented way to fix a PR already open.
# ─────────────────────────────────────────────────────────────────────────────
input=$(cat 2>/dev/null)

command=$(printf '%s' "$input" | grep -o '"command"[[:space:]]*:[[:space:]]*"\([^"\\]\|\\.\)*"' | head -1)

# Fast exit: only inspect `gh pr create`.
printf '%s' "$command" | grep -Eq 'gh'                   || exit 0
printf '%s' "$command" | grep -Eq 'pr[[:space:]]+create' || exit 0

# Browser flow reviews the body in the form; dry-run creates nothing.
printf '%s' "$command" | grep -Eq -- '(--web|--dry-run)([^A-Za-z-]|$)' && exit 0
printf '%s' "$command" | grep -Eq -- '(^|[[:space:]])-[A-Za-z]*w([[:space:]]|\\?"|$)' && exit 0

# Pull the PR body out — our own /pr skill always uses --body-file (raw
# markdown written to a temp file), so that's the primary, reliable path.
# --body "..." inline is a secondary best-effort path. If neither flag is
# present, gh opens an editor pre-filled from PULL_REQUEST_TEMPLATE.md for
# interactive review — skip (fail open), we can't inspect that content here.
have_body=false
body=""

body_file=$(printf '%s' "$command" \
  | grep -oE -- '--body-file[[:space:]=]+"?[^[:space:]"]+"?' | head -1 \
  | sed -E 's/--body-file[[:space:]=]+//; s/^"//; s/"$//')
if [ -n "$body_file" ] && [ -f "$body_file" ]; then
  body=$(cat "$body_file" 2>/dev/null)
  have_body=true
fi

if [ "$have_body" = false ]; then
  inline=$(printf '%s' "$command" | grep -oE -- '--body[[:space:]=]+"([^"\\]|\\.)*"' | head -1)
  if [ -n "$inline" ]; then
    body=$(printf '%s' "$inline" \
      | sed -E 's/^--body[[:space:]=]+"//; s/"$//' \
      | sed 's/\\n/\n/g; s/\\"/"/g')
    have_body=true
  fi
fi

[ "$have_body" = true ] || exit 0

missing=""

# ── 1. Release Version ────────────────────────────────────────────────────
# 섹션 본문만 취한다. 인용(>) 줄은 템플릿 안내문(예: v5.1.0)이라 제외 —
# pr-auto.yml의 milestone job과 동일한 판정 로직.
version=$(printf '%s' "$body" | tr -d '\r' \
  | awk '/^##[[:space:]].*Release Version/{f=1;next} /^##[[:space:]]/{f=0} f' \
  | grep -v '^[[:space:]]*>' \
  | grep -oiE 'v?[0-9]+\.[0-9]+(\.[0-9]+)?' | head -1)
[ -n "$version" ] || missing="Release Version(예: v5.1.0)"

# ── 2. 여기만은 꼭 봐주세요 (대형 PR만) ─────────────────────────────────────
# base 대비 diff 규모를 로컬 git 으로 추정 — PR 이 아직 없으니 GH API 대신 git diff.
# 임계값은 pr-auto.yml review-focus job 과 동일: 파일 10개 초과 또는 300줄 초과.
base=$(printf '%s' "$command" \
  | grep -oE -- '--base[[:space:]=]+"?[A-Za-z0-9._/-]+"?' | head -1 \
  | sed -E 's/--base[[:space:]=]+//; s/^"//; s/"$//')
base=${base:-develop}

cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || true

changed_files=0
total=0
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  stat=$(git diff --shortstat "origin/$base...HEAD" 2>/dev/null || true)
  changed_files=$(printf '%s' "$stat" | grep -oE '[0-9]+ file' | grep -oE '[0-9]+' | head -1)
  insertions=$(printf '%s' "$stat" | grep -oE '[0-9]+ insertion' | grep -oE '[0-9]+' | head -1)
  deletions=$(printf '%s' "$stat" | grep -oE '[0-9]+ deletion' | grep -oE '[0-9]+' | head -1)
  changed_files=${changed_files:-0}
  total=$(( ${insertions:-0} + ${deletions:-0} ))
fi

if [ "$changed_files" -gt 10 ] || [ "$total" -gt 300 ]; then
  section="$(printf '%s' "$body" | tr -d '\r' \
    | awk '/^##[[:space:]].*여기만은 꼭 봐주세요/{f=1;next} /^##[[:space:]]/{f=0} f' \
    | grep -v '<!--' \
    | grep -v '^[[:space:]]*-[[:space:]]*$' \
    | grep -v '^[[:space:]]*$' || true)"
  [ -n "$section" ] || missing="${missing:+$missing, }여기만은 꼭 봐주세요(대형 PR — 파일 ${changed_files}개/${total}줄, 리뷰어가 우선 봐야 할 지점)"
fi

[ -n "$missing" ] || exit 0

printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"[repo convention] PR 본문에 %s 누락 — .github/PULL_REQUEST_TEMPLATE.md 참고. 규칙 위치: .claude/hooks/require-pr-metadata.sh"}}\n' "$missing"
exit 0
