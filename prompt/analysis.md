# Analysis & POC: Repository Security & Quality Dashboard

## Background

We have an existing internal utility — an OpenShift monitoring dashboard used by our team. We want to extend it with a NEW feature: a single dashboard page that shows the security posture and code quality of every repository our team owns in GitLab.

Today, to check the security health of any repo, a person has to:

- Open GitLab
- Navigate to that specific repo
- Open the latest pipeline on the develop branch
- Click the "Security" tab
- Look at SAST, Container Scanning, Dependency Scanning, Secret Detection results
- Switch to the "Code Quality" tab to see SonarQube results
- Repeat for every other repo, one at a time

This is painful when we own many repositories. We want all of this information on ONE screen, refreshed automatically, so anyone can see the security and quality status across the whole portfolio at a glance.

## What we already have

- A GitLab instance where all our repos live
- A GitLab token (with read access) — we will provide the URL and token
- Each repo's GitLab CI pipeline already runs:
  - **Wiz** → results appear under "SAST" in GitLab's Security tab
  - **Prisma Cloud** → results appear under "Container Scanning"
  - **SonarQube** → results appear under "Code Quality"
  - Plus Dependency Scanning and Secret Detection
- Each scan produces a downloadable JSON report in GitLab (the "Download results" button on the Security tab)
- An existing Spring Boot + React dashboard deployed on OpenShift, which we will extend (not build from scratch)

## What we want the dashboard to do

A new page in the existing dashboard that, for EVERY repository our GitLab token can access:

1. Automatically finds the latest successful pipeline on the repo's default branch (usually `develop`)
2. Pulls all available security and code-quality reports from that pipeline
3. Summarizes the findings by severity (Critical, High, Medium, Low)
4. Shows ONE row per repository in a table:

   | Repo name | Group | Wiz (SAST) | Prisma (Container) | Dependency Scan | Secrets | SonarQube (Code Quality) | Last Scan |

5. Uses simple color coding so users can triage at a glance:
   - **Red** = has any Critical finding
   - **Amber** = has any High finding
   - **Green** = clean
6. Lets the user click any row to see the individual findings for that repo (same info GitLab's Security tab shows, but inside our dashboard)
7. Provides a link from each row back to the actual GitLab pipeline for that repo
8. Refreshes the data automatically in the background (every 30 minutes) so the page always loads instantly
9. Has a manual "Refresh now" button for users who want fresh data on demand
10. Shows useful filters: by group, by severity (e.g. show only repos with Critical findings), by scan age (e.g. repos not scanned in more than 7 days)

## Decisions already made (do not re-debate these)

- **Data source:** GitLab API ONLY. We do NOT integrate directly with Wiz, Prisma, or SonarQube. GitLab already aggregates all three.
- **Repo scope:** every repository the GitLab token can see (no manual repo list to maintain).
- **No database.** Cache results in memory and refresh on a schedule.
- **No history / no trend charts in the POC.** Just the current snapshot.
- **POC must work within the existing dashboard app** — same look and feel, same deployment path.

## What we need from you (the analysis / POC)

### Phase 1 — DISCOVERY (do this first, before building anything)

- Take ONE representative repo as a pilot. Confirm:
  - What exactly does the GitLab API return for that repo's latest pipeline?
  - What are the exact filenames of the security and code-quality reports?
  - What does the JSON inside those reports actually look like?
  - Which scanner name appears for Wiz, which for Prisma, which for Sonar?
  - What severity values do the reports use?
- Document findings in a short note (half a page is fine).

### Phase 2 — BUILD THE POC

- Extend the existing dashboard with the new page as described above.
- Make it work end-to-end against the real GitLab instance.
- Keep the scope tight — only what's in the "What we want the dashboard to do" list. No extras.

### Phase 3 — VERIFY

- Cross-check: for at least 5 repos, open GitLab side-by-side with our new dashboard and confirm the counts match exactly.
- Make sure the page loads in under 1 second once the cache is warm.
- Make sure one broken/unscanned repo does NOT break the whole page — other repos must still render.

## Expected outcome

By the end of the POC we should be able to:

- Open ONE page in our existing dashboard
- See a table of every repo with Wiz / Prisma / Dep / Secret / Sonar status, color-coded
- Spot the worst-offender repos in 2 seconds
- Click into any repo to see the actual findings
- Click out to GitLab for the full detail
- Trust that the data is at most 30 minutes old
- Demo it to leadership as the "single pane of glass" for repo health

## Inputs we will provide

- GitLab base URL
- GitLab access token (read scope, group-level)
- Access to the existing dashboard codebase (Spring Boot + React, deployed on OpenShift)
- One pilot repo name to use for the discovery phase

## Out of scope for the POC (explicitly NOT building yet)

- Historical trend charts / score-over-time
- Email / Slack alerts when a repo regresses
- User-level permissions or role-based access
- Direct integration with Wiz / Prisma / Sonar APIs
- Persistent storage / database
- Custom scoring formulas
- Exporting reports (CSV, PDF)

## Open questions to raise back

If anything in this brief is unclear, or if during discovery you find the GitLab reports don't contain what we expect, flag it immediately rather than guessing. Specifically check on:

- Whether `develop` is really the right branch for every repo, or if some use `main` / `master`
- Whether the GitLab token has access to ALL repos we care about, or if we need a wider-scoped token
- Whether any repos use a non-standard scanner setup that won't show up in GitLab's Security tab

Ship the POC. Then we'll iterate.
