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

package com.dogsbay.agent.app;

import java.nio.file.Path;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.dogsbay.agent.runtime.AgentChatController;

/**
 * Standalone agent chat — the same panel the editor plugin docks, hosted in a
 * plain {@link JFrame} via {@link StandaloneAgentHost}.
 *
 * <p>Doubles as the dev/test harness for the chat UI: iterate without booting
 * the editor. Responsive with no credentials — the provider bar lets you pick a
 * provider, paste a key, or use local Ollama.
 *
 * <pre>./gradlew runAgentApp            # cwd = project dir
 * java ... com.dogsbay.agent.app.AgentApp /path/to/workdir</pre>
 */
public final class AgentApp {

    private AgentApp() {}

    public static void main(String[] args) {
        Path cwd = Path.of(args.length > 0 ? args[0] : System.getProperty("user.dir"))
                .toAbsolutePath().normalize();

        SwingUtilities.invokeLater(() -> {
            StandaloneAgentHost host = new StandaloneAgentHost(cwd);

            JFrame frame = new JFrame("DogsBay Agent — " + cwd);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(760, 680);
            frame.setLocationRelativeTo(null);
            host.setDialogParent(frame);

            AgentChatController controller = new AgentChatController(host);
            frame.setContentPane(controller.panel());
            frame.setVisible(true);
        });
    }
}
