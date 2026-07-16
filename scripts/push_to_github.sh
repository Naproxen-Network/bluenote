#!/usr/bin/env bash
# Usage: bash scripts/push_to_github.sh owner/repository [private|public]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

REPOSITORY="${1:-${GITHUB_REPOSITORY:-}}"
VISIBILITY="${2:-private}"

if [[ ! "$REPOSITORY" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]]; then
  echo "Usage: bash scripts/push_to_github.sh owner/repository [private|public]"
  exit 1
fi
if [[ "$VISIBILITY" != "private" && "$VISIBILITY" != "public" ]]; then
  echo "Visibility must be private or public."
  exit 1
fi
command -v git >/dev/null || { echo "Git is required."; exit 1; }
command -v gh >/dev/null || { echo "GitHub CLI is required: https://cli.github.com/"; exit 1; }
git rev-parse --is-inside-work-tree >/dev/null
git rev-parse --verify HEAD >/dev/null || { echo "Create the first commit before pushing."; exit 1; }
gh auth status >/dev/null

git branch -M main
if ! gh repo view "$REPOSITORY" >/dev/null 2>&1; then
  gh repo create "$REPOSITORY" "--$VISIBILITY"
fi

REMOTE_URL="https://github.com/${REPOSITORY}.git"
if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "$REMOTE_URL"
else
  git remote add origin "$REMOTE_URL"
fi

git push -u origin main
echo "Done: https://github.com/${REPOSITORY}"
