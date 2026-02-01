plugins {
    id("com.android.application") version "8.2.2"
    kotlin("android") version "1.9.24"
}

android {
    namespace = "com.hailie.demo"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hailie.demo"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    flavorDimensions += "env"

    productFlavors {
        create("demo") {
            dimension = "env"
            buildConfigField("boolean", "DEMO", "true")
        }
        create("prod") {
            dimension = "env"
            buildConfigField("boolean", "DEMO", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
        sarifReport = true
        xmlReport = true
        htmlReport = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // SDK modules
    implementation(project(":core-domain"))
    implementation(project(":core-runtime"))
    implementation(project(":ports"))
    implementation(project(":fakes"))

    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.01"))

    // Compose core
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Compose tooling & tests
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    testImplementation(kotlin("test"))
}
