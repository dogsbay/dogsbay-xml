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

package com.dogsbay.dogsbayaieditor.update;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks GitHub Releases API for newer versions of the application.
 * Runs on a background thread and notifies via callback when an update is found.
 */
public class UpdateChecker {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateChecker.class);

    /** GitHub API endpoint. Change owner/repo to match your repository. */
    private static final String GITHUB_API_URL =
        "https://api.github.com/repos/dogsbay/dogsbay-xml/releases/latest";

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @FunctionalInterface
    public interface UpdateCallback {
        void onUpdateAvailable(String newVersion, String releaseUrl);
    }

    /**
     * Checks for updates asynchronously on a daemon thread.
     *
     * @param currentVersion the current application version (e.g. "3.3.1")
     * @param callback called on the background thread if an update is found
     */
    public static void checkAsync(String currentVersion, UpdateCallback callback) {
        Thread thread = new Thread(() -> check(currentVersion, callback), "update-checker");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Checks for updates synchronously (call from background thread).
     */
    public static void check(String currentVersion, UpdateCallback callback) {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API_URL))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "DogsBayXML-UpdateChecker")
                .timeout(TIMEOUT)
                .GET()
                .build();

            HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.debug("Update check returned status {}", response.statusCode());
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode release = mapper.readTree(response.body());

            String tagName = release.path("tag_name").asText("");
            String htmlUrl = release.path("html_url").asText("");

            if (tagName.isEmpty() || htmlUrl.isEmpty()) {
                LOG.debug("Update check: no tag_name or html_url in response");
                return;
            }

            Version latest = new Version(tagName);
            Version current = new Version(currentVersion);

            if (latest.isNewerThan(current)) {
                LOG.info("Update available: {} -> {}", current, latest);
                callback.onUpdateAvailable(latest.toString(), htmlUrl);
            } else {
                LOG.debug("No update available (current={}, latest={})", current, latest);
            }

        } catch (Exception e) {
            // Fail silently — update check is non-critical
            LOG.debug("Update check failed: {}", e.getMessage());
        }
    }
}
