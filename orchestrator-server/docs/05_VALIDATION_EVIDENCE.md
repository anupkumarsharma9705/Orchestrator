# Validation Evidence Register — Java Companion Orchestrator
## Live Evidence Tracking | Created: 2026-10-26 | Last Updated: 2026-10-26

---

## Evidence Register Instructions

**For every finding, record:**
- **Claim** — What you learned (specific, quotable)
- **Source** — Interview #, Experiment, Document, URL
- **Date** — YYYY-MM-DD
- **Evidence Type** — Primary (direct observation, interview, experiment) / Secondary (report, article, hearsay)
- **Confidence** — High / Medium / Low
- **Contradictions** — Any conflicting evidence
- **Outstanding Questions** — What remains unknown

**Evidence Quality Standards:**
- **Primary > Secondary** — Direct customer quotes beat market reports
- **Behavior > Intention** — "I did X" beats "I would do X"
- **Specific > General** — "Spent 6 hrs reviewing PR #234" beats "Review takes a while"
- **Recent > Historical** — Last 30 days beats "last year"
- **Negative evidence counts** — "They said no" is as valuable as "they said yes"

**Never fabricate:**
- ❌ Interview quotes you didn't hear
- ❌ Survey responses that didn't happen
- ❌ Signups that don't exist
- ❌ Payments that weren't made
- ❌ Conversion rates from non-existent funnels

---

## Evidence Entries

### Desk Research Evidence (Pre-Validation)

| # | Claim | Source | Date | Type | Confidence | Contradictions | Questions |
|---|-------|--------|------|------|------------|----------------|-----------|
| D1 | 40% of AI-generated programs have security vulnerabilities | NYU study (Pearce et al. 2022), cited in BSI/ANSSI report | 2022 | Secondary | High | GitHub claims Copilot improves code quality | Spring Boot specific rate unknown |
| D2 | Developers skip security review when AI code "looks clean" | Reddit r/ExperiencedDevs (u/minimal-salt, 90 pts, 2025-09-21) | 2025-09-21 | Secondary | Medium | Anecdotal; single team | How widespread? |
| D3 | AI PR velocity exceeds human review capacity | Reddit r/devops: 1.6M git events analysis (2026-03-12) | 2026-03-12 | Secondary | Medium | Analysis method not peer-reviewed | Magnitude of bottleneck? |
| D4 | BSI/ANSSI recommends flagging AI-generated code blocks | BSI/ANSSI joint report (2024) | 2024 | Secondary | High | Government guidance, not regulation | Enforcement timeline? |
| D5 | Virtue AI audit: all 4 AI tools (Copilot, Cursor, Codeium, Qodo) fail CWE categories | Virtue AI blog (2024-12-09) | 2024-12-09 | Secondary | Medium | Vendor marketing; methodology? | Spring Boot specific? |
| D6 | GitHub Copilot: 1.3M+ paid seats (2024) | GitHub Blog (2024-02-27) | 2024-02-27 | Secondary | High | Self-reported | Enterprise vs individual split? |
| D7 | Snyk AgentFix remediates >2/3 of supported alerts | GitHub Blog (2024-03-20) / Snyk Launch (2024-10) | 2024 | Secondary | Medium | "Supported alerts" qualifier | Unsupported alert types? |
| D8 | Checkmarx minimum deal $30K/yr; $59K+ typical | AWS Marketplace; Triotech comparison (2024) | 2024 | Secondary | High | Pricing varies | Mid-market pricing? |
| D9 | Semgrep Team: $30/contributor/mo per product (3 products = $90) | Semgrep pricing page (2024) | 2024 | Secondary | High | Startup discounts available | Actual avg contract size? |
| D10 | VibeRails: $19/mo or $299 lifetime; desktop app, BYOK | VibeRails website (2026) | 2026 | Secondary | High | New product; adoption unknown | Customer count? |
| D11 | Pinata: 1.8K weekly npm downloads; MIT license | npm registry (v0.6.0) | 2026 | Secondary | High | Downloads ≠ active users | Revenue? |
| D12 | Bootify: Free web generator; $15/mo Pro; MCP for agents | Bootify.io pricing (2026) | 2026 | Secondary | High | Web-only; no offline | User count? |
| D13 | JHipster: 20K+ generations/month (2015 claim); 22K+ stars | JHipster website; GitHub | 2015/2026 | Secondary | Medium | 2015 data stale | Current generation rate? |
| D14 | Developers report Copilot reads .env files by default | Reddit r/ClaudeAI (241 pts, 2025-06-21) | 2025-06-21 | Secondary | Medium | Specific to VS Code extension | Fixed in newer versions? |
| D15 | AI-generated code causes outages/security issues in businesses | TechRepublic article (2024-09-18); Reddit r/programming (784 pts) | 2024-09-18 | Secondary | Medium | Anecdotal compilation | Systematic data? |

