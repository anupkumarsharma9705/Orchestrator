# Unit Economics Deep Dive — Java Companion Orchestrator
## Detailed Financial Modeling | Created: 2026-10-27

---

## 1. Unit Economics Framework

### 1.1 Core Definitions

| Term | Definition | Our Context |
|------|------------|-------------|
| **Unit** | One paid seat (developer license) | Primary revenue unit |
| **ARPU** | Average Revenue Per Unit (annual) | $399 (Pro Annual) / $1,199 (Lifetime) |
| **Variable Cost/Unit** | Marginal cost to serve one seat | ~$25/yr (API fallback, support, processing) |
| **Gross Margin/Unit** | (ARPU - Variable Cost) / ARPU | 94% (Annual) / 98% (Lifetime) |
| **CAC** | Customer Acquisition Cost per seat | $200 (founder time valuation) |
| **LTV** | Lifetime Value per seat | $812 (3-yr) / $1,114 (Lifetime) |
| **Payback Period** | Months to recover CAC | 7.6 months (Annual) / Immediate (Lifetime) |

### 1.2 Unit Economics Equation

```
Unit Profitability = (ARPU × Gross Margin × Retention Months) - CAC - Fixed Cost Allocation

Where:
- Retention Months = 1 / Monthly Churn Rate
- Monthly Churn = 1 - (1 - Annual Churn)^(1/12)
- Fixed Cost Allocation = Total Fixed Costs / Total Seats
```

---

## 2. Detailed Cost Breakdown

### 2.1 Variable Costs Per Seat Per Year

| Cost Component | Calculation | Annual Cost | Confidence |
|----------------|-------------|-------------|------------|
| **NVIDIA Nemotron-3-Ultra API** | Free tier (1,000 req/day) covers ~90% of usage | $0.00 | High — NVIDIA free tier generous |
| **OpenRouter Fallback (GPT-4o)** | 10% of generations × 15K tokens × $0.015/1K | $2.25 | Medium — depends on NVIDIA reliability |
| **Ollama Local Provider** | Customer's GPU; zero marginal cost | $0.00 | High — BYO hardware model |
| **Stripe Payment Processing** | (2.9% × $399) + $0.30 = $11.87/yr | $11.87 | High — standard Stripe pricing |
| **Customer Support (Email)** | 2 hrs/yr × $50/hr opportunity cost | $100.00 | Medium — assumes 30% attach rate |
| **License Key Infrastructure** | Cloudflare Workers / small VPS amortized | $1.00 | High — negligible at scale |
| **Total Variable Cost/Seat/Year** | | **~$115** | **Revised from $25** |

**⚠️ Critical Revision:** Support cost ($100) dominates. At 30% attach rate, blended = $30/seat. If support scales to 1 hr/month/seat = $600/seat/yr — changes economics significantly.

### 2.2 Revised Variable Cost Scenarios

| Scenario | Support Level | Variable Cost/Seat/Year | Gross Margin (Annual) |
|----------|---------------|-------------------------|----------------------|
| **Optimistic (Self-Service)** | Docs + FAQ only; 0.5 hrs/yr | $15 | 96% |
| **Base (Email Support)** | 2 hrs/yr at 30% attach | $30 | 93% |
| **Conservative (High Touch)** | 1 hr/month at 50% attach | $300 | 25% |
| **Enterprise (SLA)** | 4 hrs/month + priority | $2,400 | Negative at $399 |

**Decision:** Must cap support at email-only, 48hr SLA, self-service first. Target blended variable cost ≤$50/seat/yr.

### 2.3 Fixed Costs (Annual)

| Category | Cost | Notes |
|----------|------|-------|
| Domain (.dev) + SSL | $24 | Namecheap + Let's Encrypt |
| License Key Service (VPS/Workers) | $60 | Cloudflare Workers paid tier |
| Legal/Compliance Reserve | $600 | Terms, privacy, export, annual review |
| Accounting/CPA | $360 | Annual review + bookkeeping |
| Monitoring/Error Tracking | $0 | Free tiers (UptimeRobot, Sentry) |
| Email Delivery | $0 | Resend free tier (3K/mo) |
| CI/CD (GitHub Actions) | $0 | Free for private repos |
| **Total Fixed/Year** | **$1,044** | |

