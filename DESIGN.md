# Java Companion Orchestrator - Design System

**Version:** 1.0 | **Date:** 2026-10-31 | **Status:** Active

---

## 🎨 Design Philosophy

**Principle:** *Clarity over cleverness. Consistency over novelty. Accessibility by default.*

The Companion CLI is a developer tool first. Every design decision optimizes for:
- **Cognitive load reduction** - predictable patterns, clear feedback
- **Terminal-native aesthetics** - respects the medium, works everywhere
- **Progressive disclosure** - simple by default, powerful when needed

---

## 🎯 Brand Identity

| Attribute | Value |
|-----------|-------|
| **Name** | Companion (CLI: `companion`) |
| **Tagline** | Your code generation companion |
| **Tone** | Professional, approachable, precise |
| **Audience** | Java developers (0-6 months exp to seniors) |

---

## 🌈 Color Palette

### Semantic Colors (Terminal-Safe)

| Role | ANSI Code | Hex (GUI) | Usage |
|------|-----------|-----------|-------|
| **Success** | `\u001B[32m` (Green) | `#22c55e` | Passed checks, completed tasks |
| **Error** | `\u001B[31m` (Red) | `#ef4444` | Failures, critical errors |
| **Warning** | `\u001B[33m` (Yellow) | `#facc15` | Warnings, non-critical issues |
| **Info** | `\u001B[34m` (Blue) | `#3b82f6` | Progress, informational |
| **Muted** | `\u001B[2m` (Dim) | `#64748b` | Secondary text, details |
| **Accent** | `\u001B[36m` (Cyan) | `#06b6d4` | Highlights, interactive elements |
| **Bold** | `\u001B[1m` | - | Headers, emphasis |

### Color Usage Rules

1. **Never use color alone** - always pair with icon/text
2. **Respect `--no-color`** - disable all ANSI when flag set
3. **Auto-detect TTY** - disable in CI/non-interactive environments
4. **Minimum contrast** - all combinations meet WCAG AA in terminal

---

## 📝 Typography

### Text Styles (Terminal)

| Style | ANSI | Usage |
|-------|------|-------|
| **Header** | Bold + Cyan | Section titles (`═══ TITLE ═══`) |
| **Subheader** | Bold | Subsection labels |
| **Body** | Normal | Primary content |
| **Muted** | Dim | Secondary info, timestamps |
| **Code** | Bold + Cyan | File paths, commands, keys |
| **Link** | Underline + Blue | URLs (when supported) |

### Unicode Icons (Fallback: ASCII)

| Icon | Unicode | ASCII Fallback | Meaning |
|------|---------|----------------|---------|
| ✓ | `\u2713` | `[OK]` | Success/Pass |
| ✗ | `\u2717` | `[FAIL]` | Error/Fail |
| ⚠ | `\u26a0` | `[WARN]` | Warning |
| ℹ | `\u2139` | `[INFO]` | Info |
| ▸ | `\u25b8` | `>` | Prompt/Action |
| • | `\u2022` | `-` | Bullet |
| ⟳ | `\u27f3` | `~` | In Progress |
| 🚀 | `\u1f680` | `[*]` | Launch/Generate |

---

## 📐 Layout & Spacing

### Terminal Grid

```
┌─────────────────────────────────────────────────────────────┐
│  Header Section (full width)                                │
├─────────────────────────────────────────────────────────────┤
│  Content Area (80-120 chars optimal)                        │
│  ├─ Section Header (Cyan + Bold)                            │
│  │  Content rows...                                         │
│  ├─ Section Header                                           │
│  │  Table/List...                                           │
├─────────────────────────────────────────────────────────────┤
│  Footer / Status Bar                                         │
└─────────────────────────────────────────────────────────────┘
```

### Spacing Rules

| Element | Spacing |
|---------|---------|
| Section header top margin | 1 blank line |
| Section header bottom margin | 0 (content follows) |
| Between sections | 1 blank line |
| Table header separator | 1 line (`─` repeat) |
| List item indent | 2 spaces |
| Nested list indent | +2 spaces |
| Prompt prefix | `▸ ` (2 chars) |

### Table Formatting

```
┌─────────┬─────────┬─────────┐
│ KEY     │ VALUE   │ DETAILS │
├─────────┼─────────┼─────────┤
│ key1    │ value1  │ detail1 │
│ key2    │ value2  │ detail2 │
└─────────┴─────────┴─────────┘
```

- **Alignment:** Left-aligned keys, left-aligned values
- **Padding:** 1 space min, 2 spaces between columns
- **Truncation:** `...` suffix at 80% column width
- **Empty cells:** `(empty)` in muted text

---

## 🧩 Component Patterns

### 1. Status Checks (Doctor Command)

```
═══ DOCTOR - Prerequisite Checks ═══

✓ PASS  Java 21+          Java 21.0.2 found              OK
✗ FAIL  Maven 3.9+        Maven not found                Install Maven 3.9+
⟳ RUN   Docker 27+        Checking daemon...             Waiting...
```

### 2. Progress Rendering (Generation)

```
✓ Generating project: library
  ▸ Phase: Clarification - Waiting for questions...
  ▸ Tasks: 5 pending, 0 running, 0 done
```

