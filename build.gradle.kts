plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "dev.gopherglide.gg-plugin"
version = project.findProperty("pluginVersion") ?: "3.9.9"

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
        bundledPlugin("org.jetbrains.plugins.terminal")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
        }

        changeNotes = """
            <b>Stability fix for the new gg interactive TUI</b><br>
            A recent gg update increased the interactive TUI's redraw rate, which could pin a CPU core and freeze or crash the IDE when running a simulation from the plugin. This release fixes that:
            <ul>
            <li>Runs now default to a new, lightweight native <b>"Gopher Glide" run panel</b> showing live stage progress, RPS, error rate, and latency — instead of rendering gg's full terminal UI inside the IDE.</li>
            <li>The full interactive TUI, including live &uarr;/&darr; RPS-bias control, is still available as an explicit "Run in Terminal (Interactive)" option for anyone who wants it.</li>
            <li>Snaps and the new Run panel are now combined into one "Gopher Glide" tool window with tabs, instead of two separate sidebar icons.</li>
            <li>Improved first-run experience: the plugin now proactively detects a missing gg binary and offers to install it, with visible download progress, instead of silently hanging on your first Run click.</li>
            <li>Terminology aligned with gg's "traffic simulation" branding throughout the plugin.</li>
            </ul>
            <b>Coming next:</b> a one-click built-in profile picker for zero-config runs, native IntelliJ Run Configuration support, and request-chaining navigation/completion for <code>@gg-export</code> variables.
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
