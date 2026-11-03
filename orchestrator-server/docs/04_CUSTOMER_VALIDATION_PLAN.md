# Customer Validation Plan — Java Companion Orchestrator
## Validation Strategy | Created: 2026-10-26

---

## 1. Customer Segmentation

### 1.1 Primary Segment: Java Tech Lead at Regulated Mid-Size Company (Fintech/Insurtech)

| Attribute | Detail |
|-----------|--------|
| **Profile** | Java Tech Lead / Engineering Manager; 8-30 engineers; Spring Boot 3.x, Maven, PostgreSQL, Docker, GitHub/GitLab CI |
| **Workflow** | Receives AI-generated PRs daily from team using Copilot/Cursor; must review for security, correctness, compliance before merge; owns team tool budget ($500-2K/mo) |
| **Recurring Problem** | AI PRs arrive 5-10x faster than human review capacity; 40% of AI code has security vulnerabilities (NYU study); rubber-stamping creates audit risk; no verifiable trail of what was checked |
| **Existing Solution** | Manual code review + Semgrep/SpotBugs in CI + hope; some use Snyk/Semgrep Pro; most have no systematic verification |
| **Frequency** | Daily — every AI-generated PR needs review; audit prep quarterly |
| **Consequences of Not Solving** | Security incident (avg $4.45M per IBM 2023); failed audit (remediation $100K-1M+); burnout; regulatory fines; reputational damage |
| **Purchasing Authority** | Team tool budget owner; can approve $500-2K/mo without CTO; influences org-wide tooling |
| **Likely Objections** | "We already have Copilot + Semgrep in CI"; "CLI adds friction vs IDE plugin"; "Docker not allowed on our machines"; "Budget cycle doesn't align"; "Need to see it work on our codebase first" |

---

### 1.2 Secondary Segment: Solo Java Freelancer / Agency Owner

| Attribute | Detail |
|-----------|--------|
| **Profile** | Independent consultant or small agency (1-5 people); builds Spring Boot APIs for clients; uses Copilot/ChatGPT for speed |
| **Workflow** | Generates code with AI → manually reviews → delivers to client; reputational risk if client finds vulnerabilities |
| **Recurring Problem** | Can't trust AI output for client deliverables; manual verification takes 3-5 hours per project; no audit trail to show client |
| **Existing Solution** | Manual review; avoids AI for critical paths; uses free Semgrep/SpotBugs ad-hoc |
| **Frequency** | Per project (2-8 projects/month) |
| **Consequences of Not Solving** | Client loses trust; contract loss; liability if vulnerability exploited; can't scale with AI |
| **Purchasing Authority** | Personal/agency budget; immediate decision |
| **Likely Objections** | "$399/yr is steep for occasional use"; "Prefer pay-per-project"; "Need to work on client's machine (no Docker)"; "Just use free tools" |

---

### 1.3 Tertiary Segment: Spring Boot Team Adopting AI (Early Majority)

| Attribute | Detail |
|-----------|--------|
| **Profile** | Engineering Manager at 20-100 engineer company; mandated to adopt AI but no safety net; Spring Boot + PostgreSQL + Kubernetes |
| **Workflow** | Team uses Copilot; inconsistent review practices; security team overwhelmed; compliance asking for AI governance |
| **Recurring Problem** | No standardized verification process; "review AI code carefully" doesn't scale; audit trail missing |
| **Existing Solution** | Ad-hoc guidelines; GitHub Advanced Security ($49/committer/mo); internal scripts |
| **Frequency** | Continuous — every PR; audit annually |
| **Consequences of Not Solving** | Inconsistent quality; security gaps; compliance findings; wasted engineering time |
| **Purchasing Authority** | Engineering Manager recommends; CTO approves >$500; procurement process 30-90 days |
| **Likely Objections** | "Need SSO/SCIM"; "Need team dashboards"; "Procurement requires vendor assessment"; "Prefer IDE integration"; "Budget frozen until next quarter" |

---

## 2. Problem Validation — Critical Assumptions

