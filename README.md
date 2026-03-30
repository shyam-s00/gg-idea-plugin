# Gopher Glide JetBrains Plugin

[![Build](https://github.com/shyam-s00/gg-idea-plugin/actions/workflows/build.yml/badge.svg)](https://github.com/shyam-s00/gg-idea-plugin/actions/workflows/build.yml)
[![Version](https://img.shields.io/jetbrains/plugin/v/dev.gopherglide.gg-plugin.svg?label=Jetbrains%20Plugin%20(beta))](https://plugins.jetbrains.com/plugin/30983-gopher-glide/versions/beta/1001254)




Gopher Glide brings fast execution and navigation for [Gopher-Glide (`gg`)](https://github.com/shyam-s00/gopher-glide) directly into JetBrains IDEs.

It adds run actions for `.yaml` and `.http` files, makes references clickable, and runs `gg` in the IDE terminal without leaving your editor.

## Compatibility

- **IntelliJ Platform baseline:** `2024.2` (`sinceBuild = 242`)
- **Build target:** IntelliJ IDEA Community `2024.2.4`
- **Developer toolchain:** JDK 21

### Verified IDEs (2024.2 line)

These IDEs are explicitly covered in plugin verification:

- IntelliJ IDEA
- GoLand
- WebStorm
- Rider
- PyCharm Community
- PhpStorm
- RubyMine

If an IDE is based on the IntelliJ Platform `2024.2+` and includes required bundled plugins (`JSON`, `YAML`, and `Terminal`), it is likely to work. Marketplace compatibility rules still apply per product/version.

## Features

- **Run from editor or project view:** Execute `gg` directly from `.yaml` and `.http` files.
- **Gutter actions:** Launch load tests quickly with one click from supported files.
- **YAML reference navigation:** Jump through file references in config files.
- **Automatic binary bootstrap:** Download `gg` if it is missing from your `PATH`.
- **Custom executable path:** Point the plugin to a specific local `gg` binary.


## Installation

### JetBrains Marketplace

1. Open **Settings/Preferences** (`Ctrl+Alt+S` / `Cmd+,`).
2. Go to **Plugins** > **Marketplace**.
3. Search for **Gopher Glide**.
4. Click **Install** and restart the IDE if prompted.

[View on JetBrains Marketplace](https://plugins.jetbrains.com/plugin/30983-gopher-glide)

### Manual (ZIP)

1. Download the latest plugin ZIP from [Releases](https://github.com/shyam-s00/gg-idea-plugin/releases).
2. Open **Settings/Preferences** > **Plugins**.
3. Click the gear icon and choose **Install Plugin from Disk...**.
4. Select the ZIP file and restart the IDE.

## Usage

### Run a load test

1. Open a `.yaml` or `.http` file.
2. Click the run gutter icon, or right-click and choose the Gopher Glide run action.
3. The plugin runs `gg` in the integrated terminal.

### Configure `gg` executable

1. Open **Settings/Preferences**.
2. Navigate to **Tools** > **Gopher Glide**.
3. Set a custom `gg` path (optional).

If no path is configured and `gg` is not in `PATH`, the plugin downloads the latest release from the [Gopher-Glide repository](https://github.com/shyam-s00/gopher-glide) into a plugin-managed directory.

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
