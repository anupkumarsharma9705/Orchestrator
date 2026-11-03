# Launch Plan
## Java Companion Orchestrator - v1 (CLI MVP)

---

## 1. Launch Overview

### 1.1 Launch Goals
| Metric | Target | Measurement |
|--------|--------|-------------|
| **Public Release** | Tag `v1.0.0` on GitHub | GitHub Release |
| **First 100 Users** | Downloads + successful builds | GitHub Analytics + self-report |
| **First Paid Pilot** | 1 paying customer | Signed agreement + payment |
| **Community Feedback** | 10+ detailed reviews | GitHub Discussions / Discord |

### 1.2 Launch Timeline
| Phase | Duration | Key Milestone |
|-------|----------|---------------|
| **Pre-Launch** | Weeks 1-4 | Private beta (5-10 users) |
| **Soft Launch** | Weeks 5-8 | Public GitHub release, 100+ downloads |
| **Public Launch** | Week 9+ | Product Hunt, HN, Reddit, Java communities |

---

## 2. Pre-Launch Checklist (Gate 2 Criteria)

### 2.1 Product Readiness
- [ ] **Gate 2 passed**: Stranger installs JAR → completes project in <10 min
- [ ] `companion doctor` diagnoses all prerequisites correctly
- [ ] `companion verify <folder>` works on existing Spring Boot projects
- [ ] `companion fix <folder>` runs fix rounds on existing code
- [ ] All 5 artifact files generated correctly
- [ ] README.md complete with install, usage, config, troubleshooting

### 2.2 Distribution Readiness
- [ ] Executable JAR + native launchers (Windows `.exe`, macOS `.app`, Linux `.sh`)
- [ ] GitHub Release with artifacts attached
- [ ] Homebrew formula (optional, post-launch)
- [ ] Chocolatey / Scoop / AUR packages (community maintained)

### 2.3 Documentation
- [ ] **README.md**: Install, quickstart, commands, config, troubleshooting
- [ ] **docs/USER_GUIDE.md**: Full walkthrough with screenshots
- [ ] **docs/CONFIGURATION.md**: All config options, env vars, examples
- [ ] **docs/TROUBLESHOOTING.md**: Common errors + fixes
- [ ] **docs/FAQ.md**: Top 20 questions
- [ ] **docs/SECURITY.md**: Threat model, sandbox, data handling

### 2.4 Support Infrastructure
- [ ] GitHub Discussions enabled (Q&A)
- [ ] GitHub Issues templates (bug, feature, question)
- [ ] Discord/Slack community (optional, post-launch)
- [ ] `companion doctor` output includes support links

---

## 3. Pre-Launch Beta Program (Weeks 1-4)

### 4.1 Beta Recruitment (Target: 10-15 users)
| Channel | Target | Message |
|---------|--------|---------|
| Twitter/X Java community | 5 | "Building a CLI that verifies AI-generated Spring Boot code. Beta testers wanted." |
| r/java, r/SpringBoot | 3 | "Free audit/fix for your Spring Boot service" |
| Java Discord servers | 3 | Direct outreach to active members |
| Personal network | 4 | Direct messages to Java contacts |

### 4.2 Beta Onboarding
```
1. Invite email → Calendly link (30-min onboarding call)
2. Call: Walk through `companion generate` + `verify` + `fix`
3. Shared Notion page: known issues, feedback form, direct Slack/Discord
3. Weekly check-in (async): "What broke? What surprised you?"
```

### 4.3 Beta Success Criteria
| Metric | Target |
|--------|--------|
| **Activation** | 8/10 complete full generate → verify → fix cycle |
| **Success Rate** | 80%+ complete without human intervention |
| **Bug Reports** | < 5 critical, < 10 major |
| **NPS** | > 30 (survey at end) |
| **Retention** | 50%+ use again within 2 weeks |

---

## 4. Soft Launch (Weeks 5-8)

### 5.1 Public GitHub Release
```bash
# Tag and push
git tag -a v1.0.0 -m "v1.0.0 - First public release"
git push origin v1.0.0

# GitHub Actions builds release artifacts
# Manual: Edit release notes, attach JAR + launchers
```