### 2.4 Fixed Cost Allocation Per Seat

| Total Seats | Fixed Cost/Seat/Year | Impact on Unit Economics |
|-------------|---------------------|--------------------------|
| 10 | $104 | Significant at small scale |
| 50 | $21 | Moderate |
| 100 | $10 | Minor |
| 500 | $2 | Negligible |
| 1,000 | $1 | Negligible |

---

## 3. Revenue Per Seat Models

### 3.1 Pricing Tiers

| Tier | Price | Model | Target Segment | Expected Mix |
|------|-------|-------|----------------|--------------|
| **Professional Annual** | $399/seat/yr | Subscription | Teams (5-30), regulated | 60% |
| **Professional Lifetime** | $1,199/seat | One-time | Solo devs, agencies, price-sensitive | 25% |
| **Team Pack (5 seats)** | $1,599/yr | Subscription | Growing teams | 10% |
| **Enterprise (10+)** | Custom | Contract | Large org, compliance needs | 5% |

### 3.2 Blended Revenue Per Seat (Year 1)

| Mix Assumption | Calculation | Blended ARPU |
|----------------|-------------|--------------|
| 60% Annual ($399) + 25% Lifetime ($1,199) + 10% Team ($320) + 5% Enterprise ($800) | 0.6×399 + 0.25×1199 + 0.1×320 + 0.05×800 | **$598** |
| Conservative: 80% Annual, 15% Lifetime, 5% Team | 0.8×399 + 0.15×1199 + 0.05×320 | **$532** |
| Optimistic: 40% Annual, 35% Lifetime, 15% Team, 10% Enterprise | 0.4×399 + 0.35×1199 + 0.15×320 + 0.1×800 | **$688** |

---

## 4. Customer Lifetime Value (LTV) Modeling

### 4.1 Retention Curves

| Model | Annual Churn | Monthly Churn | Median Lifetime | 3-Yr Retention |
|-------|--------------|---------------|-----------------|----------------|
| **Annual Subscription** | 15% | 1.34% | 6.7 years | 61% |
| **Lifetime License** | 0% (by definition) | 0% | Infinite (until version lock) | 100% |
| **Blended** | 9% effective | 0.78% | 11.1 years | 75% |

### 4.2 LTV Calcululations

#### Annual Subscription Seat
```
Monthly Revenue = $399 / 12 = $33.25
Monthly Variable Cost = $30 / 12 = $2.50
Monthly Gross Profit = $30.75
Monthly Churn = 1.34%
Expected Months = 1 / 0.0134 = 74.6 months
LTV (Gross) = $30.75 × 74.6 = $2,294
LTV (Net of Fixed Allocation @ 100 seats) = $2,294 - ($10 × 74.6/12) = $2,232
```

#### Lifetime License Seat
```
Revenue = $1,199 (one-time)
Variable Cost (3 years updates) = $30 × 3 = $90
Gross Profit = $1,109
No churn → LTV = $1,109 (fixed)
After year 3: version lock, no updates, no cost
```

#### Blended LTV (Weighted by Mix)

| Mix | Annual LTV | Lifetime LTV | Blended LTV |
|-----|------------|--------------|-------------|
| **Base (60/25/10/5)** | $2,232 | $1,109 | **$1,617** |
| **Conservative (80/15/5)** | $2,232 | $1,109 | **$1,927** |
| **Optimistic (40/35/15/10)** | $2,232 | $1,109 | **$1,382** |

### 4.3 LTV with Expansion Revenue

| Expansion Rate | Year 1 Seats | Year 2 Seats | Year 3 Seats | 3-Yr Revenue/Initial Seat |
|----------------|--------------|--------------|--------------|---------------------------|
| 1.0x (No expansion) | 1.0 | 0.85 | 0.72 | $399 × 2.57 = $1,025 |
| 1.5x (Base) | 1.0 | 1.28 | 1.15 | $399 × 3.43 = $1,369 |
| 2.0x (Optimistic) | 1.0 | 1.70 | 1.53 | $399 × 4.23 = $1,688 |

