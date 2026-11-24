---
name: Playwright browser
description: Drive a real browser through the Playwright MCP server for testing and scraping.
---

# Playwright browser

The Playwright MCP server runs a headless Chromium and exposes tools such as
`browser_navigate`, `browser_click`, `browser_type`, `browser_snapshot` and
`browser_take_screenshot`.

## Setup
Add the preset in Settings > MCP > Playwright. It runs:
```
npx -y @playwright/mcp@latest
```
That needs Node.js inside the Linux userland. Install Chromium once:
```
npx playwright install chromium
```

## Working style
1. Navigate, then take an accessibility snapshot before clicking. Snapshots are cheaper
   and more reliable than screenshots for finding elements.
2. Re-snapshot after every interaction; element refs change.
3. Use screenshots only to confirm visual layout.
4. Never submit forms or create accounts unless the user asked for it.