---

## Interview Evidence (To Be Filled)

*Template — Duplicate for each interview*

| # | Claim | Source | Date | Type | Confidence | Contradictions | Questions |
|---|-------|--------|------|------|------------|----------------|-----------|
| I1 | [Quote or observation] | Interview #[N] — [Role, Company size, Industry] | YYYY-MM-DD | Primary | [High/Med/Low] | [Yes/No - detail] | [Follow-up needed] |
| I2 | [Quote or observation] | Interview #[N] | YYYY-MM-DD | Primary | | | |
| I3 | [Quote or observation] | Interview #[N] | YYYY-MM-DD | Primary | | | |

**Interview Log:**

| Interview # | Date | Role | Company Size | Industry | Duration | Recording? | Status |
|-------------|------|------|--------------|----------|----------|------------|--------|
| 1 | | | | | | | Scheduled |
| 2 | | | | | | | Scheduled |
| 3 | | | | | | | Scheduled |
| 4 | | | | | | | Scheduled |
| 5 | | | | | | | Scheduled |
| 6 | | | | | | | |
| 7 | | | | | | | |
| 8 | | | | | | | |
| 9 | | | | | | | |
| 10 | | | | | | | |

---

## Experiment Evidence (To Be Filled)

### E1: Usability Test

| # | Claim | Source | Date | Type | Confidence | Contradictions | Questions |
|---|-------|--------|------|------|------------|----------------|-----------|
| E1-1 | [Participant completed flow in X min] | E1 Session #[N] | YYYY-MM-DD | Primary | | | |
| E1-2 | [Participant confused at step X] | E1 Session #[N] | YYYY-MM-DD | Primary | | | |
| E1-3 | [SUS score: X; "Would use" rating: Y] | E1 Session #[N] | YYYY-MM-DD | Primary | | | |
| E1-4 | [Critical blocker: description] | E1 Session #[N] | YYYY-MM-DD | Primary | | | |

**E1 Session Log:**

| Session # | Date | Participant Role/Company | Completion Time | SUS Score | Would Use (1-5) | Critical Blockers | Status |
|-----------|------|--------------------------|-----------------|-----------|-----------------|-------------------|--------|
| 1 | | | | | | | Pending |
| 2 | | | | | | | Pending |
| 3 | | | | | | | Pending |
| 4 | | | | | | | Pending |
| 5 | | | | | | | Pending |

**E1 Summary (Auto-calculated after 5 sessions):**
- Avg completion time: —
- Avg SUS score: —
- "Would use" ≥4: —/5
- Critical blockers: —

---

### E2: Real Project Evaluation

