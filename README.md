# Gopher Glide JetBrains Plugin

[![Build](https://github.com/shyam-s00/gg-idea-plugin/actions/workflows/build.yml/badge.svg)](https://github.com/shyam-s00/gg-idea-plugin/actions/workflows/build.yml)
[![Version](https://img.shields.io/jetbrains/plugin/v/dev.gopherglide.gg-plugin.svg?label=Jetbrains%20Plugin%20)](https://plugins.jetbrains.com/plugin/30983-gopher-glide/versions/beta/1001254)

Gopher Glide brings fast execution and navigation for [Gopher-Glide (`gg`)](https://gopherglide.dev/) — a zero-scripting API traffic simulation and performance benchmarking CLI — directly into JetBrains IDEs.

Run traffic simulations, record performance snapshots, compare results over time, and navigate config files — all without leaving your editor.

---

## Features

### ▶ Run traffic simulations from the editor

- **Gutter run icons** appear on `.gg.yaml` and `.http` files:
  - **Run** — executes the simulation in the native Gopher Glide run panel (see below).
  - **Run && Record** — same, plus records a performance snapshot.
  - **Run in Terminal (Interactive)** — runs gg's full interactive terminal UI instead, with live ↑ / ↓ RPS-bias control.
- **Context menu actions** (Run / Run && Record) are available in the Project View, Editor, and Editor Tab right-click menus under **Gopher Glide (GG)**.
- Running a `.http` file with no sibling `.gg.yaml` scaffolds a starter `traffic-sim.gg.yaml` and opens it for editing.

### 🖥 Native run panel

Runs default to a lightweight **Run** tab in the Gopher Glide tool window instead of rendering gg's interactive terminal UI inside the IDE — avoiding the CPU overhead of a full TUI redraw loop. While a simulation runs, it shows live:

- Current stage, elapsed time, and overall status
- Target vs. actual RPS, error rate, total requests
- p50 / p95 / p99 latency
- A sparkline of recent RPS history
- A **Stop** button to cancel the run cleanly

Prefer gg's full interactive TUI — for example, for live arrow-key RPS control? Use **Run in Terminal (Interactive)** instead; it's always one click away.

### 📸 Snapshot recording

- **Run && Record** executes a traffic simulation and records a performance snapshot in one step.
- Prompts for an optional tag so snapshots are easy to identify later.
- Available for both `.gg.yaml` and `.http` files via gutter icons and the context menu.

### 🗂 Gopher Glide tool window

A single **Gopher Glide** tool window (bottom toolbar) holds two tabs:

- **Run** — the native run panel described above.
- **Snaps** — lists all recorded snapshots:

| Column | Description |
|---|---|
| ID / Tag | Numeric ID plus the user-supplied tag (or "(untagged)") |
| Date | When the test run began |
| Total Requests | Aggregate request count |
| Peak RPS | Peak requests-per-second recorded |

Toolbar actions inside the Snaps tab:

- **Refresh** — reload the snapshot list from disk.
- **View Detail** — stream the full snapshot report in the terminal (select one row).
- **Compare (Diff)** — diff two snapshots side-by-side in the terminal (select exactly two rows).
- **Double-click** a row to view its details instantly.

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

Any IDE based on IntelliJ Platform `2024.2+` that includes the bundled `JSON`, `YAML`, and `Terminal` plugins is likely to work.

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
2. Click the **run gutter icon**, or right-click → **Gopher Glide (GG) → Run**.
3. The simulation runs in the **Gopher Glide → Run** tool window panel, with live metrics.
4. Want gg's full interactive TUI instead? Use the gutter's **Run in Terminal (Interactive)** action.

### Run and record a snapshot

1. Open a `.gg.yaml` or `.http` file.
2. Click the **record gutter icon**, or right-click → **Gopher Glide (GG) → Run && Record**.
3. Enter an optional tag when prompted.
4. The simulation runs and a snapshot is saved automatically.

### View and compare snapshots

1. Open the **Gopher Glide** tool window at the bottom of the IDE and switch to the **Snaps** tab.
2. Click **Refresh** to load all recorded snapshots.
3. Select a row and click **View Detail** (or double-click) to inspect results.
4. Select two rows and click **Compare** to diff them in the terminal.

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
