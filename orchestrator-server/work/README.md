# Work Tracking Folder

This folder contains all tracking files for the Java Companion Orchestrator build.

## Files

| File | Purpose | Update Frequency |
|------|---------|------------------|
| `DAILY_LOG.md` | One paragraph per day: what, blockers, decisions, tomorrow | **Daily** (end of session) |
| `PROGRESS.md` | Phase/Step completion checkboxes with dates | **Per step completion** |
| `BLOCKERS.md` | Active/resolved blockers with severity | **When blocker appears/resolved** |
| `DECISIONS.md` | Architecture decisions with rationale | **Per decision** |

---

## Update Rules

1. **DAILY_LOG.md** - Write at END of each coding session. One paragraph. Note blockers immediately.
2. **PROGRESS.md** - Check off sub-tasks immediately when done. Update Gate checklist before phase transition.
3. **BLOCKERS.md** - Add IMMEDIATELY when blocked. Move to Resolved when unblocked.
4. **DECISIONS.md** - Write BEFORE implementing. Reference in code comments.

---

## Recovery Protocol

If you lose chat context or switch machines:

1. Read `ORCHESTRATOR_PLAN.md` for full plan
2. Read `work/DAILY_LOG.md` (last 3 entries) for context
3. Read `work/PROGRESS.md` for current phase/step
4. Read `work/BLOCKERS.md` for active blockers
5. Read `work/DECISIONS.md` for architectural context
6. Resume from `PROGRESS.md` current step

---

## File Locations

```
orchestrator/
├── ORCHESTRATOR_PLAN.md        # Complete plan (this is the source of truth)
├── work/
│   ├── DAILY_LOG.md            # Daily session log
│   ├── PROGRESS.md             # Phase/Step checkboxes
│   ├── BLOCKERS.md             # Blocker tracker
│   └── DECISIONS.md            # Architecture decisions
├── engine/                     # Core engine (Maven multi-module)
├── apps/app-cli/               # CLI application
├── eval/                       # Evaluation suite
└── docs/                       # VERSIONS.md, DIARY.md (symlink to work/DAILY_LOG.md)
```

---

## Symlink Setup (Optional)

```bash
# From orchestrator/ directory
ln -s work/DAILY_LOG.md docs/DIARY.md
```

This makes `docs/DIARY.md` point to the same file as `work/DAILY_LOG.md`.