plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":ports"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    testImplementation(kotlin("test"))
}