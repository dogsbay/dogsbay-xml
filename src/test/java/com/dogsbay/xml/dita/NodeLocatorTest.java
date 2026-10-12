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

package com.dogsbay.xml.dita;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Where {@link NodeLocator} looks for Node.js, and in which order. */
class NodeLocatorTest {

    @TempDir Path dir;

    private Path executable(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "#!/bin/sh\n");
        path.toFile().setExecutable(true);
        return path;
    }

    @Test
    void prefersThePreferenceThenTheEnvironmentVariableThenThePath() throws Exception {
        Path preferred = executable(dir.resolve("pref/node"));
        Path fromEnv = executable(dir.resolve("env/node"));
        Path onPath = executable(dir.resolve("bin/node"));
        Map<String, String> env = new HashMap<>(Map.of("DOGSBAY_NODE", fromEnv.toString(), "PATH", dir.resolve("bin").toString()));

        assertThat(new NodeLocator(env, dir.resolve("home"), false, preferred.toString()).find()).contains(preferred);
        assertThat(new NodeLocator(env, dir.resolve("home"), false, null).find()).contains(fromEnv);
        env.remove("DOGSBAY_NODE");
        assertThat(new NodeLocator(env, dir.resolve("home"), false, "  ").find()).contains(onPath);
    }

    @Test
    void skipsCandidatesThatAreMissingOrNotExecutable() throws Exception {
        Path notExecutable = dir.resolve("plain/node");
        Files.createDirectories(notExecutable.getParent());
        Files.writeString(notExecutable, "");
        notExecutable.toFile().setExecutable(false);
        Path onPath = executable(dir.resolve("bin/node"));
        Map<String, String> env = Map.of("PATH", dir.resolve("missing") + ":" + dir.resolve("plain") + ":" + dir.resolve("bin"));

        assertThat(new NodeLocator(env, dir.resolve("home"), false, dir.resolve("nope/node").toString()).find()).contains(onPath);
    }

    @Test
    void findsTheHighestNvmVersionWhenThePathHasNoNode() throws Exception {
        Path home = dir.resolve("home");
        executable(home.resolve(".nvm/versions/node/v9.11.2/bin/node"));
        Path newest = executable(home.resolve(".nvm/versions/node/v22.3.0/bin/node"));
        executable(home.resolve(".nvm/versions/node/v20.18.1/bin/node"));

        assertThat(new NodeLocator(Map.of("PATH", dir.resolve("empty").toString()), home, false, null).find()).contains(newest);
    }

    @Test
    void looksInVoltaAndFnmFolders() throws Exception {
        Path home = dir.resolve("home");
        Path fnm = executable(home.resolve(".local/share/fnm/node-versions/v21.0.0/installation/bin/node"));
        assertThat(new NodeLocator(Map.of(), home, false, null).find()).contains(fnm);
        Files.delete(fnm);
        Path volta = executable(home.resolve(".volta/bin/node"));
        assertThat(new NodeLocator(Map.of(), home, false, null).candidates()).contains(volta);
    }

    @Test
    void usesWindowsNamesAndFolders() throws Exception {
        Path programFiles = dir.resolve("Program Files");
        Path node = executable(programFiles.resolve("nodejs/node.exe"));
        Map<String, String> env = Map.of("Path", dir.resolve("empty").toString(), "ProgramFiles", programFiles.toString());

        NodeLocator locator = new NodeLocator(env, dir.resolve("home"), true, null);
        assertThat(locator.candidates()).contains(dir.resolve("empty").resolve("node.exe"));
        assertThat(locator.find()).contains(node);
    }

    @Test
    void ordersVersionFoldersNumerically() {
        assertThat(NodeLocator.versionKey("v22.3.0")).isGreaterThan(NodeLocator.versionKey("v9.11.2"));
        assertThat(NodeLocator.versionKey("20.10.0")).isGreaterThan(NodeLocator.versionKey("v20.9.1"));
    }

    @Test
    void addsNodeOnlyForDogsbayTranstypesThatDoNotSetIt() throws Exception {
        Path node = executable(dir.resolve("bin/node"));
        NodeLocator locator = new NodeLocator(Map.of("PATH", dir.resolve("bin").toString()), dir.resolve("home"), false, null);
        Map<String, String> params = Map.of("args.filter", "x.ditaval");

        assertThat(DogsbayPublish.withNode("html5", params, locator)).isSameAs(params);
        assertThat(DogsbayPublish.withNode("dogsbay", params, locator))
                .containsEntry("dogsbay.node", node.toString()).containsEntry("args.filter", "x.ditaval");
        assertThat(DogsbayPublish.withNode("dogsbay-site", null, locator)).containsEntry("dogsbay.node", node.toString());
        Map<String, String> own = Map.of("dogsbay.node", "/opt/node/bin/node");
        assertThat(DogsbayPublish.withNode("dogsbay", own, locator)).isSameAs(own);
    }

    @Test
    void explainsHowToGetNodeWhenADogsbayBuildFindsNone() {
        NodeLocator none = new NodeLocator(Map.of(), dir.resolve("home"), false, null) {
            @Override
            java.util.List<Path> candidates() {
                return java.util.List.of(dir.resolve("nothing/node"));
            }
        };
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> DogsbayPublish.withNode("dogsbay", Map.of(), none))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("Node.js 20 or later")
                .hasMessageContaining("Preferences > System > Node.js");
    }
}
