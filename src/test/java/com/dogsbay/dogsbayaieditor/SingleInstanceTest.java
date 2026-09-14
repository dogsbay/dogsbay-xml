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

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class SingleInstanceTest {

    @Test
    void aRunningEditorTakesTheFileAndSaysSo() throws Exception {
        BlockingQueue<String> opened = new ArrayBlockingQueue<>(1);
        try (ServerSocket server = SingleInstance.bind(0)) {
            SingleInstance.listen(server, opened::add);

            assertThat(SingleInstance.handOff(server.getLocalPort(), "-editor", 500, 2000)).isTrue();
            assertThat(opened.poll(2, TimeUnit.SECONDS)).isEqualTo("-editor");
        }
    }

    @Test
    void aListenerThatNeverRepliesIsNotARunningEditor() throws Exception {
        // Bound but never accepting: what a process that crashed after binding leaves behind.
        try (ServerSocket stale = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            long started = System.nanoTime();

            assertThat(SingleInstance.handOff(stale.getLocalPort(), "-editor", 500, 300)).isFalse();
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2000);
        }
    }

    @Test
    void nothingListeningMeansStartAnEditor() throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            port = probe.getLocalPort();
        }

        assertThat(SingleInstance.handOff(port, "-editor", 500, 300)).isFalse();
    }

    @Test
    void longAndNonAsciiPathsArriveWhole() throws Exception {
        String path = "C:\\Users\\Zoë\\Documents\\" + "deep\\".repeat(80) + "topic.dita";
        BlockingQueue<String> opened = new ArrayBlockingQueue<>(1);
        try (ServerSocket server = SingleInstance.bind(0)) {
            SingleInstance.listen(server, opened::add);

            assertThat(SingleInstance.handOff(server.getLocalPort(), path, 500, 2000)).isTrue();
            assertThat(opened.poll(2, TimeUnit.SECONDS)).isEqualTo(path);
        }
    }

    @Test
    void aTakenPortCannotBeBoundTwice() throws Exception {
        try (ServerSocket first = SingleInstance.bind(0)) {
            assertThat(SingleInstance.bind(first.getLocalPort())).isNull();
        }
    }
}
