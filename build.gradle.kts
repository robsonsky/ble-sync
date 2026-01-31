plugins {
    // Define plugin versions ONCE here; subprojects will reference without versions
    kotlin("jvm") version "1.9.24" apply false
    kotlin("multiplatform") version "1.9.24" apply false
    kotlin("android") version "1.9.24" apply false
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
}