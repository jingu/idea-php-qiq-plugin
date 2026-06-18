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
    // BasePlatformTestCase / HeavyPlatformTestCase derive from JUnit3's TestCase, so
    // JUnit4 must be on the compile classpath and the vintage engine present to run
    // them under useJUnitPlatform() alongside the JUnit5 unit tests.
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
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
        version.set("0.10.0")
        ideaVersion {
            sinceBuild.set("241")
            untilBuild.set("261.*")
        }
        description.set(
            "Qiq template support for PhpStorm: HTML-aware syntax highlighting, full PHP " +
                "injection inside Qiq tags and inline <?php ?> blocks, cross-template rename " +
                "refactoring (Shift+F6 works both from PHP and from inside templates), and " +
                "type-checked escape directives — wrong types passed to {{h }}, {{a }}, " +
                "{{j }}, {{u }}, {{c }} are flagged at edit time by PhpStorm's own Type " +
                "Compatibility inspection, with strict / relaxed signatures auto-selected " +
                "from composer.lock."
        )
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
    // Fixture tests (BasePlatformTestCase) share an Application; the platform's
    // leaked-project hunter can flag a still-watched light project when those
    // classes run alongside the JUnit5 unit classes. Forking a fresh JVM per test
    // class isolates each so the hunter only ever sees one suite's projects.
    setForkEvery(1)
}
