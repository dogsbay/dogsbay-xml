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

import com.xagent.auth.AuthStorage;
import com.xagent.auth.CodexOAuth;
import com.xagent.auth.OAuthCredentials;

/**
 * "Sign in with ChatGPT" (OpenAI Codex OAuth) for the Swing UI. Runs xagent's
 * {@link CodexOAuth#loginViaBrowser} on a virtual thread — it starts a localhost
 * callback server, hands the authorization URL to the caller (open a browser),
 * blocks until the redirect, then persists the tokens via {@link AuthStorage}.
 * Mirrors {@code XAgentCli.handleLogin} so the editor and CLI share one login.
 */
public final class CodexLogin {

    private CodexLogin() {}

    /** Callbacks for the browser sign-in flow (invoked off the EDT). */
    public interface Listener {
        /** Open this URL in a browser for the user to authorize. */
        void onAuthUrl(String url);

        /** Tokens obtained and stored; the provider can switch to ChatGPT. */
        void onSuccess();

        /** Sign-in failed or was interrupted. */
        void onError(String message);
    }

    private static final Object LOCK = new Object();

    /** The sign-in still waiting for a browser, if there is one. */
    private static Thread inFlight;

    /** Set when the attempt was retired by a newer one, to keep it quiet. */
    private static java.util.concurrent.atomic.AtomicBoolean inFlightCancelled;

    /**
     * Begin the browser sign-in; returns immediately (work on a virtual thread).
     *
     * <p>
     * A sign-in already waiting is retired first. It holds the localhost
     * callback port for five minutes whether or not its browser tab still
     * exists, so closing the tab and asking again used to fail with "Address
     * already in use" until the old attempt timed out.
     */
    public static void start(Listener listener) {
        start(listener, new CodexOAuth());
    }

    /** As {@link #start(Listener)}, with the flow supplied — for tests. */
    static void start(Listener listener, CodexOAuth oauth) {
        synchronized (LOCK) {
            retireInFlight();
            java.util.concurrent.atomic.AtomicBoolean cancelled =
                    new java.util.concurrent.atomic.AtomicBoolean();
            inFlightCancelled = cancelled;
            inFlight = Thread.ofVirtual().name("codex-login").start(() -> {
                try {
                    OAuthCredentials credentials = oauth.loginViaBrowser(listener::onAuthUrl);
                    new AuthStorage().set(CodexOAuth.PROVIDER_ID, credentials);
                    if (!cancelled.get()) {
                        listener.onSuccess();
                    }
                } catch (Exception e) {
                    // A retired attempt reports nothing: its failure is the
                    // newer sign-in taking over, which the user just asked for.
                    if (!cancelled.get()) {
                        listener.onError(e.getMessage() == null ? e.toString() : e.getMessage());
                    }
                }
            });
        }
    }

    /**
     * Stop a sign-in that is still waiting for a browser, releasing the
     * callback port. Does nothing when none is waiting.
     */
    public static void cancel() {
        synchronized (LOCK) {
            retireInFlight();
        }
    }

    /**
     * Interrupt the waiting sign-in and give it a moment to release the port.
     * Interrupting unblocks its wait for the redirect, and the flow closes its
     * server on the way out; without the wait the next attempt can race it to
     * the bind and fail for the reason it was trying to avoid.
     */
    private static void retireInFlight() {
        Thread previous = inFlight;
        java.util.concurrent.atomic.AtomicBoolean cancelled = inFlightCancelled;
        inFlight = null;
        inFlightCancelled = null;
        if (previous == null || !previous.isAlive()) {
            return;
        }
        if (cancelled != null) {
            cancelled.set(true);
        }
        previous.interrupt();
        try {
            previous.join(PORT_RELEASE_WAIT.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** How long to wait for a retired sign-in to let go of the callback port. */
    private static final java.time.Duration PORT_RELEASE_WAIT = java.time.Duration.ofSeconds(3);

    /** Callbacks for the headless device-code flow (no local browser callback). */
    public interface DeviceListener {
        /** Show this code and URL — the user enters the code at the URL. */
        void onUserCode(String userCode, String verifyUrl);

        void onSuccess();

        void onError(String message);
    }

    /**
     * Begin the device-code sign-in (for headless environments or when the
     * localhost callback can't be used): request a user code, surface it, then
     * poll until the user approves. Returns immediately (work on a virtual thread).
     */
    public static void startDevice(DeviceListener listener) {
        cancel();   // the browser flow holds the callback port; this one does not need it
        Thread.ofVirtual().name("codex-login-device").start(() -> {
            try {
                CodexOAuth oauth = new CodexOAuth();
                CodexOAuth.DeviceAuth device = oauth.requestDeviceCode();
                listener.onUserCode(device.userCode(), device.verifyUrl());
                OAuthCredentials credentials = oauth.pollDeviceToken(device);
                new AuthStorage().set(CodexOAuth.PROVIDER_ID, credentials);
                listener.onSuccess();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                listener.onError("sign-in interrupted");
            } catch (Exception e) {
                listener.onError(e.getMessage() == null ? e.toString() : e.getMessage());
            }
        });
    }
}
