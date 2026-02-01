plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm() // JVM target so we can run tests now; iOS targets can be added later

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation(project(":core-domain"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
                implementation("io.kotest:kotest-assertions-core:5.8.0")
                implementation("io.kotest:kotest-property:5.8.0")
            }
        }
        val jvmMain by getting
        val jvmTest by getting
    }
}