| ID | Assumption | What Must Be True | Supporting Evidence | Contradictory Evidence | Unknown | Test Method |
|----|------------|-------------------|---------------------|------------------------|---------|-------------|
| **PA1** | AI-generated Spring Boot code has high vulnerability rate | >30% of AI-generated Spring Boot code has HIGH/CRITICAL findings | NYU study: 40% of AI programs vulnerable; Virtue AI audit: all 4 tools fail CWE categories; BSI/ANSSI report: "mild and severe flaws commonly present" | GitHub claims Copilot improves code quality; some developers report "mostly secure" | Exact rate for Spring Boot specifically; varies by model/prompt | E2: Run `companion verify` on 10 real Spring Boot repos generated with AI |
| **PA2** | Review bottleneck is real and painful | Tech leads spend >5 hrs/week reviewing AI PRs; PRs blocked waiting for review | Reddit r/ExperiencedDevs: "half my team relying heavily on claude... skipping manual security checks"; r/devops: 1.6M git events show velocity drops without QA | Some teams report "Copilot saves time overall"; small teams may not feel bottleneck | Hours spent per week; varies by team size/AI adoption | Interview Q: "How many hours/week do you spend reviewing AI-generated PRs?" |
| **PA3** | Audit/compliance fear drives purchasing | Failed audit or security incident triggers tool purchase | BSI/ANSSI: "systematic risk analysis should be performed"; r/ExperiencedDevs: "made me realize team was trusting AI too much" | Many companies have no formal AI policy yet; some ignore risk | % of regulated companies with AI governance policy; purchase trigger specificity | Interview Q: "Has your company had an audit finding related to AI-generated code?" |
| **PA4** | CLI UX is acceptable for daily use | Tech leads will run CLI command daily vs IDE plugin | VibeRails: desktop app model works; Bootify: web UI; Haft: CLI with TUI | GitHub Copilot: 1.3M seats in IDE; Cursor: IDE-first; JetBrains: IDE-native AI | Preference for CLI vs IDE plugin for this specific workflow | E1: Usability test — 5 tech leads run `companion generate` on fresh machine |
| **PA5** | 5-round fix loop achieves 0 HIGH/CRITICAL | ≥80% of projects reach clean state after 5 rounds | Theory: fingerprint tracking + regression guard prevents loops; similar to Snyk AgentFix + mutation testing | Snyk: "remediate more than two-thirds"; Pinata: AI verification eliminates FPs; real business logic may be unfixable | Works on CRUD vs complex business logic; regression guard effectiveness | E2: Eval on 10 real Spring Boot repos (not just golden specs) |
| **PA6** | Audit trail format (`fixes.md`, `vulnerabilities.md`) satisfies auditors | Compliance officers accept Markdown evidence | BSI/ANSSI: "flag AI generated code blocks and document used AI tools"; VibeRails: "export HTML/CSV for audit evidence" | No known precedent for this specific format; auditors may require specific tools/formats | Exact format requirements per framework (SOC2, PCI-DSS, HIPAA) | E5: Show artifacts to 2 compliance officers |
| **PA7** | Docker is available in target environments | ≥75% of prospects can run Docker locally | JHipster Heroku issue: 50% use Heroku (implies Docker); most modern dev machines have Docker | Some corps ban Docker Desktop (license); some use Podman/Colima; air-gapped envs | % with Docker Desktop vs Podman vs none; corporate policy variance | E6: Survey 20 prospects: "Can you run Docker locally?" |
| **PA8** | Budget authority at $399/seat/yr | Tech leads can approve without CTO for team of 5-10 | Market research: team tool budgets $500-2K/mo; IntelliJ $149/yr, YourKit $99/yr | Some companies require CTO for any tool >$100; procurement policies vary | Threshold for autonomous purchase; varies by company size/culture | Interview Q: "What's your process for buying a $400/yr dev tool?" |

---

## 3. Customer Interview Script

### 3.1 Interview Guide — Neutral, Behavior-Focused

**Duration:** 30 minutes  
**Format:** Video call (record with permission)  
**Incentive:** $50 gift card or free 1-year license when available

