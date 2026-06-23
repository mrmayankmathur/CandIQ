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
