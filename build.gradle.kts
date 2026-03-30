plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "dev.gopherglide.gg-plugin"
version = project.findProperty("pluginVersion") ?: "1.0.0"

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
            Initial version
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
