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

package com.dogsbay.dogsbayaieditor.cli;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.dogsbay.dogsbayaieditor.ipc.DiscoveryFile;

/**
 * HTTP client for CLI → editor communication.
 * Sends JSON-RPC requests to the editor's embedded HTTP server.
 */
class EditorClient {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private final String baseUrl;
    private final String authToken;

    private EditorClient(String baseUrl, String authToken) {
        this.baseUrl = baseUrl;
        this.authToken = authToken;
    }

    /**
     * Connect to the running editor. Returns null if editor is not running.
     */
    static EditorClient connect() {
        var info = DiscoveryFile.read();
        if (info == null) return null;
        return new EditorClient(
            "http://localhost:" + info.httpPort(),
            info.authToken()
        );
    }

    /**
     * Send a JSON-RPC request and return the result node.
     * Throws RuntimeException on error.
     */
    JsonNode call(String method, ObjectNode params) throws Exception {
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", method);
        if (params != null) {
            request.set("params", params);
        }

        HttpRequest httpReq = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/rpc"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + authToken)
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(request)))
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> httpResp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
        JsonNode response = mapper.readTree(httpResp.body());

        if (response.has("error")) {
            JsonNode error = response.get("error");
            throw new RuntimeException(error.get("message").asText());
        }

        return response.get("result");
    }

    ObjectMapper getMapper() {
        return mapper;
    }
}