**Expansion-Adjusted LTV (Base Case): ~$1,369 per initial seat over 3 years**

---

## 5. Customer Acquisition Cost (CAC) Analysis

### 5.1 CAC Components

| Channel | Cost per Lead | Conversion Rate | CAC | Scale Potential |
|---------|---------------|-----------------|-----|-----------------|
| **Direct LinkedIn Outreach** | $0 (founder time) | 5% (demo→close) | $200 | Low (manual) |
| **Referral from Pilot** | $0 | 25% | $50 | Medium |
| **Content/SEO** | $0 (founder time) | 2% | $500 | High (long-term) |
| **Conference/Events** | $500 (travel) | 10% | $5,000 | Low |
| **Partner Channel** | Revenue share (20%) | 15% | $0 upfront | Medium |

### 5.2 Blended CAC (Year 1)

| Channel Mix | Weighted CAC |
|-------------|--------------|
| 50% Direct Outreach ($200) | $100 |
| 20% Referrals ($50) | $10 |
| 20% Content ($500) | $100 |
| 10% Conferences ($5,000) | $500 |
| **Blended CAC** | **~$710** |

**⚠️ Reality Check:** Blended CAC of $710 exceeds our $200 assumption. The $200 assumes ONLY direct outreach at 5% close rate. Real-world blended will be higher.

### 5.3 CAC Sensitivity

| Scenario | Blended CAC | LTV/CAC (Base LTV $1,617) |
|----------|-------------|---------------------------|
| **Founder-only Direct (Year 1)** | $200 | 8.1x |
| **Add Content Marketing (Year 2)** | $350 | 4.6x |
| **Add Conferences (Year 2)** | $600 | 2.7x |
| **Hire Sales (Year 3+)** | $2,000+ | <1x (unsustainable) |

**Critical Constraint:** Must stay founder-led sales until $50K+ ARR. Hiring sales breaks unit economics.

---

## 6. Payback Period Analysis

### 6.1 Payback by Pricing Tier

| Tier | Upfront Revenue | Variable Cost | Net Cash | Monthly Gross Profit | Payback Period |
|------|-----------------|---------------|----------|---------------------|----------------|
| **Annual ($399)** | $399 | $30 | $369 | $30.75 | **Immediate** (cash upfront) |
| **Lifetime ($1,199)** | $1,199 | $90 | $1,109 | N/A (no recurring) | **Immediate** |
| **Team Pack ($1,599/5)** | $1,599 | $150 | $1,449 | $126.50 | **Immediate** |

**Key Insight:** Annual upfront billing = immediate payback on cash basis. True payback = when LTV > CAC + Fixed Allocation.

### 6.2 True Economic Payback (Including Fixed Costs)

| Scale (Seats) | Fixed Cost/Seat | Monthly Gross Profit | Months to Cover Fixed + CAC |
|---------------|-----------------|---------------------|----------------------------|
| 10 | $104 | $30.75 | CAC $200 + Fixed $104 = $304 / $30.75 = **9.9 months** |
| 50 | $21 | $30.75 | $221 / $30.75 = **7.2 months** |
| 100 | $10 | $30.75 | $210 / $30.75 = **6.8 months** |
| 500 | $2 | $30.75 | $202 / $30.75 = **6.6 months** |

---

## 7. Break-Even Analysis

### 7.1 Break-Even Seat Count

```
Break-Even Seats = Total Fixed Costs / (ARPU × Gross Margin - Variable Cost - Fixed Allocation/Seat)

At 100 seats: Fixed Allocation = $10/seat
Contribution/Seat = $399 × 0.94 - $30 - $10 = $335
Break-Even = $1,044 / $335 = **3.1 seats**

At 10 seats: Fixed Allocation = $104/seat
Contribution/Seat = $399 × 0.94 - $30 - $104 = $241
Break-Even = $1,044 / $241 = **4.3 seats**
```

**Conclusion:** Break-even at **4-5 seats** — extremely low due to high margins and low fixed costs.

### 7.2 Cash Flow Break-Even (Month)

