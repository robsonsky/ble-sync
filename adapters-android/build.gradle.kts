plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization") version "1.9.24"
}

android {
    namespace = "com.hailie.adapters"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
        sarifReport = true
        xmlReport = true
        htmlReport = true
        // baseline = file("lint-baseline.xml") // add later if needed
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-runtime"))
    implementation(project(":ports"))

    // Android framework + security
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Koin
    implementation("io.insert-koin:koin-android:3.5.6")

    // Unit test (JVM)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.24")
}
