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

package com.dogsbay.dogsbayaieditor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The remote http/https open/save path now uses the JDK {@code java.net.http}
 * client (replacing the retired commons-httpclient/WebDAV stack). These tests
 * exercise the GET/PUT helpers against a loopback {@link HttpServer}: content
 * round-trips, HTTP Basic credentials are sent, and non-2xx responses surface
 * as {@link IOException}.
 */
class UrlUtilitiesHttpTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void httpGetReturnsBody() throws Exception {
        server.createContext("/doc.xml", exchange -> {
            byte[] body = "<root>remote</root>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        try (InputStream in = URLUtilities.httpGet(baseUrl + "/doc.xml", null, null)) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).isEqualTo("<root>remote</root>");
        }
    }

    @Test
    void httpPutSendsBody() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        server.createContext("/upload.xml", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        URLUtilities.httpPut(baseUrl + "/upload.xml", stream("<root>uploaded</root>"), null, null);

        assertThat(received.get()).isEqualTo("<root>uploaded</root>");
    }

    @Test
    void sendsBasicAuthHeaderWhenCredentialsPresent() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        server.createContext("/secure.xml", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        URLUtilities.httpPut(baseUrl + "/secure.xml", stream("x"), "alice", "s3cret");

        String expected = "Basic " + java.util.Base64.getEncoder()
                .encodeToString("alice:s3cret".getBytes(StandardCharsets.UTF_8));
        assertThat(auth.get()).isEqualTo(expected);
    }

    @Test
    void putThrowsOnNonSuccessStatus() {
        server.createContext("/fail.xml", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        assertThatThrownBy(() ->
                URLUtilities.httpPut(baseUrl + "/fail.xml", stream("x"), null, null))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("500");
    }

    @Test
    void getThrowsOnNonSuccessStatus() {
        server.createContext("/missing.xml", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        assertThatThrownBy(() ->
                URLUtilities.httpGet(baseUrl + "/missing.xml", null, null))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("404");
    }

    @Test
    void saveRoutesHttpThroughPut() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        server.createContext("/saved.xml", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });

        URLUtilities.save(new java.net.URL(baseUrl + "/saved.xml"),
                stream("<doc>via save</doc>"), "UTF-8");

        assertThat(received.get()).isEqualTo("<doc>via save</doc>");
    }
}
