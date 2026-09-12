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

package com.dogsbay.dogsbayaieditor.ipc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The discovery file and its auth token must be created owner-read/write only,
 * with no world-readable window (the token is the sole auth secret).
 */
class DiscoveryFileTest {

    @AfterEach
    void clearOverride() {
        System.clearProperty("dogsbay.dir");
    }

    @Test
    void tokenAndDiscoveryFilesAreOwnerOnly(@TempDir Path dir) throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "POSIX permissions not supported on this platform");

        System.setProperty("dogsbay.dir", dir.toString());

        DiscoveryFile discovery = new DiscoveryFile();
        discovery.write(19601, null);

        Path token = dir.resolve("auth_token");
        Path server = dir.resolve("server.json");
        assertThat(token).exists();
        assertThat(server).exists();

        Set<PosixFilePermission> tokenPerms = Files.getPosixFilePermissions(token);
        Set<PosixFilePermission> serverPerms = Files.getPosixFilePermissions(server);

        assertThat(tokenPerms).containsExactlyInAnyOrder(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        assertThat(serverPerms).containsExactlyInAnyOrder(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    }
}
