// Root build for the Redrob Intelligent Candidate Discovery web app.
// Two modules:
//   :backend  — Java 21 + Spring Boot (REST + SSE, orchestrates the frozen Python ranker)
//   :frontend — Kotlin/JS + React (single-page demo UI)
//
// The Python ranking engine (../ranker) is FINAL and is never modified by this build.

tasks.register("printModules") {
    doLast { println("Modules: backend (Spring Boot), frontend (Kotlin/JS React)") }
}