| # | Claim | Source | Date | Type | Confidence | Contradictions | Questions |
|---|-------|--------|------|------|------------|----------------|-----------|
| E2-1 | [Project X: Initial findings: Y HIGH, Z CRITICAL] | E2 Repo #[N] | YYYY-MM-DD | Primary | | | |
| E2-2 | [Project X: After round 5: A HIGH, B CRITICAL remaining] | E2 Repo #[N] | YYYY-MM-DD | Primary | | | |
| E2-3 | [Project X: Regressions introduced: C new / D fixed] | E2 Repo #[N] | YYYY-MM-DD | Primary | | | |
| E2-4 | [Project X: Fix loop terminated / hung at round E] | E2 Repo #[N] | YYYY-MM-DD | Primary | | | |

**E2 Repo Log:**

| Repo # | Project | Type (CRUD/Business) | Initial HIGH/CRIT | Final HIGH/CRIT | Regressions | Rounds to Clean | Status |
|--------|---------|----------------------|-------------------|-----------------|-------------|-----------------|--------|
| 1 | Spring PetClinic | CRUD | | | | | Pending |
| 2 | JHipster Sample | CRUD | | | | | Pending |
| 3 | Bootify Sample | CRUD | | | | | Pending |
| 4 | Broadleaf Commerce | Business | | | | | Pending |
| 5 | Flowable | Business | | | | | Pending |
| 6 | Camunda | Business | | | | | Pending |
| 7 | Axon Framework | Business | | | | | Pending |
| 8 | Keycloak Extension | Business | | | | | Pending |
| 9 | Custom Repo A | TBD | | | | | Pending |
| 10 | Custom Repo B | TBD | | | | | Pending |

**E2 Summary (Auto-calculated after 10 repos):**
- Clean rate (0 HIGH/CRITICAL): —/10
- Avg regressions per project: —
- Fix loop hang rate: —/10

---

### E3: Cold Outreach

| # | Claim | Source | Date | Type | Confidence | Contradictions | Questions |
|---|-------|--------|------|------|------------|----------------|-----------|
| E3-1 | [DM sent to X; connection accepted / ignored] | E3 Prospect #[N] | YYYY-MM-DD | Primary | | | |
| E3-2 | [Reply received: "Interested in demo" / "Not interested" / "What's pricing?"] | E3 Prospect #[N] | YYYY-MM-DD | Primary | | | |
| E3-3 | [Demo booked for YYYY-MM-DD] | E3 Prospect #[N] | YYYY-MM-DD | Primary | | | |

**E3 Outreach Log:**

| Prospect # | Company | Role | Connection Sent | Accepted | Reply | Demo Booked | Notes | Status |
|------------|---------|------|-----------------|----------|-------|-------------|-------|--------|
| 1 | | | | | | | | Pending |
| 2 | | | | | | | | Pending |
| ... | | | | | | | | |
| 20 | | | | | | | | Pending |

**E3 Summary:**
- Connection rate: —/20 (—%)
- Reply rate: —/accepted (—%)
- Demos booked: —

---

### E4: Model Comparison

| # | Claim | Source | Date | Type | Confidence | Contradictions | Questions |
|---|-------|--------|------|------|------------|----------------|-----------|
| E4-1 | [Spec X: NVIDIA compile success Y/N; GPT-4o Y/N] | E4 Spec #[N] | YYYY-MM-DD | Primary | | | |
| E4-2 | [Spec X: NVIDIA cost $Y; GPT-4o cost $Z] | E4 Spec #[N] | YYYY-MM-DD | Primary | | | |
| E4-3 | [Spec X: NVIDIA findings: A HIGH, B CRITICAL; GPT-4o: C HIGH, D CRITICAL] | E4 Spec #[N] | YYYY-MM-DD | Primary | | | |

**E4 Spec Log:**

