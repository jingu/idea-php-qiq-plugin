import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.9.0"
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

repositories {
    mavenCentral()
    intellijPlatform.defaultRepositories()
}

// --- IntelliJ / PhpStorm を指定 & PHP を「同梱プラグイン依存」として追加 ---
dependencies {
    intellijPlatform {
        phpstorm("2024.2")                 // ← PhpStorm 2024.2 をビルドターゲットに
        bundledPlugin("com.jetbrains.php") // ← PhpLanguage 等が解決される
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
    }
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("junit:junit:4.13.2")
}

val intellijTestDependencies = configurations.named("intellijPlatformTestDependencies")

configurations.named("testImplementation") {
    extendsFrom(intellijTestDependencies.get())
}

configurations.named("testRuntimeOnly") {
    extendsFrom(intellijTestDependencies.get())
}

intellijPlatform {
    pluginVerification {
        ides {
            ide(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.PhpStorm, "2024.2")
            ide(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.PhpStorm, "2026.1")
        }
    }
    pluginConfiguration {
        name.set("Qiq Templates Support")
        id.set("io.github.jingu.idea-qiq-plugin")
        version.set("0.5.0")
        ideaVersion {
            sinceBuild.set("241")
            untilBuild.set("261.*")
        }
        description.set("Syntax highlighting and simple navigation for Qiq templates")
        vendor {
            name.set("Yoshitaka Jingu")
            url.set("https://github.com/jingu/idea-php-qiq-plugin")
        }
    }

}

val genDir = layout.projectDirectory.dir("src/gen/io/github/jingu/idea_qiq_plugin")

tasks.register<org.jetbrains.grammarkit.tasks.GenerateLexerTask>("generateQiqLexer") {
    group = "grammarkit"
    description = "Generate QiqLexer.java from Qiq.flex"

    // New properties in 2022.3.x:
    sourceFile.set(layout.projectDirectory.file("src/main/kotlin/io/github/jingu/idea_qiq_plugin/lexer/Qiq.flex"))
    targetOutputDir.set(genDir)
    // The lexer class/package must be defined in the .flex via %class and package; targetClass is ignored/removed.
    purgeOldFiles.set(true)
}

tasks.named<KotlinCompile>("compileKotlin") {
    dependsOn(tasks.named("generateQiqLexer"))
}

sourceSets.main {
    java.srcDirs("src/gen")
}

tasks.buildSearchableOptions { enabled = false }

kotlin {
    sourceSets.main {
        kotlin.srcDir("src/gen")
    }
}

tasks.test {
    useJUnitPlatform()
}
