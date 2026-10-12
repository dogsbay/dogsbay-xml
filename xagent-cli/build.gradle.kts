plugins {
    application
    id("com.gradleup.shadow") version "9.4.2"
}

dependencies {
    implementation(project(":xagent-core"))
    implementation("info.picocli:picocli:${property("picocliVersion")}")
    annotationProcessor("info.picocli:picocli-codegen:${property("picocliVersion")}")
    implementation("org.jline:jline:${property("jlineVersion")}")
    implementation("org.commonmark:commonmark:${property("commonmarkVersion")}")

    testImplementation("org.junit.jupiter:junit-jupiter:${property("junitVersion")}")
    testImplementation("org.assertj:assertj-core:${property("assertjVersion")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "com.xagent.XAgentCli"
    // FFM-ready for the forthcoming DitaEngine native binding (no-op until used).
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

// Fat (shadow) jar is the runnable artifact: xagent-cli/build/libs/xagent-cli-<version>-all.jar
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    manifest {
        attributes(
            // read by com.xagent.Version.get() for --version / banner
            "Implementation-Version" to project.version,
            // honored by `java -jar` so the FFM path needs no extra flags later
            "Enable-Native-Access" to "ALL-UNNAMED",
        )
    }
    // merge META-INF/services so the Extension ServiceLoader SPI survives shading
    mergeServiceFiles()
}

tasks.named("build") {
    dependsOn("shadowJar")
}
