---
name: GitHub workflow
description: Use git and the GitHub API/MCP server to clone, branch, commit, push and open PRs.
---

# GitHub workflow

Prefer the GitHub MCP server (Settings > MCP > GitHub) when it is connected: it exposes
repository, issue and pull-request tools without shelling out.

## With git only
```
git clone https://github.com/OWNER/REPO.git
cd REPO
git checkout -b feature/my-change
git add -A && git commit -m "Describe the change"
git push -u origin feature/my-change
```

Never commit secrets. Add `.env`, keystores and tokens to `.gitignore` first.

## With the API
```
curl -s -H "Authorization: Bearer $GITHUB_TOKEN" \
     -H "Accept: application/vnd.github+json" \
     https://api.github.com/user/repos
```

## Pull requests
Use the API or MCP tool, not the web UI:
```
gh pr create --title "..." --body "..."   # if gh is installed
```

Always ask before pushing to `main` or force-pushing.
