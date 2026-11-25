plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "dev.gopherglide.gg-plugin"
version = project.findProperty("pluginVersion") ?: "7.9.0"

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
            <b>AI agents on the shared daemon (needs gg v1.5.0+)</b><br>
            <ul>
            <li><b>Connect AI agents</b> — one dialog (Tools menu, or the AI Agents button in the Run tab) links Claude Code, Cursor, Junie, JetBrains AI Assistant and JetBrains Air. It shows what is linked, previews every change, keeps your other servers and agents, and never overwrites an entry of yours unless you choose Replace.</li>
            <li><b>Agent runs show live</b> — a run an agent starts opens in the Run tab with a badge; its marks, bias steps and holds appear on the chart. A run that starts while you are mid-run waits for yours to end.</li>
            <li><b>Your runs stay yours</b> — agents can read them but cannot steer them until you tick Allow AI Steer. Agents may only target the hosts in your project's .http files unless you say otherwise.</li>
            <li><b>Optional</b> — run your own simulations through the shared daemon too (Settings → Gopher Glide → Shared daemon).</li>
            </ul>
            <b>Autopilot, rate hold and shareable reports (needs gg v1.5.0+)</b><br>
            <ul>
            <li><b>Find your ceiling</b> — pick <i>Autopilot: Find Ceiling…</i> at the top of the profile picker. gg steps the rate up while health holds, marks the breach, confirms the rate it backs off to, and reports the highest sustainable RPS. No LLM involved.</li>
            <li><b>Guard a run</b> — tick <i>Protect run with Autopilot SLO Guard</i> in the run dialogs, or press <i>Guard SLO…</i> mid-run, to clamp the rate when p99 or the error rate breaches your limits. <i>Stop Autopilot (Take Over)</i> hands the rate back.</li>
            <li><b>Hold RPS</b> — pin the rate at a number you choose until you release it.</li>
            <li><b>Marks are stamped when you click</b> — typing the label no longer shifts the mark's position on the chart.</li>
            <li><b>Know what is failing</b> — a Recent errors strip names the failure classes and the endpoints failing most, and the error and latency cards show the last few seconds' health on hover.</li>
            <li><b>Autopilot in snapshots</b> — an outcome banner, who made each change (you or the autopilot), holds and releases in the timeline, and a diff notice when a run was steered.</li>
            <li><b>Share a run</b> — export a snapshot's mission report as Markdown or HTML, or copy it as Markdown for a pull request.</li>
            <li>Older gg versions keep working as before; the plugin offers to install a newer gg when you ask for one of these.</li>
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
