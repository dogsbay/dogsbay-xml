import java.security.MessageDigest
import java.io.StringWriter
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

plugins {
    java
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.runtime") version "2.0.1"
    id("com.gradleup.shadow") version "9.4.2"
}

group = "com.dogsbay"
// In CI, derive the version from the pushed git tag (e.g. "v4.0.0-beta.2"
// from refs/tags/v4.0.0-beta.2). Falls back to the literal below for local
// builds and PRs. Keep the fallback in sync with the latest tag so dev
// builds carry a meaningful version too.
version = (System.getenv("GITHUB_REF_NAME")
    ?.takeIf { System.getenv("GITHUB_REF_TYPE") == "tag" }
    ?.removePrefix("v"))
    ?: "4.0.0-beta.1"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

// The xagent modules share the root's toolchain, encoding and test framework;
// their own build files carry only their dependencies.
subprojects {
    apply(plugin = "java")

    group = "com.xagent"
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

// Force ASM to a JDK 25-aware release. JRuby/asciidoctorj pull ASM 9.7.1
// transitively, which tops out at Java 24 bytecode; 9.8+ understands
// class-file v69 (Java 25), so any ASM class inspection on 25 stays safe.
configurations.all {
    resolutionStrategy {
        force(
            "org.ow2.asm:asm:9.10.1",
            "org.ow2.asm:asm-tree:9.10.1",
            "org.ow2.asm:asm-commons:9.10.1",
            "org.ow2.asm:asm-analysis:9.10.1",
            "org.ow2.asm:asm-util:9.10.1"
        )
    }
}

// ── Source sets ──────────────────────────────────────────────────────────────
sourceSets {
    main {
        java {
            // Exclude XSLT debugger (being rewritten for Saxon 12)
            exclude("com/dogsbay/xslt/debugger/**")
        }
    }
}

// ── JavaFX ──────────────────────────────────────────────────────────────────
javafx {
    version = "21.0.2"
    modules("javafx.controls", "javafx.web", "javafx.swing")
}

// ── Dependencies ────────────────────────────────────────────────────────────
dependencies {

    // ── Local-only JARs (no Maven Central artifact) ─────────────────────────
    // DOM4J: custom patched build (2.9MB, much larger than standard dom4j)
    implementation(files("lib/dom4j.jar"))
    // JDOM
    // JDOM — not imported anywhere, but ROME's SyndFeedInput exposes
    // org.jdom.Document on its API surface, so the compiler needs it.
    implementation(files("lib/jdom.jar"))
    // Jaxen XPath
    implementation(files("lib/jaxen-1.1.1.jar"))
    // RelaxNG: Jing validates, Trang translates between schema syntaxes.
    // Both from Maven at the same release: they share 252 classes, which are
    // byte-identical when the versions match, and were not when one was a
    // locally recompiled jar. IsoRelax comes transitively from Jing.
    implementation("org.relaxng:jing:${property("jingVersion")}")
    implementation("org.relaxng:trang:${property("trangVersion")}")
    // Apache FOP — the renderer for XSL-FO, and what DITA-OT's PDF plugin runs on.
    // Was FOP 0.20.5 from 2003, vendored as lib/fop.jar: its API (Driver, AWTRenderer,
    // MessageHandler) was replaced wholesale in FOP 1.0 and it cannot render what
    // org.dita.pdf2 emits. Resolved from Maven at the version DITA-OT 4.3.5 bundles, so
    // the in-process toolkit finds it on the editor's own classpath, the way it already
    // finds Saxon and Xerces.
    implementation("org.apache.xmlgraphics:fop-core:${property("fopVersion")}")
    // FOP logs through java.util.logging, which logback.xml cannot reach: a PDF build printed "Rendered page #1"
    // for every page of the book, into the output of a command-line check. The bridge puts it where the rest of
    // the logging already is, under the org.apache.fop rule.
    implementation("org.slf4j:jul-to-slf4j:${property("slf4jVersion")}")
    // org.lwdita — DITA-OT's Markdown and LwDITA reader, so a project may hold .md topics.
    // The plugin is a dita.parser extension: DITA-OT instantiates MarkdownReader by name,
    // so the classes must be on the editor's own classpath, the way FOP above is. That is
    // the whole of what makes .md topics work: the plugin folder itself stays out of the
    // bundled framework, and a Markdown topic publishes against a zip that has no trace
    // of org.lwdita in it.
    // The plugin jar is not published to Maven, so it is committed; its dependencies are.
    // It declares flexmark 0.64.0, but the editor already carries 0.64.8 for its own
    // Markdown rendering, so both resolve to the one version rather than letting Gradle
    // pick silently. Only the extensions the editor did not already have are listed here;
    // flexmark core, ext-tables, ext-gfm-strikethrough, ext-autolink and
    // ext-yaml-front-matter are above, and every flexmark-util-* comes transitively.
    implementation(files("lib/org.lwdita-5.9.1.jar"))
    implementation("com.vladsch.flexmark:flexmark-ext-abbreviation:${property("flexmarkVersion")}")
    implementation("com.vladsch.flexmark:flexmark-ext-admonition:${property("flexmarkVersion")}")
    implementation("com.vladsch.flexmark:flexmark-ext-anchorlink:${property("flexmarkVersion")}")
    implementation("com.vladsch.flexmark:flexmark-ext-attributes:${property("flexmarkVersion")}")
    implementation("com.vladsch.flexmark:flexmark-ext-definition:${property("flexmarkVersion")}")
    implementation("com.vladsch.flexmark:flexmark-ext-footnotes:${property("flexmarkVersion")}")
    implementation("com.vladsch.flexmark:flexmark-ext-ins:${property("flexmarkVersion")}")
    implementation("com.vladsch.flexmark:flexmark-ext-jekyll-tag:${property("flexmarkVersion")}")
    implementation("com.vladsch.flexmark:flexmark-ext-superscript:${property("flexmarkVersion")}")
    implementation("com.vladsch.flexmark:flexmark-ext-typographic:${property("flexmarkVersion")}")
    implementation("org.nibor.autolink:autolink:${property("autolinkVersion")}")
    // htmlparser is the one dependency that has to be the plugin's own jar. It is used on
    // the HDITA (HTML-as-DITA) input path, which org.lwdita registers as
    // parser.hdita=com.elovirta.dita.html.HDitaReader. HtmlReader's constructor reads
    // DoctypeExpectation.AUTO and calls HtmlParser.setDoctypeExpectation — neither of which
    // exists any more: Maven's line starts at 1.4.1, and by the last release, 1.4.16, the
    // DoctypeExpectation class is gone from the jar altogether. So a Maven version compiles
    // and then fails at run time with NoClassDefFoundError the moment an .html topic is
    // read. DITA-OT pins lib/htmlparser-1.4.jar in its own plugins.xml for the same reason.
    implementation(files("lib/htmlparser-1.4.jar"))
    // Apache POI (old version)
    implementation(files("lib/poi.jar"))
    // Commons (old bundled version)
    implementation(files("lib/commons.jar"))
    // JAXP
    // Jisp (icon set)
    // Rhino JavaScript engine
    // Kunststoff L&F
    // JGoodies Looks
    // L2FProd Common UI
    implementation(files("lib/l2fprod-common-directorychooser.jar", "lib/l2fprod-common-totd.jar"))
    // Date picker
    // Bounce UI
    implementation(files("lib/bounce.jar"))
    // Apple Java Extensions (stubs)
    implementation(files("lib/AppleJavaExtensions.jar"))
    // Old ROME RSS (system-scope in Maven, but specific old version)
    implementation(files("lib/rome-0.9.jar", "lib/rome-fetcher-0.9.jar"))
    // DITA-OT core (custom build)
    implementation(files("lib/dost.jar", "lib/dost-configuration.jar"))
    // The index plugin's own task, for the same reason FOP is here: the toolkit runs in process, so a plugin's
    // <taskdef> resolves against the editor's classpath rather than a command line's. Without it a bookmap with an
    // <indexlist/> fails the build outright. Version-locked to the bundled DITA-OT, like dost.jar beside it.
    implementation(files("lib/dita-index.jar"))

    // ── Maven Central dependencies ──────────────────────────────────────────

    // XML Processing
    implementation("xerces:xercesImpl:2.12.2")
    implementation("xml-apis:xml-apis:1.4.01")
    implementation("net.sf.saxon:Saxon-HE:${property("saxonVersion")}")
    implementation("xml-resolver:xml-resolver:1.2")
    // Schematron for batch schematron_project. We use the XSLT engine (compiles
    // the schema to XSLT via Saxon) for full ISO conformance — the pure engine is
    // faster but mis-evaluates union rule @context ("p | entry" matched only p).
    implementation("com.helger.schematron:ph-schematron-isosch:${property("phSchematronVersion")}")
    // JAXB runtime — the XSLT schematron engine unmarshals SVRL via JAXB
    // (jakarta.xml.bind-api 4.x is already present; the impl was missing).
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.2")
    implementation("org.xmlresolver:xmlresolver:5.3.3")

    // NekoHTML
    implementation(files("lib/nekohtml.jar"))

    // FlatLaf Modern L&F
    implementation("com.formdev:flatlaf:3.4")

    // Apache Commons
    implementation("commons-logging:commons-logging:1.2")
    implementation("commons-net:commons-net:3.9.0")
    implementation("commons-io:commons-io:2.19.0")

    // Apache Ant (for DITA-OT)
    implementation("org.apache.ant:ant:1.10.18")
    implementation("org.apache.ant:ant-launcher:1.10.18")
    implementation("org.apache.ant:ant-apache-resolver:1.10.18")

    // Guava
    implementation("com.google.guava:guava:33.4.8-jre")

    // ICU4J
    implementation("com.ibm.icu:icu4j:77.1")

    // Jackson JSON/YAML
    implementation("com.fasterxml.jackson.core:jackson-core:${property("jacksonVersion")}")
    implementation("com.fasterxml.jackson.core:jackson-databind:${property("jacksonVersion")}")
    implementation("com.fasterxml.jackson.core:jackson-annotations:${property("jacksonVersion")}")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:${property("jacksonVersion")}")

    // SLF4J + Logback
    implementation("org.slf4j:slf4j-api:${property("slf4jVersion")}")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("ch.qos.logback:logback-core:1.5.18")

    // SnakeYAML
    implementation("org.yaml:snakeyaml:2.4")

    // Git Integration
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.8.0.202311291450-r")

    // Terminal Emulation
    implementation("org.jetbrains.jediterm:jediterm-ui:3.48")
    implementation("org.jetbrains.jediterm:jediterm-core:3.48")
    implementation("org.jetbrains.pty4j:pty4j:0.12.13")

    // Flexmark Markdown
    implementation("com.vladsch.flexmark:flexmark:${property("flexmarkVersion")}")
    implementation("com.vladsch.flexmark:flexmark-ext-tables:${property("flexmarkVersion")}")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:${property("flexmarkVersion")}")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-tasklist:${property("flexmarkVersion")}")
    implementation("com.vladsch.flexmark:flexmark-ext-autolink:${property("flexmarkVersion")}")
    implementation("com.vladsch.flexmark:flexmark-ext-yaml-front-matter:${property("flexmarkVersion")}")

    // AsciidoctorJ (pulls JRuby transitively)
    implementation("org.asciidoctor:asciidoctorj:3.0.1")

    // ── AI Agent ────────────────────────────────────────────────────────────
    // xagent-core is a module of this build (see plans/absorb-xagent.md), so
    // its langchain4j API dependencies come through transitively and editing
    // agent source rebuilds the editor with no publish step.
    implementation(project(":xagent-core"))
    // xagent-core brings slf4j-simple for its own CLI; the editor logs through
    // logback, and two providers make SLF4J warn on every start.
    configurations.all { exclude(group = "org.slf4j", module = "slf4j-simple") }

    // OS keychain (macOS Keychain / Windows Credential Store / Linux libsecret)
    // for storing the agent's API keys instead of plaintext settings.
    implementation("com.github.javakeyring:java-keyring:1.0.4")

    // ── CLI ───────────────────────────────────────────────────────────────────
    implementation("info.picocli:picocli:${property("picocliVersion")}")

    // ── Testing ─────────────────────────────────────────────────────────────
    testImplementation("org.junit.jupiter:junit-jupiter-api:${property("junitVersion")}")
    testImplementation("org.junit.jupiter:junit-jupiter-params:${property("junitVersion")}")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:${property("junitVersion")}")
    testImplementation("org.junit.platform:junit-platform-launcher:${property("junitPlatformVersion")}")
    testImplementation("org.assertj:assertj-core:${property("assertjVersion")}")
}

