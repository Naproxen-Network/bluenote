#!/usr/bin/env bash
# Push Little Blue Note to GitHub (requires a Personal Access Token).
# Usage:
#   export GH_TOKEN='ghp_your_token_here'
#   bash scripts/push_to_github.sh
set -e
cd "$(dirname "$0")/.."

if [ -z "$GH_TOKEN" ]; then
  echo "ERROR: Set GH_TOKEN to a GitHub Personal Access Token first."
  echo "Create one at: https://github.com/settings/tokens (scope: repo)"
  exit 1
fi

eval "$(/opt/homebrew/bin/brew shellenv)" 2>/dev/null || true

export GH_TOKEN
git remote set-url origin "https://x-access-token:${GH_TOKEN}@github.com/CJMmsz/blue_note.git"

if gh repo view CJMmsz/blue_note >/dev/null 2>&1; then
  echo "Repository exists. Pushing..."
  git push -u origin main
else
  echo "Repository CJMmsz/blue_note not found."
  echo "Create it first at https://github.com/new (owner: CJMmsz, name: blue_note, no README)."
  echo "Ensure this token has Contents: Read and write for that repo."
  exit 1
fi

echo "Done: https://github.com/CJMmsz/blue_note"
