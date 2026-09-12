plugins {
    // Auto-provision the JDK 25 toolchain (build.gradle.kts) when a contributor
    // does not have that version installed. Without this, `./gradlew build` on a
    // machine with only an older JDK fails with "No matching toolchains found"
    // rather than fetching one, and nothing in the message says a download would
    // have fixed it.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "dogsbay-editor"

// xagent-core (the embeddable agent library) and xagent-cli are modules of
// this build, not a separate repository. See plans/absorb-xagent.md.
include("xagent-core", "xagent-cli")