// ── Application ─────────────────────────────────────────────────────────────
application {
    // Launch the editor directly. The legacy `Loader` (reads loader.properties
    // from the CWD to build its own classloader from a lib/*.jar list) is
    // incompatible with the packaged uber-jar / app-image layout and NPEs there;
    // Main.main(String[]) builds a delegating ExtensionClassLoader over the app
    // classpath, so it works the same in dev (`run`), `java -jar`, and jpackage.
    mainClass.set("com.dogsbay.dogsbayaieditor.Main")
    applicationDefaultJvmArgs = listOf(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",       // JRuby
        "--add-opens", "java.base/java.io=ALL-UNNAMED",         // JRuby
        "--add-opens", "java.base/java.nio=ALL-UNNAMED",        // JNA
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",      // JNA
        // JNA/pty4j native access: warning on JDK 24+, error in a future
        // release (JEP 472). Accepted by JDK 21+.
        "--enable-native-access=ALL-UNNAMED",
        // Custom dom4j document factory — set for `run` via systemProperty too;
        // here so the packaged launcher (jpackage .cfg) also gets it.
        "-Dorg.dom4j.factory=com.dogsbay.xml.XDocumentFactory"
    )
}

// Standalone agent chat (dev/test harness for the agent plugin UI).
// Usage: ./gradlew runAgentApp [--args="/path/to/workdir"]
tasks.register<JavaExec>("runAgentApp") {
    group = "application"
    description = "Run the standalone agent chat UI (com.dogsbay.agent.app.AgentApp)"
    mainClass.set("com.dogsbay.agent.app.AgentApp")
    classpath = sourceSets["main"].runtimeClasspath
}

// ── Tasks ───────────────────────────────────────────────────────────────────
tasks.test {
    useJUnitPlatform {
        excludeTags("ui")   // display-driven tests run via :uiTest
    }
    // dom4j caches its DocumentFactory at first load, so we must set this before
    // any test class loads dom4j — JVM-arg level is the only place that's reliable
    // when tests share a JVM.
    systemProperty("org.dom4j.factory", "com.dogsbay.xml.XDocumentFactory")
    // Opt-in smoke test against a real ACP agent (see RealAgentSmokeTest).
    System.getProperty("acp.smoke")?.let { systemProperty("acp.smoke", it) }
    System.getProperty("acp.smoke.prompt")?.let { systemProperty("acp.smoke.prompt", it) }
    System.getProperty("acp.smoke.mcp")?.let { systemProperty("acp.smoke.mcp", it) }
    // The end-to-end project check runs a real DITA-OT build, so it is opt-in: -Ddita.ot.e2e=1
    System.getProperty("dita.ot.e2e")?.let { systemProperty("dita.ot.e2e", it) }
}

// A development run keeps its output on the console. Not in
// applicationDefaultJvmArgs: those reach the packaged launcher too.
tasks.named<JavaExec>("run") {
    systemProperty("dogsbay.debug", "true")
}

