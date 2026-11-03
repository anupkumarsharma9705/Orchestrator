# Revenue Validation Plan — Java Companion Orchestrator
## First-Customer Experiments & Evidence Tracking | Created: 2026-10-27

---

## 1. Revenue Validation Philosophy

**Principle:** *Interest ≠ Commitment ≠ Revenue*

| Level | Signal | Evidence Required | Weight |
|-------|--------|-------------------|--------|
| **Interest** | "Cool tool!" "I'd use that!" | Verbal/written enthusiasm | 0% — Noise |
| **Engagement** | Demo request, waitlist signup, GitHub star | Action requiring time | 10% — Leading indicator |
| **Qualification** | Pilot application, detailed requirements doc | Effort + specific need | 30% — Strong signal |
| **Financial Commitment** | Paid pilot deposit, pre-order, signed LOI | Money at risk | 70% — Validation |
| **Revenue** | Cash in bank, signed contract, invoice paid | Bank statement | 100% — Truth |

**Rule:** Only Levels 3-5 count toward validation. Levels 1-2 inform product but don't validate business model.

---

## 2. Revenue Validation Experiments

### Experiment R1: Paid Pilot Offer ("5-Day Audit/Fix Service")

| Element | Specification |
|---------|---------------|
| **Hypothesis** | Fintech tech leads will pay $2,500 for a 5-day service: "I run my verification pipeline on your AI-generated Spring Boot service, fix critical findings, deliver audit trail. Refund if no HIGH/CRITICAL issues found." |
| **Target Audience** | 5 Java Tech Leads at fintech/insurtech (10-50 engineers) with recent AI security incident or audit finding |
| **Recruitment Channel** | LinkedIn direct outreach (from E3 list); personalized DM referencing their company/tech stack |
| **Offer Details** | • Price: $2,500 (50% upfront, 50% on delivery)<br>• Scope: 1 Spring Boot service/repo<br>• Deliverables: `fixes.md`, `vulnerabilities.md`, `report.md`, remediated code<br>• Guarantee: Full refund if 0 HIGH/CRITICAL findings fixed<br>• Timeline: 5 business days from repo access |
| **Execution Steps** | 1. Identify 20 prospects (LinkedIn: "Java Tech Lead" + fintech + Spring Boot)<br>2. Send 5 personalized DMs/day for 4 days (20 total)<br>3. Book 15-min discovery calls with responders<br>4. Qualify: Has AI-generated code? Has security audit pressure?<br>5. Close: Send Stripe Payment Link for $1,250 deposit<br>6. Execute: Manual engine run + expert fixes (founder)<br>7. Deliver: Artifacts + debrief call<br>8. Collect: Feedback on `fixes.md` format; request case study permission |
| **Estimated Cost** | $0 cash + 20 hrs founder time (outreach, calls, execution) |
| **Sample Size** | 20 DMs → Target 5 calls → Target 1 paid pilot |
| **Limitations** | Manual service ≠ product; founder bias in execution; small n |
| **Success Criteria** | **Minimum:** 1 paid pilot ($2,500 revenue)<br>**Target:** 3 paid pilots ($7,500 revenue + 3 case studies)<br>**Stretch:** 5 paid pilots ($12,500 + referenceable customers) |
| **Failure Criteria** | 0 paid pilots after 20 DMs + 5 calls |
| **Next Decision** | **Pass:** Proceed to R2 + R3; build CLI wrapper for pilots<br>**Fail:** Pivot to verification-only service; lower price; partner channel |

---

### Experiment R2: Lifetime License Pre-Sale

