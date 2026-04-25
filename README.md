# Gopher Glide JetBrains Plugin

[![Build](https://github.com/shyam-s00/gg-idea-plugin/actions/workflows/build.yml/badge.svg)](https://github.com/shyam-s00/gg-idea-plugin/actions/workflows/build.yml)
[![Version](https://img.shields.io/jetbrains/plugin/v/dev.gopherglide.gg-plugin.svg?label=Jetbrains%20Plugin%20(beta))](https://plugins.jetbrains.com/plugin/30983-gopher-glide/versions/beta/1001254)

Gopher Glide brings fast execution and navigation for [Gopher-Glide (`gg`)](https://github.com/shyam-s00/gopher-glide) directly into JetBrains IDEs.

Run load tests, record performance snapshots, compare results over time, and navigate config files — all without leaving your editor.

---

## Features

### ▶ Run load tests from the editor

- **Gutter run icons** appear on `.gg.yaml` and `.http` files — click once to execute the test in the integrated terminal.
- **Context menu actions** are available in the Project View, Editor, and Editor Tab right-click menus under **Gopher Glide (GG)**.

### 📸 Snapshot recording

- **Run && Record** executes a load test and records a performance snapshot in one step.
- Prompts for an optional tag so snapshots are easy to identify later.
- Available for both `.gg.yaml` and `.http` files via gutter icons and the context menu.

### 🗂 Snaps Tool Window

A dedicated **Gopher Glide Snaps** panel (bottom toolbar) lists all recorded snapshots with key metrics:

| Column | Description |
|---|---|
| Tag | User-supplied label for the snapshot |
| Start Time | When the test run began |
| Total Requests | Aggregate request count |
| Peak RPS | Peak requests-per-second recorded |

Toolbar actions inside the panel:

- **Refresh** — reload the snapshot list from disk.
- **View Detail** — stream the full snapshot report in the terminal (select one row).
- **Compare (Diff)** — diff two snapshots side-by-side in the terminal (select exactly two rows).
- **Double-click** a row to view its details instantly.

### 🔗 YAML reference navigation

File path references inside `.gg.yaml` config files are clickable — `Ctrl+Click` / `Cmd+Click` jumps directly to the referenced file.

### ✅ JSON schema validation

`.gg.yaml` files are validated against the bundled Gopher-Glide JSON schema, giving inline errors and auto-complete for all config keys.

### ⚙️ Settings

**Settings / Preferences → Tools → Gopher Glide**

- Set a **custom `gg` binary path** if the executable is not on your `PATH`.
- If no path is configured and `gg` is not found, the plugin **automatically downloads** the latest release from the [Gopher-Glide repository](https://github.com/shyam-s00/gopher-glide) into a plugin-managed directory.

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

### Run a load test

1. Open a `.gg.yaml` or `.http` file.
2. Click the **run gutter icon**, or right-click → **Gopher Glide (GG) → Run**.
3. `gg` runs in the integrated terminal.

### Run and record a snapshot

1. Open a `.gg.yaml` or `.http` file.
2. Click the **record gutter icon**, or right-click → **Gopher Glide (GG) → Run && Record**.
3. Enter an optional tag when prompted.
4. The test runs and a snapshot is saved automatically.

### View and compare snapshots

1. Open the **Gopher Glide Snaps** panel at the bottom of the IDE.
2. Click **Refresh** to load all recorded snapshots.
3. Select a row and click **View Detail** (or double-click) to inspect results.
4. Select two rows and click **Compare** to diff them in the terminal.

### Configure the `gg` executable

1. Open **Settings / Preferences → Tools → Gopher Glide**.
2. Set a custom path to your `gg` binary (optional).

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
