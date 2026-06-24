plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.redrob"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.apache.commons:commons-csv:1.11.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Run from the repo root so (a) the optional .env (OPENAI_* for the AI Deep-Dive) resolves and
// (b) AppPaths auto-detects the repo root immediately. Gradle's rootProject is webapp/, so its
// parent is the repository root (the folder holding .env, submission.csv, and ranker/).
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir.parentFile
}

// ── Bundle the Kotlin/JS frontend bundle into the Spring Boot jar's static resources ──
// The frontend's production webpack output is copied into build/resources/main/static
// so Spring Boot serves the SPA at "/". This runs for both bootRun and bootJar.
val copyFrontend by tasks.registering(Copy::class) {
    dependsOn(":frontend:jsBrowserDistribution")
    from(project(":frontend").layout.buildDirectory.dir("dist/js/productionExecutable"))
    into(layout.buildDirectory.dir("resources/main/static"))
}

tasks.named("classes") {
    dependsOn(copyFrontend)
}
copyFrontend.configure {
    mustRunAfter(tasks.named("processResources"))
}
