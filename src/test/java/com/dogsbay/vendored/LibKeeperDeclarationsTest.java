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

package com.dogsbay.vendored;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * {@code lib/} holds source jars and build output in one directory, and {@code .gitignore}
 * is where the difference is declared: {@code lib/*.jar} is ignored, and each jar with no
 * Maven artifact is named back in with a {@code !} line.
 *
 * <p>That list had drifted by the time anyone looked (2026-10-12). It still named
 * {@code avalon-framework.jar} and {@code fop.jar}, neither of which had been in the tree
 * for some time — FOP arrives from Maven as {@code fop-core} now — and it did not name
 * {@code dita-index.jar}, which is committed. A tracked file stays tracked whatever
 * {@code .gitignore} says, so nothing was broken; the trap was latent. Had
 * {@code dita-index.jar} ever been removed and put back, it would have been silently
 * ignored, and a source jar with no Maven artifact would have left the repository for good.
 *
 * <p>The package is {@code vendored} rather than {@code build} for a reason worth knowing:
 * {@code .gitignore} listed {@code build/} unanchored, which matches a directory of that name
 * at any depth, so a {@code com.dogsbay.build} package was ignored outright. This test sat
 * there passing locally and could never have been committed. The pattern is anchored now.
 *
 * <p>So the declaration is checked against the thing it describes. It is not merely
 * tidiness: {@code syncLib} decides what to delete from {@code lib/} by asking git what it
 * tracks, and this is what keeps the declared answer and git's answer the same one.
 */
class LibKeeperDeclarationsTest {

    /** Each {@code !lib/x.jar} line of .gitignore. */
    private static Set<String> declaredKeepers() throws Exception {
        Matcher m = Pattern.compile("(?m)^!lib/(.+\\.jar)$")
                .matcher(Files.readString(Path.of(".gitignore")));
        Set<String> names = new TreeSet<>();
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    /**
     * The jars git tracks under lib/, or null where there is no repository to ask — a source
     * zip, or a {@code git archive} export, in which the question has no answer.
     */
    private static Set<String> trackedJars() throws Exception {
        Process git = new ProcessBuilder("git", "ls-files", "lib")
                .redirectErrorStream(true).start();
        List<String> lines = new String(git.getInputStream().readAllBytes()).lines().toList();
        if (git.waitFor() != 0) {
            return null;
        }
        Set<String> names = new TreeSet<>();
        for (String line : lines) {
            if (line.endsWith(".jar")) {
                names.add(line.substring("lib/".length()));
            }
        }
        return names;
    }

    /**
     * A jar git tracks but .gitignore does not name back in. This is the dangerous direction:
     * it works until the file is removed and re-added, at which point {@code lib/*.jar}
     * swallows it and a source jar with no Maven artifact leaves the repository.
     */
    @Test
    void everyCommittedJarIsDeclaredAKeeper() throws Exception {
        Set<String> tracked = trackedJars();
        assumeTrue(tracked != null, "no git repository here, so there is nothing to compare");

        Set<String> undeclared = new TreeSet<>(tracked);
        undeclared.removeAll(declaredKeepers());
        assertThat(undeclared)
                .as("git tracks these jars in lib/ but .gitignore does not name them back in, "
                    + "so lib/*.jar would swallow any one of them if it were removed and added "
                    + "again — add a !lib/<name> line for each")
                .isEmpty();
    }

    /**
     * A jar .gitignore names that git no longer tracks: a line left behind by a removal. Not
     * harmful on its own, but it makes the list stop describing the tree, and the list is what
     * a reader consults to know which jars are source.
     */
    @Test
    void everyDeclaredKeeperIsStillCommitted() throws Exception {
        Set<String> tracked = trackedJars();
        assumeTrue(tracked != null, "no git repository here, so there is nothing to compare");

        Set<String> gone = new TreeSet<>(declaredKeepers());
        gone.removeAll(tracked);
        assertThat(gone)
                .as(".gitignore names these as source jars but git tracks no such file, so "
                    + "they are lines left behind by a removal — delete them")
                .isEmpty();
    }

    /**
     * The declared keepers are the jars with no Maven artifact, so none of them should be
     * sitting in lib/ as a second copy of something the build resolves. checkLib asserts this
     * from the other direction, against the resolved classpath; this reads only the two files
     * and so still says something when the classpath is not available.
     */
    @Test
    void aDeclaredKeeperIsNotAlsoNamedAsAMavenCoordinate() throws Exception {
        String build = Files.readString(Path.of("build.gradle.kts"));
        for (String keeper : declaredKeepers()) {
            // "jaxen-1.1.1.jar" -> "jaxen", the artifact a coordinate would name.
            String artifact = keeper.replaceFirst("-[0-9][^-]*\\.jar$", "").replaceFirst("\\.jar$", "");
            assertThat(build)
                    .as("%s is declared a source jar, so the build should not also resolve "
                        + "'%s' from Maven", keeper, artifact)
                    .doesNotContain(":" + artifact + ":");
        }
    }

    /** The declared keepers are really there, so run.sh's classpath is not naming a ghost. */
    @Test
    void everyDeclaredKeeperIsOnDisk() throws Exception {
        for (String keeper : declaredKeepers()) {
            assertThat(new File("lib", keeper)).as("declared in .gitignore as a source jar").exists();
        }
    }
}
