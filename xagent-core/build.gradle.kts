// xagent-core: the embeddable agent (providers, sessions, tools, skills, MCP).
// Toolchain, encoding and test framework come from the root build; versions
// come from gradle.properties, shared with the editor.
plugins {
    `java-library`
}

dependencies {
    // api(): these types appear on xagent-core's public surface and must stay
    // on the compile classpath of consumers (xagent-cli), matching Maven's
    // transitive compile scope.
    api("dev.langchain4j:langchain4j:${property("langchain4jVersion")}")
    api("dev.langchain4j:langchain4j-open-ai:${property("langchain4jVersion")}")
    api("dev.langchain4j:langchain4j-anthropic:${property("langchain4jVersion")}")
    api("dev.langchain4j:langchain4j-ollama:${property("langchain4jVersion")}")
    api("dev.langchain4j:langchain4j-google-ai-gemini:${property("langchain4jVersion")}")
    api("dev.langchain4j:langchain4j-mcp:${property("langchain4jVersion")}-beta22")
    api("com.fasterxml.jackson.core:jackson-databind:${property("jacksonVersion")}")
    api("org.slf4j:slf4j-api:${property("slf4jVersion")}")

    // XML validation: RelaxNG (Jing) and Schematron (with Saxon as its XPath engine)
    implementation("org.relaxng:jing:${property("jingVersion")}")
    implementation("com.helger.schematron:ph-schematron-pure:${property("phSchematronVersion")}")
    // SVRLHelper moved here at 10.x
    implementation("com.helger.schematron:ph-schematron-api:${property("phSchematronVersion")}")
    implementation("net.sf.saxon:Saxon-HE:${property("saxonVersion")}")

    // logging binding (runtime only — code compiles against slf4j-api)
    runtimeOnly("org.slf4j:slf4j-simple:${property("slf4jVersion")}")

    testImplementation("org.junit.jupiter:junit-jupiter:${property("junitVersion")}")
    testImplementation("org.assertj:assertj-core:${property("assertjVersion")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
