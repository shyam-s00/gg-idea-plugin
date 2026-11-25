# Gopher Glide JetBrains Plugin

[![Build](https://github.com/shyam-s00/gg-idea-plugin/actions/workflows/build.yml/badge.svg)](https://github.com/shyam-s00/gg-idea-plugin/actions/workflows/build.yml)
[![Version](https://img.shields.io/jetbrains/plugin/v/dev.gopherglide.gg-plugin.svg?label=Jetbrains%20Plugin%20)](https://plugins.jetbrains.com/plugin/30983-gopher-glide/versions/beta/1001254)

Gopher Glide brings fast execution and navigation for [Gopher-Glide (`gg`)](https://gopherglide.dev/) — a zero-scripting API traffic simulation and performance benchmarking CLI — directly into JetBrains IDEs.

Run traffic simulations, record and compare performance snapshots, gate regressions in CI, and navigate config files — all without leaving your editor or dropping into a terminal.

---

## Features

### ▶ Run traffic simulations from the editor

- **Gutter run icons** on `.http` files:
  - **Run GG** — opens a profile picker with all 21 of gg's built-in load profiles (grouped by category: E-Commerce, Standard Testing/CI, Resilience/Chaos, Auto-Scaling, Specialized), then lets you override peak RPS/duration and optionally record a snapshot before running.
  - **⚡ Autopilot: Find Ceiling…** (top of the picker, gg v1.5.0+) — gg steps the rate up while health holds, marks where it breaches, confirms the rate it backs off to, and reports the highest sustainable RPS. No LLM involved.
  - **Run GG (Config)** — runs the file's sibling `.gg.yaml` config directly, no profile or overrides — the config owns everything.
  - **Generate config.yaml...** — scaffolds a starter `.gg.yaml` for the file without running anything.
- **Gutter run icon** on `.gg.yaml` files:
  - **Run GG** — prompts for optional snapshot recording (with a tag), then runs the config as-is.
- The same actions are available from the right-click **Gopher Glide (GG)** menu in the Project View, Editor, and Editor Tab popups, grouped into **Run**, **Generate**, and **CI** sections.
- Runs always go through the native run dashboard below — there's no interactive terminal TUI launch path, which avoids a CPU-pinning redraw-rate issue in gg's TUI that could otherwise freeze or crash the IDE.

### 🖥 Native run dashboard

The **Run** tab in the Gopher Glide tool window shows live, updating roughly once per heartbeat:

- Current status and elapsed time
- Target vs. actual RPS, error rate, and total requests (metric cards)
- p50 / p95 / p99 latency
- A scaled **RPS chart** and a **stage timeline** showing progress through the simulation's stages
- A **Stop** toolbar button that ends the run cleanly: gg finishes writing the snapshot first, and a second click forces the process to end if it is stuck
- With gg v1.5.0+: hover the error-rate and latency cards for the last few seconds' health next to the cumulative figures, and a one-line **Recent errors** strip names the failure classes and the endpoints failing most

#### Live control (gg v1.3.0+)

With gg v1.3.0 or newer, a **Live Control** strip sits above the metric cards while a run is in progress:

- **Bias** — **−10 / −5 / +5 / +10** change the target RPS without stopping the run, and **Reset** returns it to the planned rate. The badge shows the bias gg reports, so it always matches the run.
- **Mark…** — drop a named marker ("cache-warm", "deploy-v2") at the current point of the run. The last mark and a count appear under the controls; hover for the full list.
- **On the chart** — each bias change is a tick on the target line and each mark is a dashed line with its label, so you can see which change moved the numbers.
- With gg v1.5.0+, a mark is stamped when you click **Mark…**, not when you confirm its label. With older versions it is stamped when you confirm.

With gg v1.5.0 or newer the strip also has:

- **Hold RPS** — pin the rate at a number you choose, ignoring the profile's ramp, until you click **Release Hold**. A **📌 Hold N RPS** badge shows while it is in force.
- **🛡️ Guard SLO…** — start gg's built-in SLO guard mid-run with your own p99 and error-rate limits. It clamps the rate when health breaches, then releases it in steps. While an autopilot runs, bias and hold are off and **Stop Autopilot (Take Over)** hands the rate back; a hold it set stays until you release it.
- Marks the autopilot drops itself ("ceiling breached at 35 RPS…") appear on the chart with a ⚡ prefix; yours carry 👤.

You can also protect a whole run with the SLO guard from the run dialogs (**Protect run with Autopilot SLO Guard**).

With an older gg, the strip is replaced by a short notice that names the installed version and offers to install or upgrade (see below). The run itself works as before.

### 📸 Snapshot recording

Recording is a checkbox inside the same run flow, not a separate action — tick **Record a snapshot** (with an optional tag) in the run dialog/profile picker and a performance snapshot is saved automatically alongside the run.

### 🗂 Gopher Glide tool window

A single **Gopher Glide** tool window (bottom toolbar) holds two tabs:

- **Run** — the native run dashboard described above.
- **Snaps** — lists all recorded snapshots:

| Column | Description |
|---|---|
| ID / Tag | Numeric ID plus the user-supplied tag (or "(untagged)") |
| Date | When the test run began |
| Total Requests | Aggregate request count |
| Peak RPS | Peak requests-per-second recorded |

Toolbar actions inside the Snaps tab — all native dialogs, nothing streams to a terminal:

- **Refresh** — reload the snapshot list from disk.
- **View Detail** — a master-detail view: a metadata header (date, requests, peak RPS, profile, config hash, sampling rate) above a searchable, column-configurable endpoint table, with a detail pane showing the endpoint's inferred JSON schema (as a tree with real type icons, presence %, and stability) and its status-code distribution — including a synthetic "connection errors" bucket for failures that never returned an HTTP status (select one row, or double-click). For runs recorded with gg v1.3.0+, a collapsible **Manual changes** section lists every mark, bias change and hold in time order with who made it (👤 you, ⚡ the autopilot), with four figures beside it: final bias, peak bias, how long a bias was in effect, and when the first change happened. Snapshots from older gg versions say that this was not recorded. Runs steered by the autopilot also show an outcome banner above the list ("Ceiling found: 28 RPS", the limits, and why it backed off).
- **Compare (Diff)** — the same master-detail layout for comparing two snapshots: a comparative Baseline/Target header, a mismatch warning if the two runs used different profiles or configs, and a detail pane with a full **schema diff** (added/removed/changed fields) and a side-by-side status distribution comparison. Endpoint and schema changes are shown as a current value plus a ▲/▼ delta, with NEW/GONE/CHANGED tags, instead of a "before → after" pair (select exactly two rows). When either run had marks, bias changes, holds or an autopilot, a one-line notice says so, because those runs may not be like for like.
- **Assert...** — run `gg snap assert` between any two snapshots and see a pass/fail breakdown against configurable latency/error-rate/payload-size regression thresholds.
- **Export Mission Report…** (gg v1.5.0+) — save the selected snapshot's `gg snap report` as Markdown or HTML (by file extension).
- **Copy PR Markdown** (gg v1.5.0+) — put the same report on the clipboard as Markdown, ready for a pull request description.
- **Prune...** — delete old snapshots by ID(s), tag, keep-last count, or age, with a dry-run preview before anything is actually deleted.

### 🚀 One-click CI workflow generator

**Generate CI Workflow...** (Tools menu, or right-click → **Gopher Glide (GG)**) scaffolds a ready-to-run `.github/workflows/gg.yml` implementing the full headless regression-gating loop:

- On push to `main`: run a headless simulation, snapshot it as the baseline, and cache it.
- On each PR: restore the latest baseline, run and snapshot the PR build, `gg snap assert` against the baseline, and post (or update) a sticky PR comment with the result — failing the job if a regression is detected.

The generated config path is pre-filled from the first `.gg.yaml` found in the project, and you're prompted before it overwrites an existing `gg.yml`.

### 🤖 AI agents (gg v1.5.0+)

**Tools → Connect AI Agents to Gopher Glide…**, or the **AI Agents** button in the Run tab, connects Claude Code, Cursor, Junie, JetBrains AI Assistant and JetBrains Air to gg, so a run an agent starts shows up live in the Run tab like one of yours.

- **One dialog, nothing silent.** It shows what is linked now (the shared daemon, `.mcp.json`, the agent skill, Air), previews every change (`Preview changes…`), and writes only what you tick. Files are written by `gg mcp init`, which keeps your other servers and agents and never overwrites an entry of your own unless you choose **Replace**. A broken JSON file is reported and left alone.
- **Workspace `.mcp.json`** gets a `gg` server that attaches to the IDE's daemon (`gg mcp --attach`), so agent runs are visible and steerable here. gg's own default entry starts a private server instead, which the IDE never sees; the dialog upgrades it. When the entry holds a full path to gg, the dialog offers to keep `.mcp.json` out of git.
- **Agent skill** (`.claude/skills/gg/SKILL.md`) teaches agents the workflow. It is never overwritten once you have edited it.
- **JetBrains Air**: registers `gg autopilot` in `~/.jetbrains/acp.json` through a small launcher in `~/.jetbrains/gopher-glide/` that connects the agent to your project's daemon when it is running. Its slash commands (`/dry-run`, `/find-ceiling`, `/slo-guard`, `/triage`) need no API key; an optional section takes a Gemini key or an OpenAI-compatible server for open-ended questions (kept in a file only you can read).
- **In the Run tab:** an agent's run opens with a **🤖 STARTED BY AI AGENT** badge, and its marks, bias steps and holds appear on the chart with a 🤖 prefix. If you are mid-run, it waits and takes the tab when yours ends. **Stop** and the live controls work on it too.
- **Your runs stay yours.** AI agents can read a run you started but not steer it until you tick **🤖 Allow AI Steer** (off for every new run).
- **Limits for agents** (max RPS, max duration, runs at once, and which hosts they may target) are in the dialog and in **Settings → Gopher Glide → Shared daemon**. By default agents may only target the hosts found in this project's `.http` files; your own runs are never limited.
- The shared daemon is one gg process per project. It starts when you open a connected project (or on your first routed run), and it stops with the IDE. Turn on **Run my simulations through the shared daemon** in Settings to put your own runs through it as well (off by default; a run that can't use it simply runs directly).

### 📄 Scaffold a new test file

**File → New → Add GG http file** creates a `.http` file pre-filled with a sample request and a cheat sheet of all built-in gg profiles in a comment header.

### 🔗 YAML reference navigation

File path references inside `.gg.yaml` config files are clickable — `Ctrl+Click` / `Cmd+Click` jumps directly to the referenced file.

### ✅ JSON schema validation

`.gg.yaml` files are validated against the bundled Gopher-Glide JSON schema — including `stages[].name` and the optional `snap:` block — giving inline errors and auto-complete for all config keys.

### ⚙️ Settings & first-run onboarding

**Settings / Preferences → Tools → Gopher Glide**

- Set a **custom `gg` binary path**, or a **custom snapshots directory**.
- If no path is configured and `gg` isn't found, the plugin **automatically downloads** the latest release from the [Gopher-Glide repository](https://github.com/shyam-s00/gopher-glide) into a plugin-managed directory — with visible download progress, and a notification offering to install it the moment you open a project, instead of silently hanging on your first Run click.
- **Check for Updates** (shown as **Install Gopher Glide** when nothing's installed yet) reports your local vs. latest version and updates with one click.
- **Check for gg updates on startup** (on by default) looks for a newer release at most once a day and shows one notification with **Update Now**, **Open Settings** and **Skip This Version**. It stays quiet when you are offline, already current, or use a custom binary path.
- The **Run** tab shows what it knows about the installed `gg` before you start: a short note when it supports live control, or an explanation with **Upgrade** (plugin-managed binary), **Install** (a plugin-managed copy that takes priority over the one on your `PATH`, which is left untouched) or **Open Settings** (custom path) when it does not.
- **Copy Diagnostics to Clipboard** collects OS, binary paths/permissions, and version info — useful for bug reports.

---

## Compatibility

- **IntelliJ Platform baseline:** `2024.2` (`sinceBuild = 242`)
- **Live control:** needs `gg` v1.3.0 or newer; older versions run as before, without the controls
- **Hold, Autopilot, click-time marks, window health, mission reports and AI agents:** need `gg` v1.5.0 or newer; the plugin offers to install it when you ask for one of them on an older binary
- **Build target:** IntelliJ IDEA Community `2024.2.4`
- **Developer toolchain:** JDK 21

### Verified IDEs (2024.2 line)

| IDE | |
|---|---|
| IntelliJ IDEA | ✅ |
| GoLand | ✅ |
| WebStorm | ✅ |
| Rider | ✅ |
| PyCharm Community | ✅ |
| PhpStorm | ✅ |
| RubyMine | ✅ |

Any IDE based on IntelliJ Platform `2024.2+` that includes the bundled `JSON` and `YAML` plugins is likely to work.

---

## Installation

### JetBrains Marketplace

1. Open **Settings / Preferences** (`Ctrl+Alt+S` / `Cmd+,`).
2. Go to **Plugins → Marketplace**.
3. Search for **Gopher Glide**.
4. Click **Install** and restart the IDE if prompted.

[View on JetBrains Marketplace](https://plugins.jetbrains.com/plugin/30983-gopher-glide)

### Manual (ZIP)

1. Download the latest plugin ZIP from [Releases](https://github.com/shyam-s00/gg-idea-plugin/releases).
2. Open **Settings / Preferences → Plugins**.
3. Click the gear icon and choose **Install Plugin from Disk...**.
4. Select the ZIP file and restart the IDE.

---

## Usage

### Run a traffic simulation

1. Open a `.gg.yaml` or `.http` file.
2. Click the **Run GG** gutter icon, or right-click → **Gopher Glide (GG) → Run**.
3. For `.http` files, pick a profile from the popup (override peak RPS/duration and toggle snapshot recording if you like).
4. The simulation runs in the **Gopher Glide → Run** tool window panel, with live metrics, an RPS chart, and a stage timeline.

### Steer a run while it is going

1. Start a run with gg v1.3.0 or newer; the **Live Control** strip appears in the **Run** tab.
2. Use **+5 / +10 / −5 / −10** to raise or lower the load and **Reset** to go back to the plan. With gg v1.5.0+, **Hold RPS** pins the rate instead.
3. Click **Mark…**, type a label and confirm, to note an event such as a deploy.
4. Click **Stop** when you are done. With snapshot recording on, the snapshot is saved before the run ends.

### Connect an AI agent

1. Choose **Tools → Connect AI Agents to Gopher Glide…** (or click **AI Agents** in the Run tab).
2. Tick what to set up, check **Preview changes…** if you like, and click **Apply**. Restart your AI agent, or the IDE for Air, to pick it up.
3. Ask the agent to run a simulation. It appears in the **Run** tab with a 🤖 badge. To let it steer a run you started, tick **🤖 Allow AI Steer** in the Live Control strip.

### Find your system's ceiling

1. Open a `.http` file and click the **Run GG** gutter icon.
2. Choose **⚡ Autopilot: Find Ceiling…** at the top of the picker.
3. Set the starting rate, the cap, your p99 and error-rate limits, and a maximum duration (at least 30 seconds), then run.
4. The **Run** tab shows the autopilot's progress; when it finishes, the status line gives the result, and with a snapshot recorded the Snapshot View shows the same outcome banner.

### Share a run

1. In the **Snaps** tab, select a snapshot.
2. Click **Export Mission Report…** to save it as `.md` or `.html`, or **Copy PR Markdown** to paste it into a pull request.

### Record a snapshot while running

1. Follow the steps above; in the run dialog/profile picker, tick **Record a snapshot**.
2. Enter an optional tag when prompted.
3. The simulation runs and a snapshot is saved automatically.

### View, compare, assert, and prune snapshots

1. Open the **Gopher Glide** tool window at the bottom of the IDE and switch to the **Snaps** tab.
2. Click **Refresh** to load all recorded snapshots.
3. Select a row and click **View Detail** (or double-click) to inspect latency, status distribution, and schema.
4. Select two rows and click **Compare** to diff them.
5. Click **Assert...** to run `gg snap assert` between two snapshots and see a pass/fail breakdown.
6. Click **Prune...** to delete old snapshots by ID, tag, keep-last count, or age (dry-run preview by default).

### Generate a CI workflow

1. Right-click anywhere in the project (or use **Tools → Generate CI Workflow...**).
2. Confirm the detected `.gg.yaml` path, or let it fall back to a placeholder you can fill in.
3. Confirm overwrite if a `.github/workflows/gg.yml` already exists.
4. Commit the generated workflow — it captures a `main`-branch baseline and asserts every PR's snapshot against it, posting the result as a PR comment.

### Configure the `gg` executable

1. Open **Settings / Preferences → Tools → Gopher Glide**.
2. Set a custom path to your `gg` binary, or a custom snapshots directory (optional).

---

## Development

### Prerequisites

- JDK 21
- Gradle wrapper (included)

### Build and run

```bash
./gradlew build
./gradlew runIde
```

### Verify against multiple IDEs

```bash
./gradlew verifyPlugin
./gradlew verifyPlugin -PpluginVerificationIde=ALL
```

### Release

| Workflow | Trigger | What it does |
|---|---|---|
| `release-github.yml` | GitHub Release published | Verifies across all IDEs, builds and attaches ZIP to the release |
| `publish-marketplace.yml` | Manual (`workflow_dispatch`) | Publishes the specified tag to JetBrains Marketplace |