### 5.2 Launch Channels & Messaging
| Channel | Audience | Hook |
|---------|----------|------|
| **Product Hunt** | Early adopters, devs | "AI that writes Spring Boot code AND verifies it compiles, tests, and passes security scans" |
| **Hacker News** | Technical audience | "Show HN: CLI that generates verified Spring Boot projects" |
| **Reddit** r/java, r/SpringBoot | Java devs | "Free CLI: generates Spring Boot REST APIs + auto-fixes security bugs" |
| **Twitter/X** | Java community | Thread: "How I built a tool that makes AI-generated Spring Boot code production-ready" |
| **Dev.to / Medium** | Developers | Article: "Why AI-generated code fails in production (and how I fixed it)" |
| **Java newsletters** (Java Weekly, Baeldung, etc.) | Subscribers | Sponsored/guest post |

### 5.3 Launch Assets
| Asset | Format | Owner |
|-------|--------|-------|
| **Demo GIF** (60s) | `docs/assets/demo.gif` | You |
| **Architecture Diagram** | `docs/ARCHITECTURE.md` | You |
| **Comparison Table** (vs Copilot, Cursor, Codeium) | `docs/COMPARISON.md` | You |
| **One-pager PDF** | `docs/ONE_PAGER.pdf` | You |
| **Sticker Design** | `assets/sticker.png` | Designer (later) |

---

## 5. Public Launch (Week 9+)

### 6.1 Launch Day Checklist
- [ ] GitHub Release published with artifacts
- [ ] All launch channels posted (PH, HN, Reddit, Twitter, Dev.to)
- [ ] Discord/Slack invite posted
- [ ] Analytics tracking (GitHub traffic, downloads, `companion doctor` runs)
- [ ] Support rotation schedule (you + 1 backup)
- [ ] Rollback plan: `git revert` + re-release previous tag

### 6.2 Launch Day Monitoring
| Metric | Tool | Alert Threshold |
|--------|------|-----------------|
| Downloads | GitHub Releases API | > 50 in 24h |
| Build Success Rate | Self-reported / GH Issues | > 80% |
| Critical Bugs | GitHub Issues | 0 critical |
| `companion doctor` runs | Anonymous telemetry (opt-in) | > 50 |
| Support Tickets | GitHub Issues | < 10/day |

### 6.2 Communication Templates

#### Launch Tweet Thread
```
1/ 🚀 Java Companion Orchestrator v1.0 is live!

A CLI that takes "REST CRUD for Book with JWT auth" → generates a Spring Boot project → compiles → runs tests → scans for security bugs → auto-fixes them → gives you an audit trail.

All in under 10 minutes. 

👇 Thread 🧵
```

#### Hacker News Submission
```
Title: Show HN: Java Companion - CLI that generates verified Spring Boot projects
URL: https://github.com/yourusername/java-companion
Text: Built this because AI writes code fast but it's often broken/insecure. This tool generates a Spring Boot project, then runs 7 scanners (Semgrep, Trivy, SpotBugs, etc.), auto-fixes issues in 5 rounds, and produces an audit trail your auditor will accept. Looking for feedback from Java devs!
```

---

## 6. Post-Launch Operations

### 7.1 Support Workflow
```
User Issue → GitHub Issue (auto-labeled) → Triage (P0-P3) → Fix → Release Patch
```
| Priority | SLA | Examples |
|----------|-----|----------|
| **P0 - Critical** | 4 hours | Data loss, security vuln, CLI crashes on startup |
| **P1 - High** | 24 hours | Build fails, verification false positives, config loss |
| **P2 - Medium** | 72 hours | Feature requests, minor bugs, doc typos |
| **P3 - Low** | Next release | Nice-to-haves, docs improvements |

### 7.2 Release Cadence
| Channel | Cadence | Process |
|---------|---------|---------|
| **Patch** (1.0.x) | As needed | Hotfix → tag → GitHub Release |
| **Minor** (1.x.0) | Monthly | Feature branch → PR → merge → tag |
| **Major** (x.0.0) | Quarterly | RFC → design doc → implementation |

### 7.3 Versioning
```
MAJOR.MINOR.PATCH
1.0.0  → 1.0.1 (hotfix) → 1.1.0 (feature) → 2.0.0 (breaking)
```
- **Breaking changes**: CLI flag changes, artifact format, config schema
- **Documentation**: `CHANGELOG.md` + GitHub Release notes

---

## 7.4 User Acquisition Funnel

| Stage | Metric | Target (Month 3) |
|-------|--------|------------------|
| **Awareness** | GitHub stars | 500 |
| **Consideration** | Clones/downloads | 2,000 |
| **Activation** | Successful `generate` | 500 |
| **Retention** | 2nd project within 30 days | 150 |
| **Referral** | Invites sent | 50 |
| **Revenue (Post-v1)** | Paid pilots → retainers | $5k MRR |