---

#### Introduction (3 min)
> "Thanks for taking the time. I'm researching how Java teams handle AI-generated code verification. This isn't a sales call — I'm building a tool and need to understand your actual workflow. Everything you share stays confidential. May I record for note-taking?"

---

#### Background & Context (5 min)
1. "Walk me through your role and team size."
2. "What's your current stack? Spring Boot version, build tool, database, CI/CD?"
3. "How long have you been using AI coding assistants (Copilot, Cursor, ChatGPT, etc.)?"
4. "Roughly what percentage of your team's PRs involve AI-generated code?"

---

#### Current Workflow — AI Code Review (10 min)
5. "Think of the last AI-generated PR you reviewed. Walk me through what you did from opening the PR to merging (or rejecting)."
   - *Probe:* What tools ran automatically? What did you check manually? How long did it take?
6. "What's the most common type of issue you find in AI-generated code?"
   - *Probe:* Security? Logic bugs? Style? Test failures? Missing edge cases?
7. "Have you ever merged AI code that later caused a problem in production or staging?"
   - *Probe:* What happened? How was it caught? What changed after?
8. "What tools do you currently use for code quality/security scanning?"
   - *Probe:* Semgrep, SpotBugs, SonarQube, Snyk, Checkmarx, GitHub CodeQL, custom?
9. "How do you handle security vulnerabilities found in dependencies?"
   - *Probe:* Dependabot? Snyk? Manual? How long to fix?

---

#### Pain Points & Consequences (7 min)
10. "What's the biggest frustration with your current AI code review process?"
11. "Has your team ever had a security incident or audit finding related to AI-generated code?"
    - *Probe:* What happened? What was the cost (time, money, reputation)?
12. "How do you demonstrate to auditors/compliance that AI-generated code was reviewed?"
    - *Probe:* What evidence do you produce? PR comments? Checklists? Tool reports?
13. "If you could wave a magic wand and fix one thing about this workflow, what would it be?"

---

#### Existing Solutions & Alternatives (5 min)
14. "What have you tried to solve this? (Tools, processes, hiring, training)"
15. "Why didn't those solutions work fully?"
16. "What would make you switch from your current setup to a new tool?"

---

#### Purchasing & Budget (5 min)
17. "Who decides on purchasing developer tools for your team?"
18. "What's the typical budget range for a per-seat developer tool?"
19. "What's the approval process for a $400/year per developer tool?"
20. "Have you paid for any AI code verification or security tools? Which ones? How was ROI?"

---

#### Product Concept Test (3 min) — *Only at end, after understanding their reality*
> "I'm building a CLI tool that takes a natural language spec like 'REST CRUD for Book with JWT auth', generates a Spring Boot project, then runs 7 security scanners, auto-fixes findings in 5 rounds, and produces an audit trail — all in ~10 minutes. It runs locally via Docker."

21. "What's your immediate reaction?"
22. "Would you run this as a CLI, or would you need it in your IDE?"
23. "What would you need to see to justify $399/seat/year to your manager?"
24. "What would make you NOT use this?"

---

#### Closing (2 min)
25. "Is there anything I should have asked but didn't?"
26. "Would you be open to a 15-minute demo when a prototype is ready?"
27. "Can I follow up with 2-3 clarifying questions via email?"

---

### 3.2 Interview Do's and Don'ts

| Do | Don't |
|----|-------|
| Ask about specific recent events ("last PR you reviewed") | Ask hypotheticals ("would you use a tool that...") |
| Listen for workarounds ("we just...") | Pitch your solution before understanding their problem |
| Note exact language they use for problems | Use your terminology (fix loop, fingerprint, etc.) |
| Ask about money spent / time lost | Ask "would you pay $X?" |
| Probe on consequences ("what happened next?") | Accept "it's fine" without specifics |
| Record exact quotes for evidence register | Paraphrase or summarize in evidence |

---

## 4. Willingness-to-Pay Experiments

### 4.1 Experiment Ladder — Interest → Commitment

