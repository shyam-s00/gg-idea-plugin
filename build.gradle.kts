plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "dev.gopherglide.gg-plugin"
version = project.findProperty("pluginVersion") ?: "7.0.0"

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
            <b>Live control for running simulations (needs gg v1.3.0+)</b><br>
            <ul>
            <li><b>Steer a run</b> — raise or lower the target RPS with +5 / +10 / −5 / −10, reset to the plan, and drop named marks, all without stopping the run. Bias changes and marks appear on the RPS chart.</li>
            <li><b>Stop now keeps the snapshot</b> — gg is asked to stop cleanly and finishes writing the snapshot; if it does not answer, the process is interrupted, and a second click forces it to end.</li>
            <li><b>Manual changes in Snapshot View</b> — a list of every mark and bias change in time order, with final bias, peak bias, how long a bias was in effect, and when the first change happened. Snapshot Diff adds a one-line notice when either run was changed by hand.</li>
            <li><b>Know your gg before you run</b> — the Run tab shows whether the installed gg supports live control, and offers to install or upgrade it. A startup check (once a day, can be turned off in Settings) tells you when a newer gg is available.</li>
            <li>Marks are stamped when you confirm the label.</li>
            <li>Older gg versions keep working as before.</li>
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