// ── DogsBay Author (standalone offering) ───────────────────────────────────
// Self-contained jar for the WYSIWYG Author editor: the standalone-clean
// xml/author engine + the schema package + the bundled XML stack. Verified by
// running it with nothing else on the classpath (the standalone-clean rule).
tasks.register<Jar>("authorJar") {
    group = "distribution"
    description = "Builds the self-contained DogsBay Author jar"
    archiveBaseName.set("dogsbay-author")
    manifest {
        attributes["Main-Class"] = "com.dogsbay.xml.author.app.AuthorApp"
    }
    from(sourceSets.main.get().output) {
        include("com/dogsbay/xml/author/**")
        include("com/dogsbay/schema/*.class")
        include("com/dogsbay/xml/*.class")
    }
    from(zipTree("lib/dom4j.jar"))
    from(zipTree("lib/jaxen-1.1.1.jar"))
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Native app-image (bundled JRE) for DogsBay Author, using the JDK's jpackage.
tasks.register<Exec>("authorJpackage") {
    group = "distribution"
    description = "Builds a DogsBay Author app-image with a bundled JRE"
    dependsOn("authorJar")
    val dest = layout.buildDirectory.dir("author-dist").get().asFile
    doFirst {
        dest.deleteRecursively()
        dest.mkdirs()
    }
    commandLine(
        "${System.getProperty("java.home")}/bin/jpackage",
        "--type", "app-image",
        "--name", "DogsBayAuthor",
        "--app-version", project.version.toString().substringBefore("-"),
        "--input", layout.buildDirectory.dir("libs").get().asFile.absolutePath,
        "--main-jar", "dogsbay-author-${project.version}.jar",
        "--dest", dest.absolutePath
    )
}

// Display-driven UI tests (focus, keyboard, real frames). Needs a display:
// run locally as-is, or under `xvfb-run ./gradlew uiTest` in CI.
tasks.register<Test>("uiTest") {
    description = "Runs display-driven UI tests (tagged 'ui')"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("ui")
    }
    systemProperty("org.dom4j.factory", "com.dogsbay.xml.XDocumentFactory")
}

// Manifest equivalents of applicationDefaultJvmArgs, honored by `java -jar`
// (which bypasses the Gradle/jpackage launchers and their JVM flags).
// Enable-Native-Access is recognized from JDK 24 and ignored by older JDKs.
val manifestJvmAttrs = mapOf(
    "Add-Opens" to "java.base/java.lang java.base/java.io java.base/java.nio java.base/sun.nio.ch",
    "Enable-Native-Access" to "ALL-UNNAMED"
)

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.dogsbay.dogsbayaieditor.Main",
            "SplashScreen-Image" to "dogsbay-splash.gif",
            "Implementation-Vendor" to "DogsBay Ltd.",
            "Implementation-Title" to "dogsbay-editor",
            "Implementation-Version" to project.version
        )
        attributes(manifestJvmAttrs)
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    options.encoding = "UTF-8"
}

// ── Uber JAR (java -jar) ────────────────────────────────────────────────────
// Keep the shadow plugin's default "-all" classifier so the
// org.beryx.runtime + jpackage integration can find the main jar (it
// auto-routes to <name>-<version>-all.jar when shadow is on the
// classpath). The uber-jar output is therefore dogsbay-editor-<ver>-all.jar.
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    // The uber-jar bundles the full runtime classpath (DITA-OT, langchain4j,
    // asciidoctorj/JRuby, …) and now exceeds the 65,535-entry ZIP limit;
    // the zip64 extension lifts it. Needed for `jpackage` (release.yml) and
    // `shadowJar` in CI. Modern unzip/JDK read zip64 transparently.
    isZip64 = true
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    manifest {
        attributes(
            "Main-Class" to "com.dogsbay.dogsbayaieditor.Main",
            "SplashScreen-Image" to "dogsbay-splash.gif",
            "Implementation-Vendor" to "DogsBay Ltd.",
            "Implementation-Title" to "dogsbay-editor",
            "Implementation-Version" to project.version
        )
        attributes(manifestJvmAttrs)
    }
}