### 3. Plan Review

```
═══ PLAN REVIEW ═══

Task: REST CRUD for Book entity...
Task ID: TASK-abc123
Project: library

Tasks:
┌───┬────────┬─────────────────┬───────┬───────────┐
│ # │ ID     │ Title           │ Files │ Depends On│
├───┼────────┼─────────────────┼───────┼───────────┤
│ 1 │ T-001  │ Book Entity     │ 3     │ -         │
│ 2 │ T-002  │ Repository      │ 2     │ T-001     │
└───┴────────┴─────────────────┴───────┴───────────┘

Options:
  1) Approve  - Start execution
  2) Reject   - Cancel with reason
  3) View     - Show full plan JSON
▸ Choose [1-3]:
```

### 4. Configuration Display

```
KEY          VALUE
────────────────────────────────────────
serverUrl    https://api.example.com:8443
defaultProject my-project
outputFormat table
timeoutSeconds 600
apiKey       abcd****wxyz
logLevel     INFO
noColor      false
```

### 5. Error Messages

```
✗ Cannot connect to orchestrator server
  Make sure the server is running at https://localhost:8443
  Or use --offline flag for local operations
```

---

## 🔄 Interaction Patterns

### Prompts

```
▸ Project name [my-project]:
▸ Description (Enter '?' for help): REST API for books
▸ Database [postgresql]: postgresql
▸ Features (comma-separated) [validation, pagination]: validation, pagination, auth
```

### Confirmations

```
▸ Approve plan and start generation? [Y/n]:
▸ Overwrite existing files? [y/N]:
```

### Multi-Select

```
Select features (comma-separated numbers, 'all', 'none') [1,3]:
  1) Validation ✓
  2) Pagination
  3) Auth ✓
  4) Rate Limiting
```

### Help Context

```
▸ Database type [postgresql]: ?
  Help: PostgreSQL recommended for production. Use H2 for quick local testing.
```

---

## ♿ Accessibility

### Terminal Accessibility

1. **No-color mode** - `--no-color` or `NO_COLOR=1` disables all ANSI
2. **High contrast** - semantic colors work on light/dark backgrounds
3. **Screen readers** - all info conveyed via text, icons are decorative
4. **Keyboard-only** - all interactions via keyboard
5. **Timeouts** - configurable, generous defaults (10min)

### Reduced Motion

- Spinners can be disabled via `--no-spinner` (future)
- Progress updates respect `--quiet` mode

---

## 📱 Responsive Behavior

| Terminal Width | Behavior |
|----------------|----------|
| < 60 chars | Stack tables vertically, single-column lists |
| 60-100 chars | Standard tables, wrapped text |
| > 100 chars | Full tables, side-by-side panels (future) |

---

## 🛠️ Implementation Notes

### ANSI Utilities (`Ansi.java`)

```java
// Semantic methods (preferred)
Ansi.success("Done");      // Green + ✓
Ansi.error("Failed");      // Red + ✗
Ansi.warning("Caution");   // Yellow + ⚠
Ansi.info("Starting...");  // Blue + ℹ
Ansi.dim("details");       // Muted

// Composable
Ansi.wrap(text, Ansi.RED, Ansi.BOLD);

// Feature detection
Ansi.isEnabled();          // TTY + --no-color check
Ansi.setEnabled(false);    // Force disable
```

### OutputFormatter (`OutputFormatter.java`)

```java
new OutputFormatter("table").format(data);    // Table
new OutputFormatter("json").format(data);     // JSON
new OutputFormatter("markdown").format(data); // Markdown
new OutputFormatter("plain").format(data);    // key=value
```

### TablePrinter (`TablePrinter.java`)

```java
TablePrinter.printTable(headers, rows);
TablePrinter.printMap(map);
```

---

## 🔮 Future Extensions

| Feature | Status | Notes |
|---------|--------|-------|
| Web Dashboard | v2+ | React + REST API |
| IntelliJ Plugin | v2+ | Embedded terminal support |
| Theming | v1.1 | User-defined color schemes |
| Localization | v1.2 | i18n for messages |
| Rich TUI | v2+ | ratatui/ftxui-based full-screen UI |

---

## ✅ Design Checklist for New Features

- [ ] Uses semantic colors (success/error/warning/info)
- [ ] Works with `--no-color` / `NO_COLOR=1`
- [ ] Degrades gracefully at <60 char width
- [ ] All icons have ASCII fallbacks
- [ ] Follows spacing rules (section margins, table padding)
- [ ] Error messages include remediation guidance
- [ ] Prompts have sensible defaults shown in brackets
- [ ] Help text available via `?` at prompts
- [ ] Tables truncate with `...` not overflow
- [ ] Progress shows actionable state (not just spinner)

---

## 📚 References

- [ANSI Escape Codes](https://gist.github.com/fnky/458719343aabd01cfb17a3a4f7296797)
- [WCAG 2.1 Contrast](https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html)
- [CLI Guidelines](https://clig.dev/)
- [Terminal Best Practices](https://www.iana.org/assignments/terminal-types/terminal-types.xhtml)

---

*This document is the single source of truth for CLI design. Update it when adding new commands or changing visual patterns.*