| Scenario | Month to Positive Cash Flow | Cumulative Cash at Month 12 |
|----------|----------------------------|----------------------------|
| Conservative (1 cust/mo) | Month 3 | $2,779 |
| Base (2-3 cust/mo accelerating) | Month 2 | $11,186 |
| Optimistic (referral flywheel) | Month 1 | $22,786 |

---

## 8. Cohort Analysis Framework

### 8.1 Monthly Cohort Tracking Template

| Cohort Month | Seats Added | Month 1 Retention | Month 3 Retention | Month 6 Retention | Month 12 Retention | Expansion Seats | LTV Realized |
|--------------|-------------|-------------------|-------------------|-------------------|--------------------|-----------------|--------------|
| 2027-01 | | | | | | | |
| 2027-02 | | | | | | | |
| ... | | | | | | | |

### 8.2 Leading Indicators (Weekly)

| Metric | Target | Warning | Action |
|--------|--------|---------|--------|
| **Trials Started** | ≥5/week | <2/week | Increase outreach |
| **Demo→Close Rate** | >20% | <10% | Improve demo; qualify better |
| **Support Tickets/Seat** | <0.1/week | >0.5/week | Improve docs; fix top issues |
| **NVIDIA API Cost/Seat** | <$0.10/mo | >$0.50/mo | Optimize prompts; increase Ollama |
| **Fix Loop Success Rate** | >80% | <60% | Engine improvements needed |

---

## 9. Scenario Modeling

### 9.1 Three-Year Projection

| Year | Conservative | Base | Optimistic |
|------|--------------|------|------------|
| **Customers (EoY)** | 20 | 100 | 300 |
| **Seats (EoY)** | 50 | 350 | 1,200 |
| **ARR** | $19,950 | $139,650 | $478,800 |
| **Variable Costs** | $1,500 | $10,500 | $36,000 |
| **Fixed Costs** | $1,200 | $1,500 | $2,500 |
| **Gross Profit** | $17,250 | $127,650 | $440,300 |
| **Gross Margin** | 86% | 91% | 92% |
| **Founder Comp (Draw)** | $0 | $30,000 | $80,000 |
| **Net Cash Flow** | $17,250 | $97,650 | $360,300 |

### 9.2 Key Assumptions by Scenario

| Assumption | Conservative | Base | Optimistic |
|------------|--------------|------|------------|
| **Monthly New Customers** | 1-2 | 3-8 | 10-25 |
| **Annual Churn** | 20% | 15% | 10% |
| **Expansion Rate** | 1.0x | 1.5x | 2.0x |
| **Lifetime %** | 15% | 25% | 35% |
| **Team Pack %** | 5% | 10% | 15% |
| **Enterprise %** | 0% | 5% | 10% |
| **Support Cost/Seat** | $50 | $30 | $20 |
| **CAC (Blended)** | $500 | $350 | $200 |

### 9.3 Sensitivity Tornado (Impact on Year 3 ARR)

```
High Impact (±$100K+):
├── Monthly Acquisition Rate (±50%) → ±$240K
├── Annual Churn (10% vs 20%) → ±$120K
├── Price Point ($299 vs $499) → ±$100K

Medium Impact (±$20-50K):
├── Expansion Rate (1.0x vs 2.0x) → ±$50K
├── Lifetime License Mix (15% vs 35%) → ±$30K
├── Support Cost/Seat ($20 vs $50) → ±$25K

Low Impact (<$10K):
├── NVIDIA API Cost (10x) → ±$5K
├── Fixed Cost Variance → ±$2K
├── Payment Processing Rate → ±$1K
```

---

## 10. Financial Guardrails

### 10.1 Monthly Health Metrics

| Metric | Formula | Green | Yellow | Red | Action at Red |
|--------|---------|-------|--------|-----|---------------|
| **Quick Ratio** | Cash / Monthly Burn | >12 | 6-12 | <6 | Cut costs; pause hiring |
| **LTV/CAC (Rolling 3mo)** | Blended LTV / Blended CAC | >4x | 3-4x | <3x | Fix acquisition or retention |
| **Net Revenue Retention** | (Expansion - Churn) / Starting ARR | >100% | 90-100% | <90% | Fix churn; drive expansion |
| **Gross Margin** | (Revenue - Variable) / Revenue | >90% | 80-90% | <80% | Audit variable costs |
| **Support Ratio** | Support Hrs / Total Seats | <0.5 | 0.5-1.0 | >1.0 | Automate; improve docs |

