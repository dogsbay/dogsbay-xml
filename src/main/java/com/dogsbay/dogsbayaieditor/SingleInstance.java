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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * Keeps one editor per user session: a later launch hands its file to the running
 * editor over a loopback port instead of opening a second window.
 *
 * <p>A hand-off counts only when the running editor replies. Anything else that
 * accepts on the port (a process that crashed after binding it, or another program)
 * gets no reply within the timeout, and the launch starts its own editor rather than
 * exiting without a window.
 */
final class SingleInstance {

    /** The reply that confirms a running editor took the file. */
    static final int ACK = 'K';

    private SingleInstance() {}

    /**
     * Offer {@code path} to an editor already listening on {@code port}.
     *
     * @return true only when a running editor acknowledged it
     */
    static boolean handOff(int port, String path, int connectTimeoutMs, int replyTimeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), connectTimeoutMs);
            socket.setSoTimeout(replyTimeoutMs);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF(path);
            out.flush();
            return socket.getInputStream().read() == ACK;
        } catch (IOException e) {
            // Nothing listening, or a listener that never answered.
            return false;
        }
    }

    /** Listen on loopback, or null when the port is taken. */
    static ServerSocket bind(int port) {
        try {
            return new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Accept hand-offs on {@code server} in a daemon thread, acknowledge each, and pass
     * its path to {@code open}.
     */
    static Thread listen(ServerSocket server, Consumer<String> open) {
        Thread listener = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket socket = server.accept()) {
                    String path = new DataInputStream(socket.getInputStream()).readUTF();
                    socket.getOutputStream().write(ACK);
                    socket.getOutputStream().flush();
                    open.accept(path);
                } catch (IOException e) {
                    if (server.isClosed()) {
                        return;
                    }
                    // One bad client must not stop later hand-offs.
                }
            }
        }, "single-instance-listener");
        listener.setDaemon(true);
        listener.start();
        return listener;
    }
}
