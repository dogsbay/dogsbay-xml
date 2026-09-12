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

package com.dogsbay.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xagent.auth.CodexOAuth;

/**
 * The browser sign-in binds a localhost callback port and waits five minutes
 * for a redirect, whether or not the browser tab still exists. Closing the tab
 * and asking again failed with a bare "Address already in use" until that wait
 * expired, which looked like being stuck rather than like a retry.
 */
class CodexLoginRetryTest {

    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private static boolean bound(int port) {
        try (ServerSocket probe = new ServerSocket()) {
            probe.setReuseAddress(false);
            probe.bind(new InetSocketAddress("localhost", port), 1);
            return false;
        } catch (IOException inUse) {
            return true;
        }
    }

    private static boolean waitFor(java.util.function.BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    @AfterEach
    void stopAnythingLeftWaiting() {
        CodexLogin.cancel();
    }

    /** A listener that records what happened, and never completes the sign-in. */
    private static final class Watcher implements CodexLogin.Listener {
        private final CountDownLatch urlSeen = new CountDownLatch(1);
        private final AtomicReference<String> error = new AtomicReference<>();

        @Override public void onAuthUrl(String url) { urlSeen.countDown(); }
        @Override public void onSuccess() { }
        @Override public void onError(String message) { error.set(message); }
    }

    @Test
    @DisplayName("asking again retires the sign-in still holding the port")
    void askingAgainRetiresTheOldAttempt() throws Exception {
        int port = freePort();
        Watcher first = new Watcher();

        CodexLogin.start(first, new CodexOAuth(CodexOAuth.DEFAULT_ISSUER, port));
        assertThat(first.urlSeen.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(bound(port)).as("the first sign-in holds the callback port").isTrue();

        // What the user does: close the browser tab, type /login again.
        Watcher second = new Watcher();
        CodexLogin.start(second, new CodexOAuth(CodexOAuth.DEFAULT_ISSUER, port));

        assertThat(second.urlSeen.await(5, TimeUnit.SECONDS))
                .as("the second sign-in got its URL rather than an in-use port").isTrue();
        assertThat(second.error.get()).isNull();
    }

    @Test
    @DisplayName("the retired sign-in says nothing: its failure is the new one taking over")
    void theRetiredAttemptStaysQuiet() throws Exception {
        int port = freePort();
        Watcher first = new Watcher();
        CodexLogin.start(first, new CodexOAuth(CodexOAuth.DEFAULT_ISSUER, port));
        assertThat(first.urlSeen.await(5, TimeUnit.SECONDS)).isTrue();

        CodexLogin.start(new Watcher(), new CodexOAuth(CodexOAuth.DEFAULT_ISSUER, port));
        Thread.sleep(200);

        // "Login interrupted" in the panel would read as a fault, not as the
        // retry the user just asked for.
        assertThat(first.error.get()).isNull();
    }

    @Test
    @DisplayName("cancelling lets go of the callback port")
    void cancellingReleasesThePort() throws Exception {
        int port = freePort();
        Watcher watcher = new Watcher();
        CodexLogin.start(watcher, new CodexOAuth(CodexOAuth.DEFAULT_ISSUER, port));
        assertThat(watcher.urlSeen.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(bound(port)).isTrue();

        CodexLogin.cancel();

        assertThat(waitFor(() -> !bound(port))).as("port released after cancel").isTrue();
    }

    @Test
    @DisplayName("cancelling with nothing waiting is harmless")
    void cancellingNothingIsFine() {
        CodexLogin.cancel();
        CodexLogin.cancel();
    }

    @Test
    @DisplayName("a port held by something else explains itself")
    void aPortHeldElsewhereIsExplained() throws Exception {
        int port = freePort();
        try (ServerSocket squatter = new ServerSocket()) {
            squatter.bind(new InetSocketAddress("localhost", port), 1);
            Watcher watcher = new Watcher();

            CodexLogin.start(watcher, new CodexOAuth(CodexOAuth.DEFAULT_ISSUER, port));

            assertThat(waitFor(() -> watcher.error.get() != null)).isTrue();
            // "Address already in use" alone sends people hunting for a process.
            assertThat(watcher.error.get()).contains("callback port " + port);
            assertThat(watcher.error.get()).contains("waiting for its browser tab");
        }
    }
}
