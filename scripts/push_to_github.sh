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

echo "$GH_TOKEN" | gh auth login --with-token
gh auth status

# Create repo if it does not exist, then push
if gh repo view CJMmsz/blue_note >/dev/null 2>&1; then
  echo "Repository exists. Pushing..."
  git push -u origin main
else
  echo "Creating repository and pushing..."
  gh repo create CJMmsz/blue_note --public --source=. --remote=origin --push
fi

echo "Done: https://github.com/CJMmsz/blue_note"
