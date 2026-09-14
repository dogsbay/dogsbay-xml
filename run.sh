#!/bin/bash
# Launcher for DogsBay XML (dev convenience — equivalent to `./gradlew run`).
# Requires a prior build: ./gradlew compileJava processResources syncLib

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Keep in sync with applicationDefaultJvmArgs in build.gradle.kts.
JVM_FLAGS=(
    --add-opens java.base/java.lang=ALL-UNNAMED   # JRuby
    --add-opens java.base/java.io=ALL-UNNAMED     # JRuby
    --add-opens java.base/java.nio=ALL-UNNAMED    # JNA
    --add-opens java.base/sun.nio.ch=ALL-UNNAMED  # JNA
    --enable-native-access=ALL-UNNAMED            # JNA/pty4j (JDK 24+ warns without it)
    -Ddogsbay.debug=true                          # console output and the debug banner
)

exec java "${JVM_FLAGS[@]}" \
     -cp "build/classes/java/main:build/resources/main:lib/*" \
     com.dogsbay.util.loader.Loader "$@"
