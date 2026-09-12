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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Manages the discovery file at ~/.dogsbay/server.json.
 * Written on editor startup, deleted on shutdown. Allows the CLI
 * and external tools to discover the running editor instance.
 */
public class DiscoveryFile {

    private static final ObjectMapper mapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * The DogsBay config/discovery directory. Defaults to {@code ~/.dogsbay},
     * overridable via the {@code dogsbay.dir} system property so tests (and
     * sandboxed runs) can isolate it from the user's real directory.
     */
    private static Path discoveryDir() {
        String override = System.getProperty("dogsbay.dir");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), ".dogsbay");
    }

    private static Path discoveryFile() {
        return discoveryDir().resolve("server.json");
    }

    private static Path tokenFile() {
        return discoveryDir().resolve("auth_token");
    }

    private final String authToken;

    public DiscoveryFile() {
        this.authToken = loadOrCreateToken();
    }

    /**
     * Load a persistent token from ~/.dogsbay/auth_token, or create one
     * if it doesn't exist. The token survives editor restarts so that
     * MCP client configs (e.g., claude mcp add) don't need updating.
     */
    private String loadOrCreateToken() {
        try {
            if (Files.exists(tokenFile())) {
                String token = Files.readString(tokenFile()).strip();
                if (!token.isEmpty()) {
                    return token;
                }
            }
        } catch (IOException e) {
            // fall through to generate
        }

        String token = generateToken();
        try {
            Files.createDirectories(discoveryDir());
            writeOwnerOnly(tokenFile(), token);
        } catch (IOException e) {
            System.err.println("[Auth] Failed to persist token: " + e.getMessage());
        }
        return token;
    }

    /**
     * Delete the persisted auth token so a new one is generated on next startup.
     */
    public static void resetToken() {
        try {
            Files.deleteIfExists(tokenFile());
        } catch (IOException e) {
            System.err.println("Failed to delete token file: " + e.getMessage());
        }
    }

    /**
     * Write the discovery file with the current server info.
     */
    public void write(int httpPort, Path ipcSocket) throws IOException {
        Files.createDirectories(discoveryDir());

        ObjectNode json = mapper.createObjectNode();
        json.put("pid", ProcessHandle.current().pid());
        json.put("http_port", httpPort);
        json.put("mcp_endpoint", "http://localhost:" + httpPort + "/mcp");
        json.put("api_endpoint", "http://localhost:" + httpPort + "/api");
        json.put("auth_token", authToken);
        json.put("version", com.dogsbay.dogsbayaieditor.Identity.getIdentity().getVersion());
        json.put("started_at", Instant.now().toString());
        if (ipcSocket != null) {
            json.put("ipc_socket", ipcSocket.toAbsolutePath().toString());
        }

        writeOwnerOnly(discoveryFile(), mapper.writeValueAsString(json));
    }

    /**
     * Write text to a file that is owner-read/write only from the moment it is
     * created (no world-readable window before a later chmod). On POSIX the file
     * is created with mode 600; on other platforms it falls back to a plain
     * create + write.
     */
    private static void writeOwnerOnly(Path path, String content) throws IOException {
        Files.deleteIfExists(path);
        try {
            FileAttribute<?> ownerOnly = PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rw-------"));
            Files.createFile(path, ownerOnly);
        } catch (UnsupportedOperationException e) {
            // Non-POSIX (Windows) — create without the POSIX attribute.
            Files.createFile(path);
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    /**
     * Delete the discovery file on shutdown.
     */
    public void delete() {
        try {
            Files.deleteIfExists(discoveryFile());
        } catch (IOException e) {
            // best effort
        }
    }

    public String getAuthToken() {
        return authToken;
    }

    /**
     * Read the discovery file (used by CLI to find the editor).
     * Returns null if the editor is not running or the file is stale.
     */
    public static ServerInfo read() {
        try {
            if (!Files.exists(discoveryFile())) return null;

            var json = mapper.readTree(Files.readString(discoveryFile()));
            long pid = json.get("pid").asLong();

            // Verify PID is still alive
            if (!ProcessHandle.of(pid).isPresent()) {
                Files.deleteIfExists(discoveryFile());
                return null;
            }

            return new ServerInfo(
                pid,
                json.get("http_port").asInt(),
                json.has("ipc_socket") ? Path.of(json.get("ipc_socket").asText()) : null,
                json.get("auth_token").asText(),
                json.get("version").asText()
            );
        } catch (Exception e) {
            return null;
        }
    }

    public static Path getDiscoveryDir() {
        return discoveryDir();
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Info about a running editor instance, read from the discovery file.
     */
    public record ServerInfo(
        long pid,
        int httpPort,
        Path ipcSocket,
        String authToken,
        String version
    ) {}
}
