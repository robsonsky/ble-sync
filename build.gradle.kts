import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
plugins {
    // Define plugin versions ONCE here; subprojects will reference without versions
    kotlin("jvm") version "1.9.24" apply false
    kotlin("multiplatform") version "1.9.24" apply false
    kotlin("android") version "1.9.24" apply false
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
    id("com.diffplug.spotless") version "6.25.0" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.6" apply false
    id("com.github.ben-manes.versions") version "0.51.0"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.14.0" apply false
}

subprojects {
    apply(plugin = "com.diffplug.spotless")

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(17) // or 17
        }
    }

    plugins.withId("java") {
        extensions.configure<org.gradle.api.plugins.JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17)) // or 17
            }
        }
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("**/*.kt")
            ktlint("1.1.1") // Stable with Kotlin 1.9.24
            trimTrailingWhitespace()
            endWithNewline()
            indentWithSpaces(4)
        }
        kotlinGradle {
            target("**/*.gradle.kts")
            ktlint("1.1.1")
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    apply(plugin = "io.gitlab.arturbosch.detekt")

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config = files("${rootDir}/detekt.yml")
        buildUponDefaultConfig = true
        allRules = false
        autoCorrect = false
        parallel = true
    }

    dependencies {
        "detektPlugins"("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.6")
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
    }

    // Java toolchain 17 everywhere
    extensions.findByName("java")?.let {
        (it as org.gradle.api.plugins.JavaPluginExtension).toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "17"
            // Turn on useful warnings (keep errors gentle for now; we’ll tighten later)
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-Xjsr305=strict",
                "-Xjvm-default=all"
            )
        }
    }

    pluginManager.withPlugin("java") {
        apply(plugin = "jacoco")
        tasks.withType<Test>().configureEach {
            useJUnitPlatform() // safe even if using kotlin("test")
        }
        tasks.register<JacocoReport>("jacocoTestReportMerged") {
            dependsOn(tasks.withType<Test>())
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
            val sources = files(
                "src/main/java",
                "src/main/kotlin"
            )
            val classes = fileTree("build/classes") {
                include("**/*.class")
                exclude("**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/*\$*") // common excludes
            }
            sourceDirectories.setFrom(sources)
            classDirectories.setFrom(classes)
            executionData.setFrom(fileTree("build") { include("**/*.exec", "**/*.ec") })
        }
    }
}

tasks.register("depsUpdate") {
    dependsOn("dependencyUpdates")
}

configure(listOf(project(":core-domain"), project(":ports"), project(":core-runtime"), project(":adapters-android"))) {
    pluginManager.apply("org.jetbrains.kotlinx.binary-compatibility-validator")
}