// ── Sync dependencies to lib/ for runtime (loader.properties) ───────────────
// ── lib/ hygiene ────────────────────────────────────────────────────────────
// lib/ holds two different kinds of jar and they must not be confused:
//
//   * jars Gradle resolves from Maven, which syncLib copies here purely so the
//     dev launchers (run.sh, bin/dogsbay-xml, loader.properties) can use
//     `lib/*`. These are build output. Committing them wastes space, and a
//     stale copy left behind after a version bump silently wins on the
//     launcher classpath — that has bitten the Jing and Schematron upgrades.
//   * jars with no Maven artifact (a patched dom4j, the old Swing widgets),
//     which are genuinely source and stay committed.
//
// checkLib fails on both hazards: a resolvable jar that has been committed,
// and two versions of the same artifact sitting in lib/ at once.
// ── Third-party licence report ──────────────────────────────────────────────
// Resolves every runtime dependency's POM and reads its <licenses>, writing
// THIRD-PARTY.md. Covers the Maven half only: the jars under lib/ that have no
// Maven artifact carry no metadata to read, and are listed as needing a
// human answer rather than silently omitted.
tasks.register("licenseReport") {
    description = "Regenerate THIRD-PARTY.md from the resolved dependencies' POMs"
    val runtime = configurations.named("runtimeClasspath")
    val outFile = file("THIRD-PARTY.md")
    doLast {
        val ids = runtime.get().incoming.resolutionResult.allComponents
            .mapNotNull { it.id as? ModuleComponentIdentifier }
            .distinctBy { "${it.group}:${it.module}:${it.version}" }
            .sortedBy { "${it.group}:${it.module}".lowercase() }

        // one detached configuration for all the POMs, resolved in a single pass
        val pomDeps = ids.map {
            dependencies.create("${it.group}:${it.module}:${it.version}@pom")
        }.toTypedArray()
        val poms = configurations.detachedConfiguration(*pomDeps).also {
            it.isTransitive = false
        }.resolvedConfiguration.lenientConfiguration.artifacts

        val pomByName = poms.map { it.file }.associateBy { it.name }.toMutableMap()

        fun licenceNamesIn(text: String): List<String> =
            Regex("<license>.*?<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL)
                .findAll(text).map { it.groupValues[1].trim() }.toList()

        // "<parent><groupId>g</groupId><artifactId>a</artifactId><version>v</version>"
        fun parentOf(text: String): String? {
            val parent = Regex("<parent>(.*?)</parent>", RegexOption.DOT_MATCHES_ALL)
                .find(text)?.groupValues?.get(1) ?: return null
            fun tag(name: String) = Regex("<$name>(.*?)</$name>").find(parent)?.groupValues?.get(1)?.trim()
            val g = tag("groupId") ?: return null
            val a = tag("artifactId") ?: return null
            val v = tag("version") ?: return null
            return "$g:$a:$v"
        }

        fun pomTextFor(coords: String): String? {
            val (_, a, v) = coords.split(":")
            pomByName["$a-$v.pom"]?.let { return it.readText() }
            return try {
                val fetched = configurations.detachedConfiguration(
                    dependencies.create("$coords@pom")
                ).also { it.isTransitive = false }
                    .resolvedConfiguration.lenientConfiguration.artifacts
                    .map { it.file }.firstOrNull() ?: return null
                pomByName[fetched.name] = fetched
                fetched.readText()
            } catch (e: Exception) {
                null
            }
        }

        // A multi-module project usually declares its licence once, in the parent,
        // so follow the chain rather than reporting "see upstream" 47 times.
        fun licencesOf(id: ModuleComponentIdentifier): String {
            var text = pomByName["${id.module}-${id.version}.pom"]?.readText()
                ?: return "not published in POM"
            repeat(5) {
                val names = licenceNamesIn(text)
                if (names.isNotEmpty()) return names.joinToString("; ")
                val parent = parentOf(text) ?: return "not published in POM"
                text = pomTextFor(parent) ?: return "not published in POM (parent $parent unavailable)"
            }
            return "not published in POM"
        }

        val sb = StringBuilder()
        sb.appendLine("# Third-party dependencies")
        sb.appendLine()
        sb.appendLine("Generated by `./gradlew licenseReport`. Do not edit by hand.")
        sb.appendLine()
        sb.appendLine("## Resolved from Maven (${ids.size})")
        sb.appendLine()
        sb.appendLine("Declared licence as published in each artifact's POM.")
        sb.appendLine()
        sb.appendLine("| Artifact | Version | Licence |")
        sb.appendLine("|---|---|---|")
        ids.forEach {
            sb.appendLine("| ${it.group}:${it.module} | ${it.version} | ${licencesOf(it)} |")
        }

        // Only the jars committed to lib/: the rest of what sits there is syncLib
        // output, already covered by the resolved table above.
        val local = providers.exec {
            commandLine("git", "ls-files", "lib")
        }.standardOutput.asText.get().lines()
            .filter { it.endsWith(".jar") }
            .map { it.removePrefix("lib/") }
            .sorted()
        sb.appendLine()
        sb.appendLine("## Bundled under lib/ (${local.size})")
        sb.appendLine()
        sb.appendLine("Jars with no Maven artifact, committed to this repository. They carry no")
        sb.appendLine("machine-readable licence metadata, so each needs a human answer before")
        sb.appendLine("redistribution. Several are version-stripped and predate the practice of")
        sb.appendLine("shipping a licence inside the jar.")
        sb.appendLine()
        local.forEach { sb.appendLine("- `$it`") }

        outFile.writeText(sb.toString())
        logger.lifecycle("Wrote ${outFile.name}: ${ids.size} resolved, ${local.size} bundled")
    }
}

tasks.register("checkLib") {
    description = "Fail when lib/ holds a committed Maven jar or two versions of one artifact"
    val libDir = file("lib")
    val resolved = configurations.named("runtimeClasspath")
    doLast {
        // invariantSeparatorsPath: on Windows the path holds "\lib\", which a
        // "/lib/" match misses, reporting every committed lib/ jar as a Maven one.
        val fromMaven = resolved.get().files
            .filterNot { it.invariantSeparatorsPath.contains("/lib/") }
            .map { it.name }
            .toSet()

        val problems = mutableListOf<String>()

        val tracked = providers.exec {
            commandLine("git", "ls-files", "lib")
        }.standardOutput.asText.get().lines().filter { it.endsWith(".jar") }.map { it.removePrefix("lib/") }

        val committedButResolvable = tracked.filter { it in fromMaven }.sorted()
        if (committedButResolvable.isNotEmpty()) {
            problems += "These lib/ jars are resolved from Maven and must not be committed " +
                "(syncLib writes them):\n  " + committedButResolvable.joinToString("\n  ")
        }

        // "Saxon-HE-12.10.jar" -> artifact "saxon-he", version "12.10". A trailing
        // classifier ("xmlresolver-5.3.3-data.jar") is part of the same release, not
        // a second version, so the version is compared rather than the file name.
        // Unversioned names have no key and are skipped.
        val versioned = Regex("^(.*?)-([0-9][A-Za-z0-9.]*)(-[A-Za-z][A-Za-z0-9]*)?\\.jar$")
        val versionsByArtifact = mutableMapOf<String, MutableMap<String, MutableList<String>>>()
        libDir.listFiles { f -> f.name.endsWith(".jar") }?.forEach { f ->
            versioned.find(f.name)?.let { m ->
                versionsByArtifact
                    .getOrPut(m.groupValues[1].lowercase()) { mutableMapOf() }
                    .getOrPut(m.groupValues[2]) { mutableListOf() }
                    .add(f.name)
            }
        }
        versionsByArtifact.filterValues { it.size > 1 }.forEach { (artifact, byVersion) ->
            problems += "Two versions of $artifact in lib/, so the dev launcher's " +
                "classpath order decides which wins:\n  " +
                byVersion.values.flatten().sorted().joinToString("\n  ")
        }

        if (problems.isNotEmpty()) {
            throw GradleException(problems.joinToString("\n\n") +
                "\n\nFix: delete the offending files from lib/ (and `git rm` them if committed), " +
                "then re-run ./gradlew syncLib.")
        }
    }
}

tasks.register<Copy>("syncLib") {
    description = "Copy Maven Central dependencies to lib/ for loader.properties"
    from(configurations.runtimeClasspath.get().filter { !it.invariantSeparatorsPath.contains("/lib/") })
    into("lib")

    // loader.properties is the legacy Loader's own classpath list (run.sh). It was
    // hand-maintained and drifted: after the last dependency sweep it named 17 jars
    // that no longer exist and missed every one that had been added. Generate it
    // from what is actually in lib/ so it cannot go stale again.
    doLast {
        val jars = file("lib").listFiles { f -> f.name.endsWith(".jar") }
            ?.map { "lib/${it.name}" }?.sorted() ?: emptyList()
        val entries = (listOf("dogsbay-editor.jar") + jars).joinToString(";")
        file("loader.properties").writeText(
            "# Generated by ./gradlew syncLib — do not edit by hand.\n" +
            "# The legacy Loader (run.sh) reads this to build its classpath and find the\n" +
            "# class to start (Main.main(ExtensionClassLoader, String[])).\n" +
            "main.class=com.dogsbay.dogsbayaieditor.Main\n" +
            "libraries=$entries\n"
        )
    }
}
// compileJava reads jars from lib/ (the fileTree dependencies above), so when
// both run in one invocation the copy goes first. Gradle 9 refuses to guess.
tasks.named("check") { dependsOn("checkLib") }
tasks.named("compileJava") { mustRunAfter("syncLib") }
tasks.named("compileTestJava") { mustRunAfter("syncLib") }

// ── DITA built-in bundle: regenerate dtd/files.list ─────────────────────────
// DitaBuiltInAssets reads dtd/files.list at runtime to know which DTD files to
// extract from the JAR. Regenerate the listing whenever the bundled DTD set
// changes so we don't have to hand-maintain it.
tasks.register("generateDitaDtdListing") {
    description = "Regenerate the DITA bundle's dtd/files.list"
    val dtdDir = file("src/main/resources/com/dogsbay/dogsbayaieditor/plugin/dita/builtin/dtd")
    inputs.dir(dtdDir).withPropertyName("dtdSources")
    val listingFile = dtdDir.resolve("files.list")
    outputs.file(listingFile).withPropertyName("listing")
    doLast {
        val files = dtdDir.walkTopDown()
            .filter { it.isFile && it.name != "files.list" }
            .map { it.relativeTo(dtdDir).path.replace('\\', '/') }
            .sorted()
            .toList()
        listingFile.writeText(files.joinToString("\n", postfix = "\n"))
        logger.lifecycle("DITA bundle: regenerated ${listingFile.name} (${files.size} entries)")
    }
}

// The bundle's version gates re-extraction into a user's profile. It was an empty file that never changed, so the
// comparison was always "" against "", the bundle was extracted once on a machine and never refreshed, and a corrected
// DTD or template reached nobody who had run the editor before. A digest of the bundle's own contents changes exactly
// when the bundle does, which is what the gate wants — not the application's version, which would rewrite the files on
// every release for no reason.
tasks.register("generateDitaBundleVersion") {
    description = "Regenerate the DITA bundle's version.txt from a digest of its contents"
    // the listing is part of what gets hashed, so it has to be written first
    dependsOn("generateDitaDtdListing")
    val builtinDir = file("src/main/resources/com/dogsbay/dogsbayaieditor/plugin/dita/builtin")
    val versionFile = builtinDir.resolve("version.txt")
    inputs.dir(builtinDir).withPropertyName("bundleSources")
    outputs.file(versionFile).withPropertyName("bundleVersion")
    doLast {
        val digest = MessageDigest.getInstance("SHA-256")
        builtinDir.walkTopDown()
            .filter { it.isFile && it.name != "version.txt" }
            .sortedBy { it.relativeTo(builtinDir).path.replace('\\', '/') }
            .forEach { file ->
                digest.update(file.relativeTo(builtinDir).path.replace('\\', '/').toByteArray())
                digest.update(file.readBytes())
            }
        val version = digest.digest().joinToString("") { "%02x".format(it) }.take(16)
        versionFile.writeText(version + "\n")
        logger.lifecycle("DITA bundle: version.txt is now $version")
    }
}

tasks.named("processResources") {
    dependsOn("generateDitaBundleVersion") // which depends on the listing
}

// ── Default DITA-OT framework bundle (regeneration tool) ─────────────────────
// Produces the committed resource
//   src/main/resources/com/dogsbay/dogsbayaieditor/framework/builtin/dita-ot-framework-<ver>.zip
// — a trimmed DITA-OT home (HTML5/XHTML/PDF; no lib/, since the editor
// supplies those libraries to the in-process ProcessorFactory). It's seeded as
// the default framework on first launch (DefaultDitaOtFramework). The zip is
// COMMITTED so normal/CI builds on every OS just ship it; run this task only to
// regenerate it (downloads + re-integrates via DITA-OT's bin/dita → Linux/macOS).
// Version tracks the DITA-OT bundle shipped with the editor.
//
// The bundle also carries the dogsbay publishing plugin (dita-ot/plugins/
// com.dogsbay.publish, with its converter), integrated like DITA-OT's own
// plugins. Its revision (ditaOtBundleRevision, written next to the zip) makes
// DefaultDitaOtFramework replace an installed framework from an older bundle;
// raise it whenever the bundle's content changes. -PditaOtZip=<path> uses a
// local copy of the DITA-OT zip (its checksum is still verified).
// ── PDF fonts for the bundled DITA-OT ───────────────────────────────────────
// Stock DITA-OT renders PDF in the base-14 PostScript fonts, which is Times-Roman for body text. Times-Roman has no
// Hebrew, no minus sign (U+2212), no arrows: FOP warns and drops the character, so the PDF is missing text and says
// so only in a log. FOP resolves a font-family list to one font and does not consult it again per character, so
// there is no fallback to fix this — the font that renders the text has to have the glyphs.
//
// Liberation is the choice because it is metric-compatible with Times New Roman, Arial and Courier New, so the
// change from the stock output is coverage rather than appearance, and because its serif face has Hebrew — DejaVu
// Serif, the obvious alternative, does not, which would have left the localization content still broken.
//
// Embedded rather than found on the machine: an editor that renders a document differently depending on which fonts
// happen to be installed is worse than one that always renders it the same.
val liberationFontsVersion = "2.1.5"
val liberationFontsSha256 = "7191c669bf38899f73a2094ed00f7b800553364f90e2637010a69c0e268f25d0"

/** Put the fonts in the staged home, register them with FOP, and point DITA-OT's mappings at them. */
fun installPdfFonts(home: File, work: File, run: (File, Array<out String>) -> Unit) {
    val archive = File(work, "liberation-fonts.tar.gz")
    val local = (project.findProperty("liberationFontsArchive") as String?)?.let { file(it) }
    if (local != null) {
        local.copyTo(archive, overwrite = true)
    } else {
        run(work, arrayOf("curl", "-sSL", "-o", archive.absolutePath,
            "https://github.com/liberationfonts/liberation-fonts/files/7261482/" +
                "liberation-fonts-ttf-$liberationFontsVersion.tar.gz"))
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(archive.readBytes())
    val actual = digest.joinToString("") { byte -> "%02x".format(byte) }
    check(actual == liberationFontsSha256) {
        "Liberation fonts checksum mismatch: expected $liberationFontsSha256, got $actual"
    }
    run(work, arrayOf("tar", "xzf", archive.absolutePath))

    val fontsDir = File(home, "plugins/org.dita.pdf2/fonts").apply { mkdirs() }
    val unpacked = File(work, "liberation-fonts-ttf-$liberationFontsVersion")
    unpacked.listFiles { f -> f.name.endsWith(".ttf") }?.forEach { it.copyTo(File(fontsDir, it.name), true) }
    listOf("LICENSE", "AUTHORS").forEach { name ->
        File(unpacked, name).takeIf { it.isFile }?.copyTo(File(fontsDir, "$name.liberation-fonts"), true)
    }
    val faces = fontsDir.listFiles { f -> f.name.endsWith(".ttf") }?.size ?: 0
    check(faces == 12) { "Expected 12 Liberation faces in the bundle, found $faces" }

    registerFontsWithFop(home, fontsDir)
    pointFontMappingsAtLiberation(home)
}

/** Tell FOP where the fonts are, so it embeds them instead of looking for them on the machine. */
fun registerFontsWithFop(home: File, @Suppress("UNUSED_PARAMETER") fontsDir: File) {
    val xconf = File(home, "plugins/org.dita.pdf2.fop/cfg/fop.xconf")
    // Relative to the config file, because the bundle is unpacked wherever a user's settings folder happens to be.
    // An absolute path here would be this build machine's, and would resolve to nothing on anyone else's.
    val relative = "../../org.dita.pdf2/fonts"
    val text = xconf.readText()
    val entries = StringBuilder()
    listOf("Serif" to "LiberationSerif", "Sans" to "LiberationSans", "Mono" to "LiberationMono")
        .forEach { (family, file) ->
            listOf(
                Triple("normal", "normal", "Regular"),
                Triple("normal", "bold", "Bold"),
                Triple("italic", "normal", "Italic"),
                Triple("italic", "bold", "BoldItalic")
            ).forEach { (style, weight, face) ->
                entries.append(
                    """
                    |        <font embed-url="$relative/$file-$face.ttf">
                    |          <font-triplet name="Liberation $family" style="$style" weight="$weight"/>
                    |        </font>
                    |""".trimMargin()
                )
            }
        }
    // Into the PDF renderer's own <fonts>, ahead of <auto-detect/>: ours are the ones that must win.
    val marker = "        <!-- auto-detect fonts -->"
    check(text.contains(marker)) { "fop.xconf no longer has the auto-detect marker to insert before" }
    xconf.writeText(text.replaceFirst(marker, entries.toString() + marker))
}

/**
 * Name the embedded fonts as the default face for every logical font, keeping the stock list behind them.
 *
 * The stock mapping is kept beside it, so someone who wants Times back has the file to copy into a PDF
 * customisation directory rather than having to reconstruct it.
 */
fun pointFontMappingsAtLiberation(home: File) {
    val mappings = File(home, "plugins/org.dita.pdf2/cfg/fo/font-mappings.xml")
    val stock = mappings.readText()
    File(mappings.parentFile, "font-mappings-base14.xml").writeText(stock)
    var text = stock
    listOf(
        "Times New Roman, Times," to "Liberation Serif, Times New Roman, Times,",
        "<font-face>Helvetica," to "<font-face>Liberation Sans, Helvetica,",
        "<font-face>Courier New, Courier," to "<font-face>Liberation Mono, Courier New, Courier,"
    ).forEach { (from, to) ->
        check(text.contains(from)) { "font-mappings.xml no longer contains \"$from\"" }
        text = text.replace(from, to)
    }
    mappings.writeText(text)
}

val ditaOtBundleRevision = 10
tasks.register("bundleDitaOt") {
    group = "dita"
    description = "Regenerate the trimmed DITA-OT HTML5 framework bundle (manual; Linux/macOS)"
    doLast {
        // Checked before the download and the unzip: these only read the working tree, and
        // finding out after several minutes that a bundle is missing wastes all of it.
        val plugin = file("dita-ot/plugins/com.dogsbay.publish")
        check(File(plugin, "converter/publish-dita.mjs").isFile) {
            "The dogsbay plugin has no converter: run ./gradlew bundleDogsbayConverter first"
        }
        // All three parts, because build.mjs loads chunks/ and spawns the worker: a bundle
        // with only build.mjs passes a one-file check and fails at publish time.
        check(File(plugin, "ssg/build.mjs").isFile && File(plugin, "ssg/highlight-worker.mjs").isFile
                && File(plugin, "ssg/chunks").list()?.isNotEmpty() == true) {
            "The dogsbay plugin has no complete site builder: run ./gradlew bundleDogsbaySsg first"
        }
        // The version and its checksum travel together — a different version would fail the
        // verification below — so both are literals here. Raising the version means raising
        // both, DefaultDitaOtFramework.VERSION, and ditaOtBundleRevision above.
        val version = "4.3.5"
        val sha256 = "a2e5a6ce9841e35fffb6363a11c2e5dec23a2f64f3a397940221cbdfe159e21c" // dita-ot 4.3.5
        // PDF stays: org.dita.pdf2 is the stylesheets, org.dita.pdf2.fop the renderer's plugin wiring. Its bundled
        // FOP jars are dropped with the rest of lib/ below, because the editor now carries FOP on its own classpath —
        // which is also why org.dita.pdf2.axf and .xep go: they are the commercial renderers, and we have no licence
        // to call them.
        // org.dita.index stays: a bookmap's <indexlist/> builds an empty index without it, and a PDF that silently
        // has no index is the same shape of fault as a PDF that silently drops characters.
        // org.lwdita goes, although the editor reads Markdown topics: its reader is a Java
        // class DITA-OT instantiates by name, so what makes .md work is the jars on the
        // editor's classpath (see the dependencies block), not this folder. Verified by
        // publishing a Markdown topic against a zip with no org.lwdita entry at all. The
        // folder holds only the markdown *output* transtypes' XSLT, which nothing offers.
        val dropPlugins = listOf(
            "org.dita.pdf2.axf", "org.dita.pdf2.xep",
            "org.lwdita", "org.dita.htmlhelp",
            "org.dita.eclipsehelp", "org.dita.troff"
        )
        // A tripwire for a trim that did not happen, not a platform limit: full DITA-OT is ten times this. Raised
        // from 25 when the PDF stylesheets were kept — they are 2.8 MB of XSL-FO on their own.
        val ceilingMb = 40L

        fun run(dir: File, vararg cmd: String) {
            val p = ProcessBuilder(*cmd).directory(dir).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            check(p.waitFor() == 0) { "Command failed: ${cmd.joinToString(" ")}\n$out" }
        }

        val work = layout.buildDirectory.dir("dita-ot-bundle").get().asFile
        work.deleteRecursively(); work.mkdirs()
        val zip = File(work, "dita-ot-$version.zip")

        val localZip = (project.findProperty("ditaOtZip") as String?)?.let { file(it) }
        if (localZip != null) {
            logger.lifecycle("bundleDitaOt: using $localZip")
            localZip.copyTo(zip, overwrite = true)
        } else {
            logger.lifecycle("bundleDitaOt: downloading DITA-OT $version")
            run(work, "curl", "-sSL", "-o", zip.absolutePath,
                "https://github.com/dita-ot/dita-ot/releases/download/$version/dita-ot-$version.zip")
        }
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(zip.readBytes()).joinToString("") { "%02x".format(it) }
        check(actual == sha256) { "DITA-OT $version sha256 mismatch: got $actual" }

        run(work, "unzip", "-q", zip.absolutePath)
        val home = File(work, "dita-ot-$version")

        dropPlugins.forEach { File(home, "plugins/$it").deleteRecursively() }
        val dogsbayPlugin = file("dita-ot/plugins/com.dogsbay.publish")
        dogsbayPlugin.copyRecursively(File(home, "plugins/com.dogsbay.publish"))
        logger.lifecycle("bundleDitaOt: re-integrating (dropped ${dropPlugins.size} plugins)")
        run(home, "./bin/dita", "install")

        // In-process DITA-OT reads its transtype list once, from the classpath
        // (org.dita.dost.platform/plugin.properties in lib/dost-configuration.jar), not
        // from the home it runs; add the bundle's transtypes and plugin folders to it, so
        // the editor can run the dogsbay transtypes. Existing entries (pdf, from a full
        // DITA-OT) stay.
        val bundled = Properties().apply {
            File(home, "config/org.dita.dost.platform/plugin.properties").inputStream().use { load(it) }
        }
        val jar = file("lib/dost-configuration.jar")
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(jar.inputStream()).use { zin ->
            generateSequence { zin.nextEntry }.forEach { e -> if (!e.isDirectory) entries[e.name] = zin.readBytes() }
        }
        val propsName = "org.dita.dost.platform/plugin.properties"
        val current = Properties().apply { load(entries.getValue(propsName).inputStream()) }
        fun union(key: String) {
            val merged = (current.getProperty(key, "").split(";") + bundled.getProperty(key, "").split(";"))
                .filter { it.isNotBlank() }.distinct()
            current.setProperty(key, merged.joinToString(";"))
        }
        union("transtypes")
        bundled.stringPropertyNames().filter { it.startsWith("plugin.") && it.endsWith(".dir") }
            .forEach { current.setProperty(it, bundled.getProperty(it)) }
        val sorted = current.stringPropertyNames().sorted()
            .joinToString("\n") { k ->
                val out = StringWriter()
                Properties().apply { setProperty(k, current.getProperty(k)) }.store(out, null)
                out.toString().lines().first { !it.startsWith("#") && it.isNotBlank() }
            }
        entries[propsName] = ("#DITA-OT runtime configuration, do not edit manually\n" + sorted + "\n").toByteArray()
        ZipOutputStream(jar.outputStream()).use { zout ->
            entries.forEach { (name, bytes) ->
                zout.putNextEntry(ZipEntry(name).apply { time = 0 })
                zout.write(bytes)
                zout.closeEntry()
            }
        }
        logger.lifecycle("bundleDitaOt: transtypes in ${jar.name}: ${current.getProperty("transtypes")}")

        // Strip everything not needed for in-process HTML5 publishing.
        listOf("lib", "doc", "docsrc", "samples", "resources", "bin").forEach {
            File(home, it).deleteRecursively()
        }
        // And the PDF plugin's own copy of FOP, for the same reason the toolkit's lib/ goes: the editor carries
        // FOP 2.11 and its dependencies on its classpath, and a second copy inside the bundle would be 13 MB of
        // the same jars — loaded or not, whichever won would be a coin toss.
        File(home, "plugins/org.dita.pdf2.fop/lib").deleteRecursively()
        listOf("startcmd.sh", "startcmd.bat").forEach { File(home, it).delete() }

        installPdfFonts(home, work, ::run)

        // Stage framework.xml + dita-ot/ and verify the size ceiling.
        val stage = File(work, "stage").apply { deleteRecursively(); mkdirs() }
        home.copyRecursively(File(stage, "dita-ot"))
        File(stage, "framework.xml").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <framework name="DITA-OT $version" version="$version">
              <description>DITA Open Toolkit $version (trimmed: HTML5, XHTML and PDF). DITA
              editing and validation are built into the editor and work without this framework;
              this adds publishing. Runs in-process via the editor's DITA-OT engine — the
              toolkit's own lib/ is not shipped because the editor supplies those libraries,
              and neither is the PDF plugin's copy of FOP, for the same reason. The PDF fonts
              are Liberation, embedded so that a document renders the same everywhere.</description>

              <tool name="dita-ot" src="dita-ot"/>
            </framework>
            """.trimIndent() + "\n"
        )
        val homeMb = stage.walkTopDown().filter { it.isFile }.map { it.length() }.sum() / (1024 * 1024)
        check(homeMb <= ceilingMb) { "Trimmed DITA-OT home is ${homeMb}MB, over the ${ceilingMb}MB ceiling" }

        val resource = file(
            "src/main/resources/com/dogsbay/dogsbayaieditor/framework/builtin/dita-ot-framework-$version.zip")
        resource.parentFile.mkdirs(); resource.delete()
        run(stage, "zip", "-qr", resource.absolutePath, "framework.xml", "dita-ot")
        File(resource.parentFile, "dita-ot-framework-$version.revision").writeText("$ditaOtBundleRevision\n")
        logger.lifecycle("bundleDitaOt: wrote ${resource.name} (home ${homeMb}MB, zip ${resource.length() / 1024}KB)")
    }
}

// ── dogsbay publishing converter (regeneration tool) ─────────────────────────
// Builds the dogsbay CLI's `publish-dita` command as one JavaScript file in the
// sibling ../dogsbay repo and copies it into the DITA-OT plugin
// (dita-ot/plugins/com.dogsbay.publish/converter/), with the dogsbay commit in
// converter/VERSION. Committed, so builds ship it; run this, then bundleDitaOt,
// when the converter changes.
tasks.register("bundleDogsbayConverter") {
    group = "dita"
    description = "Refresh the dogsbay DITA-OT plugin's converter from ../dogsbay (manual)"
    doLast {
        val dogsbay = file("../dogsbay")
        check(File(dogsbay, "packages/cli/scripts/bundle-publish-dita.mjs").isFile) {
            "dogsbay repo with packages/cli/scripts/bundle-publish-dita.mjs not found at ../dogsbay"
        }
        fun capture(vararg cmd: String): String {
            val p = ProcessBuilder(*cmd).directory(dogsbay).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            check(p.waitFor() == 0) { "Command failed: ${cmd.joinToString(" ")}\n$out" }
            return out.trim()
        }
        val converter = file("dita-ot/plugins/com.dogsbay.publish/converter")
        converter.mkdirs()
        logger.lifecycle("bundleDogsbayConverter: building the dogsbay CLI's packages")
        capture("pnpm", "turbo", "build", "--filter=dogsbay...", "--output-logs=errors-only")
        capture("node", "packages/cli/scripts/bundle-publish-dita.mjs", File(converter, "publish-dita.mjs").absolutePath)
        val commit = capture("git", "rev-parse", "--short", "HEAD")
        val dirty = capture("git", "status", "--porcelain").isNotEmpty()
        File(converter, "VERSION").writeText(commit + (if (dirty) "-dirty" else "") + "\n")
        logger.lifecycle("bundleDogsbayConverter: converter from dogsbay $commit${if (dirty) " (uncommitted changes)" else ""}")
    }
}

// Builds dogsbay-ssg's build-only entry in the sibling ../dogsbay-ssg repo and
// copies it into the DITA-OT plugin (dita-ot/plugins/com.dogsbay.publish/ssg/),
// with the dogsbay-ssg commit in ssg/VERSION. Committed, so builds ship it; run
// this, then bundleDitaOt, when the site builder changes.
//
// This is a FOLDER, not one file like the converter: the entry code-splits, so
// build.mjs loads chunks/ beside it and spawns highlight-worker.mjs for
// highlighting. All three must travel together.
//
// Separate from bundleDogsbayConverter because it is a different source repo
// with its own commit, so each task's VERSION stamp answers "did this change?"
// about the thing it actually built.
tasks.register("bundleDogsbaySsg") {
    group = "dita"
    description = "Refresh the dogsbay DITA-OT plugin's site builder from ../dogsbay-ssg (manual)"
    doLast {
        val ssgRepo = file("../dogsbay-ssg")
        check(File(ssgRepo, "scripts/build-cli.mjs").isFile) {
            "dogsbay-ssg repo with scripts/build-cli.mjs not found at ../dogsbay-ssg"
        }
        fun capture(vararg cmd: String): String {
            val p = ProcessBuilder(*cmd).directory(ssgRepo).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            check(p.waitFor() == 0) { "Command failed: ${cmd.joinToString(" ")}\n$out" }
            return out.trim()
        }
        // Both steps, not just build-cli: the entry embeds src/generated/ (client JS, theme
        // CSS, KaTeX assets) which only scripts/build-client.mjs writes, and which is
        // gitignored there. Without this a fresh clone fails with an opaque esbuild
        // resolve error, and — worse — an existing stale src/generated/ builds fine and
        // ships last week's client assets under a VERSION stamping today's commit.
        logger.lifecycle("bundleDogsbaySsg: building dogsbay-ssg's client assets and build entry")
        capture("npm", "run", "build")
        val built = File(ssgRepo, "dist/build")
        check(File(built, "build.mjs").isFile) { "dogsbay-ssg did not produce dist/build/build.mjs" }
        // Replaced wholesale: the chunk names carry content hashes, so copying over
        // an older bundle would leave its orphaned chunks behind for ever.
        val ssg = file("dita-ot/plugins/com.dogsbay.publish/ssg")
        check(ssg.deleteRecursively()) { "Could not clear $ssg" }
        ssg.mkdirs()
        built.copyRecursively(ssg)
        val commit = capture("git", "rev-parse", "--short", "HEAD")
        val dirty = capture("git", "status", "--porcelain").isNotEmpty()
        File(ssg, "VERSION").writeText(commit + (if (dirty) "-dirty" else "") + "\n")
        val kb = ssg.walkTopDown().filter { it.isFile }.map { it.length() }.sum() / 1024
        logger.lifecycle("bundleDogsbaySsg: site builder from dogsbay-ssg $commit${if (dirty) " (uncommitted changes)" else ""} (${kb}KB)")
    }
}

// ── Bundled sample project (regeneration tool) ──────────────────────────────
// Produces the committed sample resource
//   src/main/resources/com/dogsbay/dogsbayaieditor/samples/audacity-demo.zip
// (+ a version stamp) from a `git archive` snapshot of the sibling
// ../dogsbay-xml-dita-tutorial repo (tracked files only — no .git, no gitignored
// output, and nothing that repo's .gitattributes marks export-ignore). Committed
// so all builds ship it; run this task to refresh it when the sample changes.
//
// The snapshot is of a named ref — main by default, -PsampleRef=... for another —
// not of whatever is checked out there. That repository keeps a branch per
// tutorial stage, so with HEAD a maintainer who had left tutorial/09-map-structure
// checked out shipped stage 9 as the sample, and the only sign of it was the
// version stamp. BundledSampleHealthTest is the gate on what comes out.
tasks.register("bundleDemo") {
    group = "dita"
    description = "Regenerate the bundled sample project from ../dogsbay-xml-dita-tutorial (manual)"
    doLast {
        val demo = file("../dogsbay-xml-dita-tutorial")
        check(demo.isDirectory) {
            "dogsbay-xml-dita-tutorial not found at ../dogsbay-xml-dita-tutorial"
        }
        val ref = (findProperty("sampleRef") as String?) ?: "main"
        val resDir = file("src/main/resources/com/dogsbay/dogsbayaieditor/samples")
        resDir.mkdirs()
        val zip = File(resDir, "audacity-demo.zip")
        zip.delete()

        fun capture(vararg cmd: String): String {
            val p = ProcessBuilder(*cmd).directory(demo).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            check(p.waitFor() == 0) { "Command failed: ${cmd.joinToString(" ")}\n$out" }
            return out.trim()
        }

        // git archive names the ref in its own error when it does not resolve,
        // so there is nothing to add by checking for it first.
        capture("git", "archive", "--format=zip", "-o", zip.absolutePath, ref)
        val version = capture("git", "rev-parse", "--short", ref)
        File(resDir, "audacity-demo-version.txt").writeText(version + "\n")
        logger.lifecycle("bundleDemo: wrote audacity-demo.zip from $ref (${zip.length() / 1024}KB, version $version)")
    }
}

// ── Native packaging (jlink + jpackage) ─────────────────────────────────────
// The --add-launcher description for the CLI executable. Options it does not
// name — java-options above all, which carries the --add-opens JRuby and JNA
// need — are inherited from the main launcher.
//
// The platform-specific keys keep the CLI out of the desktop: --linux-shortcut
// and --win-menu/--win-shortcut apply to every launcher in the image, so
// without these the command line gets its own applications-menu entry that
// flashes a window and exits. win-console makes the Windows executable a
// console program, so its output reaches the shell that ran it.
val cliLauncherProperties = layout.buildDirectory.file("jpackage-launcher/dogsbay-xml.properties")
val writeCliLauncherProperties = tasks.register("writeCliLauncherProperties") {
    description = "Write the jpackage --add-launcher properties for the CLI executable"
    val out = cliLauncherProperties
    val osName = System.getProperty("os.name").lowercase()
    val text = buildString {
        appendLine("main-class=com.dogsbay.dogsbayaieditor.cli.DogsBayCli")
        appendLine("description=DogsBay XML command line")
        when {
            osName.contains("linux") -> appendLine("linux-shortcut=false")
            osName.contains("windows") -> {
                appendLine("win-console=true")
                appendLine("win-menu=false")
                appendLine("win-shortcut=false")
            }
        }
    }
    // The content is an input. With outputs alone Gradle calls the task
    // up to date whenever the file exists, so editing the text above would
    // ship the previous launcher description without saying so.
    inputs.property("text", text)
    outputs.file(out)
    doLast {
        val file = out.get().asFile
        file.parentFile.mkdirs()
        file.writeText(text)
    }
}

// jpackage builds the app image and the Linux package in two steps, and the
// second step does not honour the launcher's linux-shortcut=false: it writes a
// desktop entry for every launcher in the image, so the command line would get
// an applications-menu icon that launches a console program with no console.
// Overriding the desktop template for that one launcher (the resource is named
// after it) is the way to keep it off the menu. The GUI launcher keeps the
// default template.
val cliDesktopResources = layout.buildDirectory.dir("jpackage-resources")

// GNOME Shell does not read the icon off the window (_NET_WM_ICON) for the
// overview, the dash or alt-tab: it matches the window to a desktop entry and
// takes the icon from there. The match is on WM_CLASS, which AWT derives from
// the main class — "com.dogsbay.dogsbayaieditor.Main" with the dots turned into
// dashes. Without StartupWMClass saying so, an installed build shows GNOME's
// generic cog however good the icon in the window is. DesktopEntryTest pins the
// string to the real main class so moving Main cannot silently break it.
val guiWmClass = "com-dogsbay-dogsbayaieditor-Main"

val writeDesktopEntries = tasks.register("writeDesktopEntries") {
    description = "Write the desktop entries jpackage generates for the launchers"
    val out = cliDesktopResources
    // The command line launcher: jpackage writes a desktop entry for every
    // launcher in the image and ignores linux-shortcut=false at the packaging
    // step, so this override is what keeps a console program off the menu.
    val cliEntry = """
        [Desktop Entry]
        Name=DogsBay XML command line
        Comment=DogsBay XML command line
        Exec=APPLICATION_LAUNCHER
        Icon=APPLICATION_ICON
        Terminal=true
        Type=Application
        Categories=Development
        NoDisplay=true
        MimeType=
    """.trimIndent() + "\n"
    // No MimeType line: jpackage never puts %f in Exec, so claiming to handle
    // application/xml would list DogsBay XML under "Open With" and then start it
    // with no arguments and an empty editor. A real association needs
    // --file-associations, not a MIME type.
    val guiEntry = """
        [Desktop Entry]
        Name=DogsBay XML
        Comment=An AI-assisted XML and DITA editor
        Exec=APPLICATION_LAUNCHER
        Icon=APPLICATION_ICON
        Terminal=false
        Type=Application
        Categories=Development
        StartupWMClass=$guiWmClass
    """.trimIndent() + "\n"
    inputs.property("cliEntry", cliEntry)
    inputs.property("guiEntry", guiEntry)
    inputs.file("src/main/packaging/icons/dogsbay-paw.png").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(out)
    doLast {
        val dir = out.get().asFile
        dir.mkdirs()
        dir.resolve("dogsbay-xml.desktop").writeText(cliEntry)
        // Named after the image: jpackage looks the override up by launcher name.
        dir.resolve("DogsBay-XML-Editor.desktop").writeText(guiEntry)
        // The icon as well, under the same name. --icon dresses the app image,
        // but the Linux packaging step re-derives lib/<name>.png from the
        // resource directory and falls back to the stock Java icon when it is
        // not there — so the image carried the paw and the .deb and .rpm
        // shipped Duke.
        val icon = file("src/main/packaging/icons/dogsbay-paw.png")
        if (icon.isFile) {
            icon.copyTo(dir.resolve("DogsBay-XML-Editor.png"), overwrite = true)
        }
    }
}

runtime {
    options.addAll(
        "--strip-debug",
        "--compress", "zip-6",
        "--no-header-files",
        "--no-man-pages"
    )

    // The jlink runtime must carry every module the app touches, or the
    // packaged launcher fails with NoClassDefFoundError. Base set verified with
    // `jdeps --print-module-deps` on the uber-jar; the service-loaded modules at
    // the end (charsets/localedata/zipfs) jdeps can't see but are needed at
    // runtime (non-UTF encodings, locale formatting, zip/jar filesystems).
    modules.addAll(
        "java.base", "java.compiler", "java.desktop", "java.logging",
        "java.management", "java.naming", "java.net.http", "java.prefs",
        "java.rmi", "java.scripting", "java.security.jgss", "java.sql",
        "java.xml", "java.xml.crypto",
        "jdk.httpserver",            // com.sun.net.httpserver (IPC/MCP server)
        "jdk.unsupported",           // sun.misc.Unsafe (JRuby, JNA)
        "jdk.unsupported.desktop",   // Swing/AWT internal access
        "jdk.crypto.ec",             // TLS
        "jdk.jfr",                   // flight recorder hooks pulled transitively
        "jdk.net",                   // extended socket options
        "jdk.security.auth",         // JAAS
        "jdk.jsobject",              // JavaFX WebView JS bridge
        "jdk.xml.dom",               // org.w3c.dom.html (Xerces)
        // Service-loaded — invisible to jdeps, required at runtime:
        "jdk.charsets",              // non-UTF/extended charsets (XML encodings)
        "jdk.localedata",            // non-root locale date/number formatting
        "jdk.zipfs"                  // zip/jar FileSystem provider
    )

    jpackage {
        // jpackage rejects "-beta.1" style suffixes in --app-version on some
        // platforms (must be numeric), so strip the pre-release qualifier for
        // the OS-level version while keeping the full version in the jar
        // manifest (read by Identity for the About dialog).
        appVersion = project.version.toString().substringBefore("-")
        // Not "DogsBay-XML": the CLI launcher added below is "dogsbay-xml",
        // and the two differ only in case. Linux is case-sensitive so both
        // coexist, but macOS and Windows treat them as one path and jpackage
        // fails creating the second — the app image built on Linux and nowhere
        // else. Lowercased this is "dogsbay-xml-editor", which cannot collide.
        imageName = "DogsBay-XML-Editor"
        installerName = "DogsBay-XML-Editor"

        val commonOpts = listOf("--vendor", "DogsBay Ltd.")

        // os.name check instead of org.gradle.internal.os.OperatingSystem:
        // internal Gradle API, not safe to rely on across Gradle 9+.
        val osName = System.getProperty("os.name").lowercase()

        // The paw, in the one format each platform will read: .ico on Windows,
        // .icns on macOS, .png everywhere else. Without --icon jpackage ships
        // the stock Java icon, which is what the installed app wore.
        val iconDir = file("src/main/packaging/icons")
        val iconFile = when {
            osName.contains("windows") -> iconDir.resolve("dogsbay-paw.ico")
            osName.contains("mac") -> iconDir.resolve("dogsbay-paw.icns")
            else -> iconDir.resolve("dogsbay-paw.png")
        }
        val iconOpts = if (iconFile.exists()) listOf("--icon", iconFile.absolutePath) else emptyList()

        // A second executable in the same app image, for the command line.
        // The image already carries every class and a JRE, so the CLI costs
        // nothing extra to ship — and without it the only way to run the
        // command line is a checkout and a Gradle build, which is a lot to ask
        // of someone who wants to validate a map in a pipeline.
        imageOptions = iconOpts + listOf(
            "--add-launcher", "dogsbay-xml=${cliLauncherProperties.get().asFile.absolutePath}"
        )
        when {
            osName.contains("linux") -> {
                // Linux package format is selectable so one runner can produce
                // both Debian/Ubuntu (.deb) and Fedora/RHEL (.rpm) artifacts:
                //   ./gradlew jpackage -PlinuxPackage=rpm   (needs `rpm`/rpmbuild)
                //   ./gradlew jpackage                      (default: deb)
                val linuxPackage = (project.findProperty("linuxPackage") as String?) ?: "deb"
                val linuxCommon = listOf(
                    "--linux-shortcut",
                    "--linux-menu-group", "Development",
                    "--linux-app-category", "Development"
                )
                installerType = linuxPackage
                installerOptions = commonOpts + iconOpts + linuxCommon + listOf(
                    "--resource-dir", cliDesktopResources.get().asFile.absolutePath
                ) + when (linuxPackage) {
                    "rpm" -> listOf("--linux-rpm-license-type", "Proprietary")
                    else -> listOf("--linux-deb-maintainer", "info@dogsbay.com")
                }
            }
            osName.contains("mac") -> {
                installerType = "dmg"
                installerOptions = commonOpts + iconOpts + listOf(
                    "--mac-package-identifier", "com.dogsbay.dogsbayaieditor"
                )
            }
            osName.contains("windows") -> {
                installerType = "msi"
                installerOptions = commonOpts + iconOpts + listOf(
                    "--win-menu",
                    "--win-shortcut",
                    "--win-dir-chooser"
                )
            }
        }
    }
}

// jpackageImage builds the app image the launcher description feeds into, so
// the file has to exist first.
tasks.matching { it.name == "jpackageImage" }.configureEach {
    dependsOn(writeCliLauncherProperties)
}
tasks.matching { it.name == "jpackage" }.configureEach {
    dependsOn(writeDesktopEntries)
}