| Level | Experiment | What It Measures | Commitment Level |
|-------|------------|------------------|------------------|
| **1. Interest** | Landing page waitlist | Email signup for early access | Low — email only |
| **2. Engagement** | Demo request form | Request 15-min live demo | Medium — time commitment |
| **3. Qualification** | Paid pilot application | Apply for 5-day audit/fix service ($2,500) | High — application effort |
| **4. Financial** | Paid pilot deposit | $500 deposit to reserve pilot slot | Very high — money at risk |
| **5. Purchase** | Annual license pre-order | Sign 1-year contract at $399/seat | Maximum — legal commitment |

**Rule:** Only Levels 3-5 count as validation. Levels 1-2 are leading indicators only.

---

### 4.2 Willingness-to-Pay Interview Questions

> *Ask only after validating problem exists (PA1-PA3 confirmed)*

1. "You mentioned spending ~X hours/week reviewing AI PRs. At your fully loaded cost (~$150/hr), that's ~$Y/week. What would you pay to reduce that by 80%?"
2. "Your last audit took Z weeks and cost $W. What would you pay to cut prep time in half?"
3. "If a tool cost $399/seat/year and saved 4 hours of review per project, would your team buy it? Who approves?"
4. "Would you pay $2,500 for a 5-day service where I run verification on your AI-generated service, fix findings, and give you an audit trail? Refund if no critical issues found."
5. "What's the maximum you'd pay per seat per year for a tool that eliminates manual AI code review?"

**Scoring:**
- **Strong signal:** Names specific budget, describes approval process, references past purchases
- **Weak signal:** "Maybe," "depends," "ask my manager" without knowing process
- **Negative signal:** "We'd never pay for that," "free tools are fine," "not a priority"

---

## 5. Validation Experiments

### Experiment E1: Usability Test — CLI UX Acceptance

| Element | Specification |
|---------|---------------|
| **Hypothesis** | Java tech leads can complete `companion generate` flow in <15 min on fresh machine; ≥4/5 say "I'd use this" |
| **Target Audience** | 5 Java Tech Leads at fintech/insurtech (10-50 engineers); active Copilot/Cursor users |
| **Recruitment** | LinkedIn direct outreach; personal network; Java Discord #jobs #spring-boot; $50 gift card incentive |
| **Execution Steps** | 1. Send Calendly link for 45-min slot<br>2. Provide fresh VM/laptop with Java, Maven, Docker, `companion.jar`<br>3. Task: "Generate a REST CRUD for Book entity with author, genre, ISBN, pagination, validation"<br>4. Observe: time, confusion points, questions asked, approval decision<br>5. Post-task: SUS survey + "Would you use this? Why/why not?" |
| **Estimated Cost** | $250 (5 × $50 gift cards) + 5 hrs founder time |
| **Sample Size** | 5 (qualitative; Nielsen: 5 users find 80% of usability issues) |
| **Limitations** | Small n; prototype may be buggy; participants may be polite; not real project |
| **Success Criteria** | • ≥4/5 complete full flow in <15 min<br>• ≥4/5 rate "Would use this" 4+ on 5-point scale<br>• Zero critical blockers (crash, unclear question, dead end) |
| **Failure Criteria** | • ≤2/5 complete in <15 min<br>• ≥3/5 rate "Would use this" ≤2<br>• Any critical blocker (crash, data loss, infinite loop) |
| **Next Decision** | **Pass:** Proceed to Phase 2 CLI polish<br>**Fail:** Pivot to IntelliJ Plugin first (same engine, different UI) |

---

### Experiment E2: Real Project Evaluation — Fix Loop Efficacy

