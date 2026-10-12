/*
 * Copyright (C) 2002-2026 DogsBay Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dogsbay.util.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * run.sh starts the editor through the legacy Loader, which reads the class to start from
 * the loader.properties that {@code ./gradlew syncLib} generates. Without main.class the
 * Loader fails with a NullPointerException before any window opens.
 */
class LoaderPropertiesTest {

    @Test
    void syncLibWritesAMainClassTheLoaderCanStart() throws Exception {
        String build = Files.readString(Path.of("build.gradle.kts"));
        Matcher m = Pattern.compile("main\\.class=([\\w.]+)").matcher(build);
        assertThat(m.find()).as("syncLib writes main.class into loader.properties").isTrue();
        Class<?> main = Class.forName(m.group(1));
        assertThat(main.getDeclaredMethod("main", ExtensionClassLoader.class, String[].class))
                .as("the Loader calls main(ExtensionClassLoader, String[])").isNotNull();
    }
}