| Spec # | Category | NVIDIA Compile | GPT-4o Compile | NVIDIA Cost | GPT-4o Cost | NVIDIA Findings | GPT-4o Findings | Status |
|--------|----------|----------------|----------------|-------------|-------------|-----------------|-----------------|--------|
| 1 | CRUD | | | | | | | Pending |
| 2 | CRUD | | | | | | | Pending |
| 3 | CRUD | | | | | | | Pending |
| 4 | CRUD | | | | | | | Pending |
| 5 | CRUD | | | | | | | Pending |
| 6 | Auth+Rel | | | | | | | Pending |
| 7 | Auth+Rel | | | | | | | Pending |
| 8 | Auth+Rel | | | | | | | Pending |
| 9 | Biz Rule | | | | | | | Pending |
| 10 | Biz Rule | | | | | | | Pending |

**E4 Summary:**
- NVIDIA compile success rate: —%
- GPT-4o compile success rate: —%
- NVIDIA as % of GPT-4o: —%
- Avg cost/spec (NVIDIA): —
- Avg cost/spec (GPT-4o): —

---

### E5: Audit Trail Review

| # | Claim | Source | Date | Type | Confidence | Contradictions | Questions |
|---|-------|--------|------|------|------------|----------------|-----------|
| E5-1 | [Auditor 1: "This would satisfy SOC2 evidence requirement with minor changes"] | E5 Auditor #[N] | YYYY-MM-DD | Primary | | | |
| E5-2 | [Auditor 2: "Missing: timestamped reviewer signatures; need Jira integration"] | E5 Auditor #[N] | YYYY-MM-DD | Primary | | | |
| E5-3 | [Both agree on format change: add field X] | E5 Combined | YYYY-MM-DD | Primary | | | |

**E5 Auditor Log:**

| Auditor # | Background | Framework | Verdict | Required Changes | Status |
|-----------|------------|-----------|---------|------------------|--------|
| 1 | | | | | Pending |
| 2 | | | | | Pending |

---

### E6: Docker Survey

| # | Claim | Source | Date | Type | Confidence | Contradictions | Questions |
|---|-------|--------|------|------|------------|----------------|-----------|
| E6-1 | [Respondent: "Docker Desktop allowed"] | E6 Survey #[N] | YYYY-MM-DD | Primary | | | |
| E6-2 | [Respondent: "Podman only; Docker Desktop banned"] | E6 Survey #[N] | YYYY-MM-DD | Primary | | | |
| E6-3 | [Respondent: "No containers allowed; air-gapped"] | E6 Survey #[N] | YYYY-MM-DD | Primary | | | |

**E6 Survey Log:**

| Respondent # | Role | Company | Docker Desktop? | Alternative? | Notes | Status |
|--------------|------|---------|-----------------|--------------|-------|--------|
| 1 | | | | | | Pending |
| ... | | | | | | |
| 20 | | | | | | Pending |

**E6 Summary:**
- Docker Desktop allowed: —/20 (—%)
- Podman/Colima: —/20 (—%)
- No containers: —/20 (—%)

---

### E7: Pricing Validation

| # | Claim | Source | Date | Type | Confidence | Contradictions | Questions |
|---|-------|--------|------|------|------------|----------------|-----------|
| E7-1 | [Prospect: "Yes, $399/seat is reasonable for 4 hrs saved"] | E7 Interview #[N] | YYYY-MM-DD | Primary | | | |
| E7-2 | [Prospect: "Too expensive; we use free Semgrep"] | E7 Interview #[N] | YYYY-MM-DD | Primary | | | |
| E7-3 | [Prospect: "Need CTO approval >$200; process takes 30 days"] | E7 Interview #[N] | YYYY-MM-DD | Primary | | | |

**E7 Interview Log:**

| Interview # | Role | Company | Verdict | Approval Process | Objections | Status |
|-------------|------|---------|---------|------------------|------------|--------|
| 1 | | | | | | Pending |
| ... | | | | | | |
| 10 | | | | | | Pending |

**E7 Summary:**
- "Yes/Likely": —/10
- "Maybe": —/10
- "No/Too expensive": —/10
- Can describe approval: —/10

---

## Confirmed vs. Unvalidated Assumptions

