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
- A **Stop** toolbar button to cancel the run cleanly

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
- **View Detail** — inspect a snapshot's latency, status distribution, and inferred response schema (select one row, or double-click).
- **Compare (Diff)** — diff two snapshots side-by-side (select exactly two rows).
- **Assert...** — run `gg snap assert` between any two snapshots and see a pass/fail breakdown against configurable latency/error-rate/payload-size regression thresholds.
- **Prune...** — delete old snapshots by ID(s), tag, keep-last count, or age, with a dry-run preview before anything is actually deleted.

### 🚀 One-click CI workflow generator

**Generate CI Workflow...** (Tools menu, or right-click → **Gopher Glide (GG)**) scaffolds a ready-to-run `.github/workflows/gg.yml` implementing the full headless regression-gating loop:

- On push to `main`: run a headless simulation, snapshot it as the baseline, and cache it.
- On each PR: restore the latest baseline, run and snapshot the PR build, `gg snap assert` against the baseline, and post (or update) a sticky PR comment with the result — failing the job if a regression is detected.

The generated config path is pre-filled from the first `.gg.yaml` found in the project, and you're prompted before it overwrites an existing `gg.yml`.

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
- **Copy Diagnostics to Clipboard** collects OS, binary paths/permissions, and version info — useful for bug reports.

---

## Compatibility

- **IntelliJ Platform baseline:** `2024.2` (`sinceBuild = 242`)
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