| Element | Specification |
|---------|---------------|
| **Hypothesis** | Solo Java developers and agency owners will pre-purchase a $1,199 lifetime license for "Java Companion — lifetime access, 3 years updates, ships Q1 2027" |
| **Target Audience** | Solo Java devs, freelancers, small agency owners (1-5 people) active on Reddit, Discord, Twitter |
| **Recruitment Channel** | • Reddit: r/java, r/SpringBoot, r/freelance (technical post + comment)<br>• Java Discord: #tools, #spring-boot, #freelance<br>• Twitter/X: Thread with demo GIF<br>• Newsletter: "Java Tools Weekly" style (if exists) |
| **Offer Details** | • Price: $1,199 (one-time)<br>• Includes: All future v1.x updates, v2.0 major upgrade, offline mode, lifetime license<br>• Delivery: Q1 2027 (conservative); early beta access Q4 2026<br>• Refund: 30-day money-back after delivery<br>• Payment: Stripe Checkout (immediate charge) |
| **Landing Page** | Simple Carrd/Notion page with:<br>• Hero: "Lifetime license for Java Companion — $1,199"<br>• Demo GIF (60 sec)<br>• Feature list (generation + verification + fix loop + audit trail)<br>• FAQ: "What if v2 is a different product?" → "v2.0 included"<br>• Stripe Checkout button |
| **Execution Steps** | 1. Build landing page (2 hrs)<br>2. Create demo GIF (1 hr)<br>3. Post to Reddit (1 post, engage in comments 2 hrs)<br>4. Share in Discord (3 communities, 1 hr)<br>5. Tweet thread (30 min)<br>6. Track: Visitors → Checkout started → Completed |
| **Estimated Cost** | $50 (Carrd Pro + domain) + 5 hrs founder time |
| **Sample Size** | Target: 1,000 visitors → 30 checkouts → 3 purchases |
| **Limitations** | Pre-sale for unreleased product; refund risk; reputation risk if delayed |
| **Success Criteria** | **Minimum:** 3 pre-orders ($3,597)<br>**Target:** 10 pre-orders ($11,990)<br>**Stretch:** 25 pre-orders ($29,975) |
| **Failure Criteria** | 0 pre-orders after 1,000 visitors |
| **Next Decision** | **Pass:** Validates solo/agency segment; funds 3 months dev<br>**Fail:** Solo segment not willing to prepay; focus on team annual licenses |

---

### Experiment R3: Pilot-to-Annual Conversion

| Element | Specification |
|---------|---------------|
| **Hypothesis** | Paid pilot customers will convert to $399/seat/yr annual licenses, crediting pilot fee |
| **Target Audience** | R1 pilot customers (3-5 companies) |
| **Offer Details** | • "Convert to annual license: $399/seat/yr, credit your $2,500 pilot fee"<br>• Example: 5-seat team = $1,995/yr - $2,500 credit = $0 year 1, $1,995 year 2<br>• Includes: All pilot features + CLI access + `companion verify` + `companion fix`<br>• Contract: 1-year, auto-renew, cancel anytime |
| **Execution Steps** | 1. After pilot delivery + debrief (Week 5-6)<br>2. Send proposal: "Pilot results: X findings fixed. Want to run this on every AI PR?"<br>3. Propose conversion with pilot credit<br>4. Send Stripe Subscription link or manual invoice<br>5. Onboard: License key, CLI download, team setup call |
| **Estimated Cost** | 2 hrs founder time per pilot |
| **Sample Size** | 3-5 pilots from R1 |
| **Success Criteria** | **Minimum:** 2/3 convert ($2,394 ARR)<br>**Target:** 3/3 convert ($3,591 ARR)<br>**Stretch:** 5/5 convert + seat expansion ($6,000+ ARR) |
| **Failure Criteria** | 0/3 convert |
| **Next Decision** | **Pass:** Annual model validated; recurring revenue established<br>**Fail:** Pilots see value but won't pay recurring; test usage-based or lower price |

---

### Experiment R4: Team Pack Validation (5-Seat)

| Element | Specification |
|---------|---------------|
| **Hypothesis** | Growing teams (5-10 engineers) will buy 5-seat pack at $1,599/yr (20% discount) |
| **Target Audience** | Engineering Managers at 10-30 engineer companies; Spring Boot teams adopting AI |
| **Recruitment Channel** | Referrals from R1/R3 pilots; LinkedIn "Engineering Manager" + "Spring Boot" |
| **Offer Details** | • Price: $1,599/yr for 5 seats ($320/seat vs $399)<br>• Single license key; seat management via CLI<br>• Includes: All Pro features + team usage dashboard (v1.1) |
| **Execution Steps** | 1. Ask R1/R3 pilots: "Know any EMs at other companies who need this?"<br>2. Intro call → Demo → Proposal<br>3. Close: Stripe Subscription for $1,599/yr |
| **Sample Size** | 5 intros → Target 2 closes |
| **Success Criteria** | **Minimum:** 1 Team Pack sale ($1,599 ARR)<br>**Target:** 3 Team Packs ($4,797 ARR) |
| **Next Decision** | **Pass:** Volume discount model works; builds expansion revenue<br>**Fail:** Teams prefer per-seat; simplify pricing |