| Assumption | Status | Evidence | Confidence | Next Action |
|------------|--------|----------|------------|-------------|
| **PA1: AI vuln rate >30%** | Desk research only | D1, D5, D15 | Medium | E2: Test on 10 real projects |
| **PA2: Review bottleneck >5 hrs/week** | Desk research only | D2, D3 | Medium | Interview Q5; E1 observation |
| **PA3: Audit fear drives purchase** | Desk research only | D4 | Medium | Interview Q11; E5 |
| **PA4: CLI UX acceptable** | Unvalidated | — | — | E1: 5 usability tests |
| **PA5: Fix loop 80% clean** | Unvalidated | — | — | E2: 10 real projects |
| **PA6: Audit trail format accepted** | Unvalidated | — | — | E5: 2 auditors |
| **PA7: Docker available** | Desk research weak | D13 (JHipster Heroku 50%) | Low | E6: 20 survey |
| **PA8: Budget authority $399** | Unvalidated | — | — | E7: 10 prospects; Interview Q17-20 |

---

## Validation Scorecard

| Assumption | Validated? | Evidence Count | Confidence | Blocking? |
|------------|------------|----------------|------------|-----------|
| PA1 | ❌ No | 3 desk | Medium | No — E2 will test |
| PA2 | ❌ No | 2 desk | Medium | No — Interviews will test |
| PA3 | ❌ No | 1 desk | Medium | No — E5 will test |
| PA4 | ❌ No | 0 | — | **YES — E1 required before Phase 2** |
| PA5 | ❌ No | 0 | — | **YES — E2 required before Gate 1** |
| PA6 | ❌ No | 0 | — | **YES — E5 required before Gate 1** |
| PA7 | ❌ No | 1 weak | Low | No — E6 will test |
| PA8 | ❌ No | 0 | — | No — E7 will test |

**Blocking Assumptions (must validate before next phase):**
1. **PA4 (CLI UX)** → Blocks Phase 2 CLI investment
2. **PA5 (Fix loop efficacy)** → Blocks Gate 1 (core promise)
3. **PA6 (Audit trail format)** → Blocks Gate 1 (enterprise sales)

---

## Evidence Quality Dashboard

| Metric | Target | Current | Status |
|--------|--------|---------|--------|
| Primary evidence entries | ≥20 | 0 | ⏳ Not started |
| Customer interviews completed | 10 | 0 | ⏳ Not started |
| Experiments completed | 7 | 0 | ⏳ Not started |
| Contradictions documented | — | 0 | — |
| Assumptions validated | 8 | 0 | ⏳ Not started |
| Blocking assumptions resolved | 3 | 0 | 🔴 Blocked |

---

## Evidence Collection Checklist

### Before Each Interview
- [ ] Recording permission obtained
- [ ] Script printed with probes
- [ ] Incentive prepared ($50 gift card)
- [ ] Calendly link sent
- [ ] Fresh VM/laptop ready (for E1)

### After Each Interview
- [ ] Recording saved (encrypted)
- [ ] Notes typed within 2 hours
- [ ] Quotes entered in evidence register
- [ ] Confidence scored
- [ ] Contradictions noted
- [ ] Follow-up questions identified

### After Each Experiment
- [ ] Raw data saved (CSV/JSON)
- [ ] Summary metrics calculated
- [ ] Evidence entries created
- [ ] Go/No-Go decision documented
- [ ] Next actions assigned

### Weekly Review (Every Friday)
- [ ] Evidence register updated
- [ ] Scorecard refreshed
- [ ] Blocking assumptions reassessed
- [ ] Next week's experiments prioritized
- [ ] Stakeholder update sent (if applicable)

---

## Change Log

| Date | Change | Author |
|------|--------|--------|
| 2026-10-26 | Created evidence register with desk research entries | Auto-generated |
| 2026-10-26 | Added interview/experiment templates | Auto-generated |

---

*This register is the single source of truth for validation evidence. Update in real-time. No experiment is complete until evidence is recorded here.*