| Element | Specification |
|---------|---------------|
| **Hypothesis** | 5-round fix loop achieves 0 HIGH/CRITICAL on ≥8/10 real Spring Boot projects |
| **Target Audience** | 10 real Spring Boot projects (open source) with AI-generated or legacy code |
| **Recruitment** | GitHub: Spring PetClinic, JHipster samples, Bootify samples, Broadleaf Commerce, Flowable, Camunda, Axon, Keycloak extensions, custom repos from network |
| **Execution Steps** | 1. Clone 10 repos<br>2. Run `companion verify` on each (or manually run verification pipeline)<br>3. If findings exist, run fix loop (rounds 2-5)<br>4. Record: initial findings by severity, findings after each round, regressions introduced, final state<br>5. Classify: CRUD vs business logic complexity |
| **Estimated Cost** | $0 (compute only) + 20 hrs founder time |
| **Sample Size** | 10 projects (5 CRUD, 5 business logic) |
| **Limitations** | Not generated by our tool (different code style); may not have AI patterns; manual fix loop simulation |
| **Success Criteria** | • ≥8/10 reach 0 HIGH/CRITICAL after round 5<br>• ≤5% regression rate (new findings / fixed findings)<br>• Fix loop terminates (doesn't hang) on all 10 |
| **Failure Criteria** | • ≤5/10 reach 0 HIGH/CRITICAL<br>• >10% regression rate<br>• Fix loop hangs or exceeds budget on >2 projects |
| **Next Decision** | **Pass:** Proceed to Gate 1<br>**Fail:** Narrow to CRUD-only scaffolding accelerator; market as "verified starter" |

---

### Experiment E3: Cold Outreach — Distribution Feasibility

| Element | Specification |
|---------|---------------|
| **Hypothesis** | Solo founder can book ≥1 demo from 20 cold LinkedIn DMs to fintech tech leads |
| **Target Audience** | 20 Java Tech Leads at fintech/insurtech (10-50 engineers); LinkedIn: "Java Tech Lead" + "fintech" + "Spring Boot" |
| **Recruitment** | LinkedIn Sales Navigator (free trial) or manual search; personalized connection request + DM |
| **Message Template** | "Hi [Name], I'm a solo founder building a CLI that verifies AI-generated Spring Boot code passes security audits (compiles, tests, 7 scanners, auto-fix, audit trail). Open to a 15-min demo? Happy to run it on one of your repos. - [Your Name]" |
| **Execution Steps** | 1. Build list of 50 prospects<br>2. Send 20 connection requests + DMs (Week 1)<br>3. Follow up once at 3 days<br>4. Track: connection rate, reply rate, demo booked, disqualified |
| **Estimated Cost** | $0 + 5 hrs founder time |
| **Sample Size** | 20 DMs (minimum for statistical signal) |
| **Limitations** | LinkedIn response rates low (~10-20%); may need 50+ for 1 demo; selection bias |
| **Success Criteria** | • ≥20% connection acceptance rate<br>• ≥15% reply rate (of connections)<br>• ≥1 demo booked |
| **Failure Criteria** | • <10% connection rate<br>• <5% reply rate<br>• 0 demos booked after 50 DMs |
| **Next Decision** | **Pass:** Direct sales viable; scale to 100 DMs/week<br>**Fail:** Pivot to partner channel (Spring Boot consultancies) or content marketing |

---

### Experiment E4: Model Comparison — NVIDIA Quality

| Element | Specification |
|---------|---------------|
| **Hypothesis** | NVIDIA Nemotron-3-Ultra achieves ≥70% compile success rate of GPT-4o on Spring Boot golden specs |
| **Target Audience** | N/A (technical benchmark) |
| **Recruitment** | N/A |
| **Execution Steps** | 1. Define 10 golden specs (5 CRUD, 3 Auth+Relations, 2 Business Rules)<br>2. Run each spec through Clarifier → Planner → Coder using NVIDIA<br>3. Run same specs with GPT-4o (via OpenRouter)<br>4. Measure: compile success (round 1), tests pass (round 5), findings by severity, cost, time<br>5. Compare side-by-side |
| **Estimated Cost** | ~$10-20 API costs (GPT-4o); NVIDIA free tier |
| **Sample Size** | 10 specs × 2 models = 20 runs |
| **Limitations** | Small sample; specs may favor one model; prompt engineering not optimized |
| **Success Criteria** | • NVIDIA compile success ≥70% of GPT-4o<br>• NVIDIA cost per spec ≤$0.10<br>• No catastrophic failures (infinite loops, OOM) |
| **Failure Criteria** | • NVIDIA compile success <50% of GPT-4o<br>• Cost per spec >$0.25<br>• Frequent NPEs/malformed tool calls |
| **Next Decision** | **Pass:** Keep NVIDIA as primary<br>**Fail:** Switch to OpenRouter/GPT-4o as primary; NVIDIA as fallback |

---

### Experiment E5: Audit Trail Review — Compliance Acceptance

| Element | Specification |
|---------|---------------|
| **Hypothesis** | Compliance officers accept `fixes.md` + `vulnerabilities.md` as audit evidence |
| **Target Audience** | 2 compliance/audit professionals (SOC2, PCI-DSS, or HIPAA experience); fintech/healthtech background |
| **Recruitment** | Network referrals; LinkedIn "Compliance Engineer" "Security Auditor" at fintech; $100 honorarium |
| **Execution Steps** | 1. Generate artifacts from E2 (real project fix loop)<br>2. 30-min call: walk through `fixes.md`, `vulnerabilities.md`, `report.md`<br>3. Ask: "Would this satisfy evidence requirement for [framework]?"<br>4. Collect specific format change requests |
| **Estimated Cost** | $200 honoraria + 2 hrs founder time |
| **Sample Size** | 2 (qualitative; need at least 2 for pattern) |
| **Limitations** | Opinion of 2 individuals; not legal advice; frameworks differ |
| **Success Criteria** | • Both say "this would satisfy evidence requirement" or "minor changes needed"<br>• No fundamental format rejection |
| **Failure Criteria** | • Either says "this would not satisfy audit evidence"<br>• Major format redesign required |
| **Next Decision** | **Pass:** Freeze artifact formats v1.0<br>**Fail:** Redesign artifact format; engage compliance consultant |

---

### Experiment E6: Docker Environment Survey

| Element | Specification |
|---------|---------------|
| **Hypothesis** | ≥75% of target prospects can run Docker locally (Docker Desktop, Podman, or Colima) |
| **Target Audience** | 20 Java Tech Leads at fintech/insurtech/healthtech |
| **Recruitment** | Same as E3 (can combine); LinkedIn poll; Java Discord poll |
| **Execution Steps** | 1. Create 3-question survey (Google Forms/Typeform)<br>2. Distribute via LinkedIn DM, Discord, email<br>3. Questions: "Can you run Docker Desktop locally?" "If not, what do you use?" "Would a Podman/Colima fallback work?"<br>4. Collect 20 responses |
| **Estimated Cost** | $0 + 1 hr founder time |
| **Sample Size** | 20 responses |
| **Limitations** | Self-reported; may not reflect actual corporate policy |
| **Success Criteria** | • ≥15/20 say yes to Docker Desktop or Podman/Colima<br>• ≤2/20 say "no container runtime allowed" |
| **Failure Criteria** | • >5/20 say "no container runtime allowed"<br>• Majority use only Podman (need compatibility testing) |
| **Next Decision** | **Pass:** Docker-only sandbox v1<br>**Fail:** Build Podman/Colima/rootless fallback; add to Phase 1.8 scope |

---

### Experiment E7: Pricing Validation — Willingness to Pay

| Element | Specification |
|---------|---------------|
| **Hypothesis** | Java tech leads at regulated companies will pay $399/seat/yr for verified AI code generation |
| **Target Audience** | 10 Java Tech Leads (from E1/E3 interviews) |
| **Recruitment** | Same participants as E1/E3; ask at end of interview |
| **Execution Steps** | 1. After validating problem, present: "Tool saves ~4 hrs review/project = ~$600 value/project"<br>2. Ask: "At $399/seat/year, would your team buy? Who approves?"<br>3. Ask: "What would make it a no-brainer? What would kill the deal?"<br>4. Track: Yes/No/Maybe; approval process; objections |
| **Estimated Cost** | $0 (embedded in interviews) |
| **Sample Size** | 10 (from interview pool) |
| **Limitations** | Hypothetical; no actual purchase; social desirability bias |
| **Success Criteria** | • ≥6/10 say "Yes" or "Likely"<br>• ≥4/10 can describe approval process concretely<br>• No universal "too expensive" objection |
| **Failure Criteria** | • ≤3/10 say "Yes"<br>• ≥5/10 say "Too expensive" or "Free tools sufficient"<br>• Cannot describe approval process |
| **Next Decision** | **Pass:** $399/seat/yr validated<br>**Fail:** Test lower price ($199/seat/yr) or usage-based; or pivot to freemium |

---

## 6. Customer Acquisition Feasibility

### 6.1 Channel Evaluation for Solo Founder

| Channel | Feasibility | Cost | Time to First Lead | Scalability | Solo-Friendly? |
|---------|-------------|------|-------------------|-------------|----------------|
| **Direct LinkedIn Outreach** | High | $0 | 1-2 weeks | Low (manual) | ✅ Yes |
| **Reddit (r/java, r/SpringBoot, r/ExperiencedDevs)** | High | $0 | 1-3 weeks | Medium (content) | ✅ Yes |
| **Java Discord/Slack Communities** | High | $0 | 1-2 weeks | Low (engagement) | ✅ Yes |
| **Technical Blog / Dev.to / Medium** | Medium | $0 | 4-12 weeks | High (SEO) | ✅ Yes |
| **Conference Talks (CFP)** | Medium | $0-500 | 3-6 months | Medium | ⚠️ Travel time |
| **Partner: Spring Boot Consultancies** | Medium | Rev share | 2-4 months | High | ✅ Yes |
| **Product Hunt / Hacker News** | Low | $0 | Launch day | One-time spike | ✅ Yes |
| **SEO / Content Marketing** | Low | $0 | 6-12 months | High | ✅ Yes |
| **Paid Ads** | Low | $500+/mo | Immediate | High | ❌ Budget |

### 6.2 Recommended Acquisition Sequence

**Month 1-2 (Validation Phase):**
1. Direct LinkedIn outreach (20 DMs/week) → Target: 3 demo bookings
2. Reddit technical posts (2 posts: "Why AI generates SQLi in Spring Boot", "My 5-round fix loop results")
3. Java Discord engagement (answer questions, mention tool when relevant)

**Month 3-6 (Early Customers):**
1. Convert 3 pilots → case studies
2. Dev.to technical deep-dives (1/month)
3. Referral ask: "Who else on your team / network should see this?"
4. Partner outreach: 5 Spring Boot consultancies

**Month 6-12 (Scale):**
1. Product Hunt launch
2. Conference CFP submissions (SpringOne, Devoxx, JFokus)
3. MCP server release (agent ecosystem distribution)
4. IntelliJ Plugin (IDE distribution channel)

---

## 7. Validation Timeline & Milestones

| Week | Milestone | Experiment | Decision Gate |
|------|-----------|------------|---------------|
| 1-2 | Recruit 5 usability test participants | E1 | — |
| 2-3 | Run usability tests | E1 | **GO/NO-GO: CLI UX** |
| 2-3 | Send 20 cold outreach DMs | E3 | — |
| 3-4 | Run model comparison | E4 | **GO/NO-GO: NVIDIA Provider** |
| 3-4 | Clone & test 10 real Spring Boot repos | E2 | — |
| 4-5 | Complete fix loop evaluation | E2 | **GO/NO-GO: Fix Loop Efficacy** |
| 4-5 | Docker survey (20 responses) | E6 | **GO/NO-GO: Sandbox Strategy** |
| 5-6 | Audit trail review with 2 auditors | E5 | **GO/NO-GO: Artifact Format** |
| 6-8 | Pricing validation (10 prospects) | E7 | **GO/NO-GO: Pricing Model** |
| 8-12 | Convert pilots to paid licenses | — | **Gate 0: First Revenue** |

---

## 8. Go/No-Go Decision Framework

### Gate 0: Problem Validated (Week 2)
| Condition | GO | VALIDATE FURTHER | PIVOT |
|-----------|----|------------------|-------|
| **E1 Usability** | ≥4/5 complete <15 min; ≥4/5 "would use" | 3/5 complete; mixed feedback | ≤2/5 complete; major UX issues |
| **E3 Outreach** | ≥1 demo booked from 20 DMs | 1-2 replies; no demo yet | 0 replies from 50 DMs |
| **PA1/PA2 Confirmed** | Interviews confirm >5 hrs/week review pain; >30% vuln rate | Partial confirmation | No pain / low vuln rate |

### Gate 1: Solution Validated (Week 6)
| Condition | GO | VALIDATE FURTHER | PIVOT |
|-----------|----|------------------|-------|
| **E2 Fix Loop** | ≥8/10 projects 0 HIGH/CRITICAL | 6-7/10; regressions manageable | ≤5/10; fix loop broken |
| **E4 Model** | NVIDIA ≥70% of GPT-4o compile success | 50-70%; acceptable with tuning | <50%; switch provider |
| **E5 Audit Trail** | 2/2 auditors accept format | 1/2 accept with minor changes | 0/2 accept; major redesign |
| **E6 Docker** | ≥15/20 can run Docker/Podman | 10-14/20; need Podman support | <10/20; major sandbox rework |

### Gate 2: Business Validated (Week 12)
| Condition | GO | VALIDATE FURTHER | PIVOT |
|-----------|----|------------------|-------|
| **E7 Pricing** | ≥6/10 say "Yes" at $399/seat/yr | 4-5/10; price sensitive | ≤3/10; wrong price point |
| **Paid Pilots** | 3/5 pilots convert to annual license | 1-2 convert; need iteration | 0 convert; no product-market fit |
| **CAC < $500** | Measured CAC < $500 | $500-1000; optimize | >$1000; channel failure |

---

## 9. Evidence Register Template

*See `docs/05_VALIDATION_EVIDENCE.md` for live register*

| Claim | Source | Date | Evidence Type | Confidence | Contradictions | Outstanding Questions |
|-------|--------|------|---------------|------------|----------------|----------------------|
| [Claim] | [Interview # / Experiment / Doc] | YYYY-MM-DD | [Primary/Secondary] | [High/Med/Low] | [Yes/No - detail] | [Question] |

---

## 10. Risk Mitigation for Validation Phase

| Risk | Mitigation |
|------|------------|
| **Participants ghost / no-show** | Over-recruit 2x; $50 incentive; flexible scheduling |
| **Prototype too buggy for usability test** | Run internal dogfood first; have backup recorded demo |
| **Cold outreach ignored** | Personalize each DM; reference their specific company/tech; A/B test messages |
| **Real projects too different from golden specs** | Select diverse repos; document differences; don't over-claim |
| **Auditors unavailable** | Start recruiting Week 1; offer $100; have backup (compliance consultant) |
| **Founder bias in interpretation** | Record all sessions; have peer review notes; use strict success criteria |

---

## 10. Success Thresholds Summary

| Metric | Minimum Threshold | Target | Stretch |
|--------|-------------------|--------|---------|
| Usability test completion rate | 60% (3/5) | 80% (4/5) | 100% (5/5) |
| "Would use this" rating | 3.5/5 avg | 4.2/5 avg | 4.7/5 avg |
| Fix loop clean rate | 60% (6/10) | 80% (8/10) | 90% (9/10) |
| Regression rate | <10% | <5% | <2% |
| Cold outreach demo rate | 5% (1/20) | 10% (2/20) | 20% (4/20) |
| NVIDIA vs GPT-4o compile | 50% | 70% | 85% |
| Auditor acceptance | 1/2 "acceptable" | 2/2 "acceptable" | 2/2 "exceeds requirements" |
| Docker availability | 50% | 75% | 90% |
| Willingness to pay $399 | 30% (3/10) | 60% (6/10) | 80% (8/10) |

---

*This validation plan is a living document. Update after each experiment. No experiment is "done" until evidence is recorded in `docs/05_VALIDATION_EVIDENCE.md`.*