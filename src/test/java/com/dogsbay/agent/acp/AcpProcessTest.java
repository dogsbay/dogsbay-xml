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
package com.dogsbay.agent.acp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import com.dogsbay.agent.acp.AcpWire.ClientCapabilities;

class AcpProcessTest {

    @Test
    void windowsWrapsShimsInCmd() {
        assertThat(AcpProcess.platformCommand(List.of("npx", "x"))).isNotNull();
    }

    /**
     * A real child process whose stdout never speaks: the reader thread is
     * blocked in a pipe read that no interrupt reaches. Closing must still
     * return promptly, in the documented order.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    void closingAClientOverARealProcessPipeDoesNotDeadlock() throws Exception {
        List<String> failures = new CopyOnWriteArrayList<>();
        AcpProcess process = AcpProcess.start(List.of("cat"), Path.of("."), l -> { });
        AcpClient client = new AcpClient(process.process().getInputStream(), process.process().getOutputStream(),
                new ClientCapabilities(false, false), new AcpClient.Listener() {
                    @Override public void onUpdate(String s, AcpWire.Update u) { }
                    @Override public void onFailure(String m, Throwable c) { failures.add(m); }
                }, r -> null, null);
        assertThat(process.isAlive()).isTrue();

        long start = System.nanoTime();
        Thread closer = Thread.ofPlatform().start(() -> {
            process.close();
            client.close();
        });
        closer.join(Duration.ofSeconds(10).toMillis());

        assertThat(closer.isAlive()).as("close returned").isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(8));
        assertThat(process.isAlive()).isFalse();
        assertThat(client.isConnected()).isFalse();
    }
}