---

## 3. Validation Timeline (12 Weeks)

```
WEEK 1-2:     R1 OUTREACH + R2 LANDING PAGE
├── Mon: Build R2 landing page + demo GIF
├── Tue: Launch R2 (Reddit, Discord, Twitter)
├── Wed-Fri: R1 LinkedIn DMs (5/day × 4 days = 20)
├── Weekend: Follow up R1 replies; book calls

WEEK 3-4:     R1 DISCOVERY CALLS + R2 TRAFFIC
├── Mon-Wed: 5 discovery calls (30 min each)
├── Thu-Fri: Qualify → Send Stripe links for $1,250 deposits
├── R2: Monitor traffic; engage comments; tweak copy

WEEK 5-6:     R1 PILOT EXECUTION + R3 CONVERSION PREP
├── Mon-Wed: Execute 1-3 pilots (manual engine run)
├── Thu-Fri: Deliver artifacts; debrief calls
├── R3: Send conversion proposals with pilot credit

WEEK 7-8:     R1 MORE PILOTS + R3 CLOSES + R4 OUTREACH
├── Execute remaining pilots
├── Close R3 conversions
├── R4: Ask for referrals to Engineering Managers

WEEK 9-10:    R4 TEAM PACK OUTREACH
├── Intro calls with EMs
├── Demos → Proposals → Closes

WEEK 11-12:   REVENUE VALIDATION REVIEW
├── Tally: Cash collected, ARR signed, lessons learned
├── Go/No-Go decision for Phase 2 investment
```

---

## 4. Revenue Validation Scorecard

### 4.1 Experiment Tracker

| Experiment | Status | Target | Actual | Revenue | Evidence |
|------------|--------|--------|--------|---------|----------|
| **R1: Paid Pilots** | Not Started | 3 pilots | — | $7,500 | Stripe receipts, artifacts |
| **R2: Lifetime Pre-Sale** | Not Started | 10 pre-orders | — | $11,990 | Stripe receipts |
| **R3: Pilot→Annual** | Not Started | 3/3 convert | — | $3,591 ARR | Signed contracts |
| **R4: Team Packs** | Not Started | 3 packs | — | $4,797 ARR | Stripe subscriptions |

**Total Validated Revenue Target (12 weeks): $27,878**

### 4.2 Go/No-Go Gates

| Gate | Week | Criteria | GO | VALIDATE FURTHER | PIVOT |
|------|------|----------|----|------------------|-------|
| **Gate R1** | 4 | R1: ≥1 paid pilot | ≥1 pilot | 0 pilots but 3+ calls | 0 pilots, 0 calls |
| **Gate R2** | 4 | R2: ≥3 pre-orders | ≥3 | 1-2 pre-orders | 0 after 1K visitors |
| **Gate R3** | 8 | R3: ≥2/3 convert | ≥2 | 1/3 converts | 0/3 convert |
| **Gate R4** | 10 | R4: ≥1 Team Pack | ≥1 | 0 but 3+ intros | 0 intros |
| **Final Gate** | 12 | **Total ARR ≥ $10K** | ≥$10K | $5K-10K | <$5K |

---

## 5. Evidence Requirements

### 5.1 Per-Experiment Evidence Checklist

**R1 Paid Pilot:**
- [ ] Stripe receipt for $1,250 deposit
- [ ] Stripe receipt for $1,250 balance
- [ ] Signed scope agreement (1-pager)
- [ ] Delivered artifacts (`fixes.md`, `vulnerabilities.md`, `report.md`)
- [ ] Client feedback email/testimonial
- [ ] Case study permission (signed)

**R2 Lifetime Pre-Sale:**
- [ ] Stripe Checkout session IDs
- [ ] Customer emails + names
- [ ] Refund policy acknowledged
- [ ] Traffic analytics (visitors, conversion rate)

**R3 Pilot→Annual:**
- [ ] Signed annual contract or Stripe Subscription ID
- [ ] Pilot credit applied correctly
- [ ] License keys issued
- [ ] Team onboarding call completed

**R4 Team Pack:**
- [ ] Stripe Subscription for $1,599/yr
- [ ] 5 license keys issued
- [ ] Seat management documented

---

