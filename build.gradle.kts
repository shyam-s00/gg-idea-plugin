plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "dev.gopherglide.gg-plugin"
version = project.findProperty("pluginVersion") ?: "6.9.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2.4")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("org.jetbrains.plugins.yaml")
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
        }

        changeNotes = """
            <b>A completely overhauled Snapshot View and Diff experience</b><br>
            This release is a full visual and functional rework of the native Snap dialogs, bringing them to parity with (and beyond) what <code>gg</code>'s terminal TUI shows:
            <ul>
            <li><b>Snapshot View</b> — a rich metadata header (date, requests, peak RPS, profile, config hash, sampling rate) above an endpoint table with a live-updating detail pane: an inferred JSON schema tree (with real type icons, and object/array/string/number distinctions) and a status-code distribution chart, including a synthetic "connection errors" bucket for failures that never received an HTTP status.</li>
            <li><b>Snapshot Diff</b> — the compare view gets the same treatment, plus a comparative Baseline/Target header, a mismatch warning banner when the two snapshots used different profiles or configs, and a full <b>schema diff</b> (added/removed/changed fields, colored consistently with the rest of the diff table) alongside a side-by-side status distribution comparison.</li>
            <li><b>Delta-first diff formatting</b> — endpoint and schema diff cells now show the current value with a ▲/▼ delta instead of a cluttered "before → after" pair, with NEW/GONE/CHANGED tags for added, removed, and modified fields.</li>
            <li><b>Search and column chooser</b> on both endpoint tables — filter rows live, and show/hide columns (Max Payload, P50/P99, etc.) with your choice remembered across restarts.</li>
            <li><b>Profile-aware comparisons</b> — snapshots now record which load profile generated them, surfaced in both dialogs and used to detect apples-to-oranges comparisons; legacy snapshots without a profile fall back to comparing config hashes.</li>
            <li>Fixed a handful of longstanding bugs</li>
            </ul>
        """.trimIndent()
    }

    publishing {
        token = System.getenv("PUBLISH_TOKEN")
        channels = listOf(project.findProperty("pluginChannel")?.toString() ?: "default")
    }

    pluginVerification {
        ides {
            val ideToVerify = project.findProperty("pluginVerificationIde") as? String
            when (ideToVerify) {
                "GO" -> create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.GoLand, "2024.2")
                "WS" -> create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.WebStorm, "2024.2")
                "RD" -> create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.Rider, "2024.2")
                "PC" -> create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.PyCharmCommunity, "2024.2")
                "PS" -> create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.PhpStorm, "2024.2")
                "RM" -> create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.RubyMine, "2024.2")
                "ALL" -> {
                    recommended()
                    create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.GoLand, "2024.2")
                    create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.WebStorm, "2024.2")
                    create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.Rider, "2024.2")
                    create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.PyCharmCommunity, "2024.2")
                    create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.PhpStorm, "2024.2")
                    create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.RubyMine, "2024.2")
                }
                else -> recommended() // Default to IDEA Community (IC) for local builds and the 'IC' matrix job
            }
        }
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
