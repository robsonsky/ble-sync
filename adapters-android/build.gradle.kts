plugins {
    id("com.android.library") version "8.2.2"
    kotlin("android") version "1.9.24"
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
    implementation(project(":ports"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.core:core-ktx:1.12.0")

    testImplementation(kotlin("test"))
}