## 6. Revenue Validation Risks & Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **Pilots ask for custom features** | High | Scope creep | Fixed scope: "1 repo, 5 days, audit trail only" |
| **Clients delay payment** | Medium | Cash flow | 50% upfront; Stripe auto-charge balance |
| **Pre-sale refund requests** | Low | Reputation | Clear "beta Q1 2027"; 30-day post-delivery refund |
| **Pilots don't convert** | Medium | ARR gap | Test lower price ($299) or usage-based |
| **Founder bias in pilot execution** | High | False confidence | Document exact steps; time-box; no manual fixes beyond engine |
| **Seasonal slowdown (holidays)** | Medium | Timeline slip | Start Week 1 Nov; avoid Dec 20-Jan 5 |

---

## 7. Revenue Tracking Dashboard (Template)

### 7.1 Weekly Revenue Tracker

| Week | R1 Pilots | R2 Pre-Sales | R3 Conversions | R4 Team Packs | Cash Collected | ARR Signed |
|------|-----------|--------------|----------------|---------------|----------------|------------|
| 1 | 0 | 0 | 0 | 0 | $0 | $0 |
| 2 | | | | | | |
| 3 | | | | | | |
| 4 | | | | | | |
| 5 | | | | | | |
| 6 | | | | | | |
| 7 | | | | | | |
| 8 | | | | | | |
| 9 | | | | | | |
| 10 | | | | | | |
| 11 | | | | | | |
| 12 | | | | | | |
| **Total** | | | | | | |

### 7.2 Cohort Revenue Tracking

| Customer | Source | Pilot Revenue | Annual ARR | Lifetime | Team Pack | Expansion | Churn Date | LTV Realized |
|----------|--------|---------------|------------|----------|-----------|-----------|------------|--------------|
| PilotCo A | R1 | $2,500 | $1,995 | — | — | — | — | — |
| SoloDev B | R2 | — | — | $1,199 | — | — | — | — |
| ... | | | | | | | | |

---

## 8. Pricing Validation Integration

### 8.1 Experiment E7 (From Validation Plan) Integration

| E7 Question | Revenue Experiment | Validation Signal |
|-------------|-------------------|-------------------|
| "Would your team buy at $399/seat/yr?" | R3 Conversion | Actual contract signed |
| "What would make it a no-brainer?" | R1 Pilot Debrief | Feature requests vs price objections |
| "Who approves $400/yr tool?" | R1/R3 Sales Process | Decision-maker identified |

### 8.2 Price Sensitivity Testing

| Test | Method | Success Signal |
|------|--------|----------------|
| **A/B: $399 vs $299** | Alternate offers in R1/R3 | Higher close rate at $299? |
| **Anchor: "Saves 4 hrs × $150 = $600/project"** | R1 Pitch | "That's reasonable" vs "Too expensive" |
| **Lifetime vs Annual** | R2 vs R3 | Which converts better? |

---

## 9. First 10 Customers — Profile Targets

| # | Target Profile | Source | Experiment | Expected Revenue |
|---|----------------|--------|------------|------------------|
| 1 | Fintech Tech Lead (15 eng) | LinkedIn DM | R1 Pilot | $2,500 + $1,995 ARR |
| 2 | Insurtech Tech Lead (25 eng) | LinkedIn DM | R1 Pilot | $2,500 + $3,990 ARR |
| 3 | Healthtech Tech Lead (8 eng) | Referral | R1 Pilot | $2,500 + $1,197 ARR |
| 4 | Solo Java Freelancer | Reddit | R2 Lifetime | $1,199 |
| 5 | Agency Owner (3 devs) | Discord | R2 Lifetime | $1,199 |
| 6 | Solo Java Dev | Twitter | R2 Lifetime | $1,199 |
| 7 | PilotCo A (convert) | R3 | — | $1,995 ARR |
| 8 | PilotCo B (convert) | R3 | — | $3,990 ARR |
| 9 | PilotCo C (convert) | R3 | — | $1,197 ARR |
| 10 | Growing Team (10 eng) | R4 Referral | R4 Team Pack | $1,599 ARR |

**Projected First-10 Revenue: $14,379 cash + $15,165 ARR**

---

## 10. Post-Validation: Scaling Revenue

### 10.1 Month 4-6: Repeatable Sales Motion

