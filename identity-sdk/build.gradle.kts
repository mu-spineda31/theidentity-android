/*
 * Gradle config del SDK Android.
 *
 * Distribución: JitPack o Maven Central. Para JitPack basta con tagear
 * el repo `vX.Y.Z` y JitPack lo expone automáticamente.
 */

plugins {
    id("com.android.library") version "8.2.0"
    id("org.jetbrains.kotlin.android") version "1.9.10"
    id("maven-publish")
}

android {
    namespace = "io.theidentity.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation("androidx.activity:activity:1.8.0")
    implementation("androidx.core:core-ktx:1.12.0")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "io.theidentity"
                artifactId = "identity-sdk"
                version = "0.1.0"
            }
        }
    }
}