---

## 8. Metrics Dashboard (Grafana/Notion)

| Category | KPI | Target | Frequency |
|----------|-----|--------|-----------|
| **Adoption** | Weekly active users | 100 | Weekly |
| **Activation** | First-build success rate | > 80% | Daily |
| **Quality** | Build success rate (round 1) | > 90% | Per run |
| **Quality** | Tests pass rate (round 5) | > 85% | Per run |
| **Security** | HIGH/CRITICAL findings post-fix | 0 | Per run |
| **Cost** | Avg cost per project | < $0.50 | Per run |
| **Engagement** | Repeat usage (2nd project) | > 30% | Monthly |
| **Satisfaction** | NPS (quarterly survey) | > 30 | Quarterly |

---

## 9. Launch Budget (v1)

| Item | Cost | Notes |
|------|------|-------|
| GitHub Pro | $0 | Free for public repos |
| Docker Hub | $0 | Public images free |
| NVIDIA API | $0-50/mo | Free tier generous |
| Domain (java-companion.dev) | $12/yr | Namecheap |
| SSL Cert | $0 | Let's Encrypt |
| Monitoring (UptimeRobot) | $0 | Free tier |
| **Total** | **~$12/yr** | Extremely lean |

---

## 10. Post-Launch Roadmap (First 90 Days)

| Week | Focus | Deliverable |
|------|-------|-------------|
| 1-2 | Stabilize | Bug fixes, docs polish, first 10 users |
| 3-4 | Feedback Loop | User interviews, prioritize top 10 issues |
| 5-6 | v1.1 | Multi-provider (OpenRouter), `--offline` Ollama |
| 7-8 | v1.2 | `companion verify` on any folder, `companion fix` improvements |
| 9-10 | v1.3 | GitHub Action for CI integration |
| 11-12 | v1.4 | Cost dashboard, team workspaces (prep for v2) |

---

## 10. Rollback Plan

| Scenario | Trigger | Action |
|----------|---------|--------|
| **Critical Bug in Release** | P0 bug reported within 24h | `git revert` → tag previous → re-release |
| **Security Vulnerability** | CVE in dependency | Pin version → patch → emergency release |
| **Data Corruption** | SQLite DB corruption | Restore from backup (auto-daily) |
| **API Key Leak** | Key exposed in logs/issue | Rotate key → invalidate → re-release |

---

## 11. Communication Channels

| Channel | Purpose | Owner |
|---------|---------|-------|
| **GitHub Issues** | Bugs, features, questions | You |
| **GitHub Discussions** | Q&A, show-and-tell | You + Community |
| **Discord/Slack** | Real-time chat, beta coordination | You (later) |
| **Email Newsletter** | Monthly updates, tips | You (quarterly) |
| **Twitter/X** | Announcements, tips | You |

---

## 12. Legal & Compliance (Pre-Launch)

- [ ] **LICENSE** file (Apache 2.0 recommended)
- [ ] **CONTRIBUTING.md** with DCO sign-off
- [ ] **SECURITY.md** with responsible disclosure
- [ ] **CODE_OF_CONDUCT.md** (Contributor Covenant)
- [ ] **Privacy Policy** (if collecting any data)
- [ ] **Terms of Service** (if web app later)
- [ ] **Export Compliance** (encryption, US regulations)
- [ ] **Dependency License Check** (`./mvnw license:check`)

---

## 13. Launch Day Runbook

```
06:00  Final CI pass on main branch
06:30  Tag v1.0.0, push tag
07:00  GitHub Actions builds release artifacts
07:30  Verify JAR runs: `java -jar companion.jar --help`
08:00  Create GitHub Release with artifacts + release notes
08:30  Post to Product Hunt, Hacker News, Reddit, Twitter/X
09:00  Share in Java Discords, Slacks, newsletters
10:00  Monitor GitHub Issues, Discord, Twitter for feedback
12:00  Lunch break
14:00  Respond to first issues, triage P0/P1
18:00  End of day: tally metrics, plan Day 2
```

---

## 14. Success Definition (30 Days Post-Launch)

| Metric | Target | Stretch |
|--------|--------|---------|
| **GitHub Stars** | 200 | 500 |
| **Downloads** | 500 | 2,000 |
| **Successful Builds** | 200 | 1,000 |
| **Paid Pilots** | 2 | 5 |
| **Contributors** | 3 | 10 |
| **Blog Posts/Mentions** | 5 | 20 |

---

*Launch plan is a living document. Update weekly during launch window.*