| Activity | Frequency | Owner | Metric |
|----------|-----------|-------|--------|
| LinkedIn Outreach | 20 DMs/week | Founder | 3 calls/week |
| Demo Calls | 3/week | Founder | 30% close rate |
| Pilot Execution | 1-2/week | Founder | 5-day turnaround |
| Referral Ask | Every pilot close | Founder | 1 intro/pilot |
| Content Publishing | 1/week | Founder | 1,000 views/post |

### 10.2 Month 6-12: Channel Expansion

| Channel | Investment | Expected CAC | Timeline |
|---------|------------|--------------|----------|
| **Partner Program** | 20% rev share | $0 upfront | Month 6 |
| **MCP Server Launch** | Dev time (2 wks) | $0 | Month 8 |
| **IntelliJ Plugin** | Dev time (6 wks) | $0 | Month 10 |
| **Conference Sponsorship** | $2,000 | $500 | Month 9 |

---

## 11. Validation Evidence Register (Revenue-Specific)

*See `docs/05_VALIDATION_EVIDENCE.md` for full register. Revenue-specific entries:*

| # | Claim | Source | Date | Evidence Type | Revenue Amount | Status |
|---|-------|--------|------|---------------|----------------|--------|
| RV1 | [Client X paid $2,500 pilot deposit] | Stripe Receipt #xxx | YYYY-MM-DD | Primary | $2,500 | Pending |
| RV2 | [Client Y signed $1,995/yr annual] | Stripe Sub #yyy | YYYY-MM-DD | Primary | $1,995 ARR | Pending |
| RV3 | [Client Z pre-ordered $1,199 lifetime] | Stripe Checkout #zzz | YYYY-MM-DD | Primary | $1,199 | Pending |
| RV4 | [Prospect said "$399 too expensive; $199 okay"] | Interview Notes | YYYY-MM-DD | Primary | — | Pending |
| RV5 | [Prospect said "Need SSO first"] | Interview Notes | YYYY-MM-DD | Primary | — | Pending |

---

## 12. Decision Framework: Validation → MVP Investment

### 12.1 Investment Gates

| Gate | Validation Required | Investment Unlocked |
|------|---------------------|---------------------|
| **Gate 0 (Week 2)** | E1 PASS (CLI UX) + E3 PASS (Outreach) | Phase 1 Core Engine Completion |
| **Gate 1 (Week 6)** | E2 PASS (Fix Loop) + E4 PASS (Model) + E5 PASS (Audit) + R1 ≥1 Pilot | Phase 2 CLI Polish + Release Artifacts |
| **Gate 2 (Week 12)** | R3 ≥2 Conversions + Total ARR ≥$10K | Marketing Budget ($2K/mo), Contractor Help |

### 12.2 If Validation Fails

| Failure Mode | Pivot Action | Revised Investment |
|--------------|--------------|---------------------|
| **R1: 0 Pilots** | Lower price to $1,500; target verification-only service | Pause Phase 2; service-first |
| **R2: 0 Pre-orders** | Kill lifetime tier; focus on annual team licenses | Simplify pricing |
| **R3: 0 Conversions** | Test $199/seat/yr; usage-based ($50/project) | Re-price; extend pilots |
| **All Fail** | Pivot to "Spring Boot Verification Consulting" | Service business; no product |

---

## 13. Final Validation Checklist

**Before investing in Phase 2 (CLI polish, release, marketing):**

- [ ] **R1:** ≥1 paid pilot completed with artifacts delivered
- [ ] **R3:** ≥2 pilot-to-annual conversions
- [ ] **E1:** ≥4/5 usability testers complete flow <15 min, say "would use"
- [ ] **E2:** ≥8/10 real Spring Boot repos reach 0 HIGH/CRITICAL
- [ ] **E4:** NVIDIA ≥70% compile success vs GPT-4o
- [ ] **E5:** 2/2 auditors accept `fixes.md` format
- [ ] **Total Validated ARR:** ≥$10,000

**If ALL checked → PROCEED TO PHASE 2**

**If ANY unchecked → VALIDATE FURTHER (extend timeline, don't build)**

**If MAJORITY unchecked → PIVOT**

---

*This plan is executed in real-time. Update `docs/05_VALIDATION_EVIDENCE.md` with every experiment result. No experiment is complete until evidence is recorded. Do not proceed to Phase 2 without Gate 1 PASS.*