### 10.2 Quarterly Strategic Reviews

| Quarter | Focus | Key Questions |
|---------|-------|---------------|
| **Q1 (Launch)** | Product-Market Fit | Do pilots convert? Is fix loop reliable? |
| **Q2 (Traction)** | Channel Validation | Which channel scales? CAC sustainable? |
| **Q3 (Growth)** | Unit Economics | LTV/CAC > 3x? Gross margin > 90%? |
| **Q4 (Scale)** | Organizational | Hire? Partner? Raise? Stay solo? |

---

## 11. Capital Requirements

### 11.1 No External Capital Needed (Bootstrap)

| Capital Need | Amount | Source |
|--------------|--------|--------|
| **Pre-Launch (Months 1-3)** | $2,000 | Founder savings |
| **Domains, Tools, Incentives** | $500 | Revenue from R1 pilots |
| **Living Expenses (6 mo buffer)** | $15,000 | Founder savings / spouse income |
| **Total At Risk** | **$17,500** | |

### 11.2 When to Consider External Funding

| Trigger | Evaluation |
|---------|------------|
| **ARR > $100K with LTV/CAC > 5x** | Could accelerate with sales hire |
| **Enterprise demand > capacity** | Need dedicated success/support |
| **Competitor raises $10M+** | Defensive speed may require capital |
| **Platform expansion (MCP, IDE, Web)** | Parallel dev tracks need team |

**Default Stance:** Stay bootstrapped. Control > Speed for solo founder.

---

## 12. Tax & Compliance Considerations

| Item | Annual Cost | Notes |
|------|-------------|-------|
| **Business Entity (LLC)** | $800 (CA) / $300 (DE) | Varies by state |
| **Sales Tax Nexus** | Variable | Economic nexus thresholds ($100K/200 transactions in most states) |
| **International VAT/GST** | Complex | Use Paddle/ LemonSqueezy for MoR if selling globally |
| **Export Compliance (Encryption)** | $0 | Self-classification; EAR99 for dev tools |
| **Insurance (E&O, Cyber)** | $1,500-3,000 | Recommended at $10K+ ARR |
| **Total Compliance Reserve** | **$2,500-5,000/yr** | Build into pricing |

---

## 13. Conclusion: Unit Economics Verdict

### ✅ PASS — Unit Economics Support Sustainable Business

| Criterion | Threshold | Actual | Status |
|-----------|-----------|--------|--------|
| **Gross Margin** | >80% | 93% (base) | ✅ PASS |
| **LTV/CAC (3-yr)** | >3x | 4.8x (base) | ✅ PASS |
| **Payback Period** | <12 months | 6.8 months (at 100 seats) | ✅ PASS |
| **Break-Even Seats** | <50 | 4-5 seats | ✅ PASS |
| **Variable Cost % Revenue** | <20% | 7% (base) | ✅ PASS |
| **Fixed Cost Scalability** | <10% at 100 seats | 2.6% | ✅ PASS |

### ⚠️ CONDITIONS

1. **Support must stay ≤$30/seat/yr blended** — Email-only, self-service first
2. **CAC must stay ≤$350 blended** — Founder-led sales until $50K ARR
3. **Churn must stay ≤15%** — Annual contracts; strong onboarding
4. **NVIDIA free tier must hold** — Monitor; Ollama fallback ready

### 🔴 RED LINES (Pivot Triggers)

| Metric | Red Line | Action |
|--------|----------|--------|
| Blended Variable Cost > $100/seat/yr | Support model broken | Pivot to self-service only |
| Blended CAC > $1,000 | Channel economics broken | Pause growth; fix product |
| Churn > 25% | Product-market fit failed | Pivot to verification-only |
| Gross Margin < 70% | Cost structure broken | Audit every cost line |

---

*All figures are modeled scenarios based on stated assumptions. Not verified results. Update with real data from validation experiments (E1-E7, R1-R3).*