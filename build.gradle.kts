plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "dev.gopherglide.gg-plugin"
version = project.findProperty("pluginVersion") ?: "5.0.0"

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
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
        }

        changeNotes = """
            <b>A complete, terminal-free Snap workflow, plus a richer run dashboard</b><br>
            This release rounds the plugin out into a full regression-testing loop you never need to leave the editor for:
            <ul>
            <li><b>Native snapshot viewer &amp; diff</b> — view a snapshot's latency, status distribution, and inferred schema, or diff two snapshots side-by-side, without dropping into gg's interactive terminal TUI.</li>
            <li><b>Snap Assert from the IDE</b> — run <code>gg snap assert</code> between any two snapshots and see a pass/fail breakdown in a native dialog.</li>
            <li><b>Snap Prune from the IDE</b> — clean up old snapshots by ID, tag, keep-last count, or age, with a dry-run preview before anything is deleted.</li>
            <li><b>One-click CI Workflow Generator</b> — a new "Generate CI Workflow..." action scaffolds a ready-to-run GitHub Actions workflow implementing headless run &rarr; snap &rarr; assert &rarr; PR comment, matching the pattern documented at gopherglide.dev/snap.</li>
            <li><b>Built-in profile picker</b> — run any <code>.http</code> file against a profile (smoke, load, stress, soak, spike) with zero config, with an override dialog for one-off tweaks.</li>
            <li><b>Richer run dashboard</b> — the native run panel now shows a live stage timeline and RPS chart alongside error rate and latency percentiles.</li>
            <li>Reorganized the right-click "Gopher Glide (GG)" menu into logical groups (Run, Generate, CI) and renamed "Gopher-Glide Test" to "Add GG http file" for clarity.</li>
            <li>Simplified <code>.http</code>/<code>.gg.yaml</code> run actions down to two clear entry points, <b>"Run GG"</b> and <b>"Run GG (Config)"</b>, replacing the previous set of overlapping run/record actions.</li>
            <li>Added a <b>"Run panel refresh interval"</b> setting (Settings &rarr; Gopher Glide) to control how often the run dashboard updates.</li>
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
