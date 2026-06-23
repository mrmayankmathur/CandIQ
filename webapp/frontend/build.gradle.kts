plugins {
    kotlin("multiplatform") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "discovery.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            // Keep the conventional src/main layout instead of src/jsMain.
            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")
            dependencies {
                implementation(project.dependencies.platform("org.jetbrains.kotlin-wrappers:kotlin-wrappers-bom:2026.6.7"))
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-react-dom")
                implementation("org.jetbrains.kotlin-wrappers:kotlin-emotion-react")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
    }
}
