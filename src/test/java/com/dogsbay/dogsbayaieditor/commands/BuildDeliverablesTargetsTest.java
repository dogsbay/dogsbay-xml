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

package com.dogsbay.dogsbayaieditor.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** A build writes kept temporary files under the root, so the gate must hold the root as well as the output. */
class BuildDeliverablesTargetsTest {

    @Test
    void holdsTheRootAndTheOutputBase() {
        Path root = Path.of("proj").toAbsolutePath();
        Path site = Path.of("site").toAbsolutePath();

        CommandTargets targets = CommandTargets.of(
                new BuildDeliverablesCommand(root.toString(), site.toString(), null, null, null, true));

        assertThat(targets.scope()).isEqualTo(CommandTargets.Scope.TREE);
        assertThat(targets.allPaths()).containsExactlyInAnyOrder(root, site);
    }

    @Test
    void resolvesARelativeOutputAgainstTheRoot() {
        Path root = Path.of("proj").toAbsolutePath();

        CommandTargets targets = CommandTargets.of(new BuildDeliverablesCommand(root.toString(), "out", null, null));

        assertThat(targets.allPaths()).containsExactlyInAnyOrder(root, root.resolve("out"));
    }

    @Test
    void holdsTheRootWhenOutputIsTheProjectDefault() {
        Path root = Path.of("proj").toAbsolutePath();

        CommandTargets targets = CommandTargets.of(new BuildDeliverablesCommand(root.toString(), null, null, null));

        assertThat(targets.allPaths()).containsExactly(root);
    }
}
