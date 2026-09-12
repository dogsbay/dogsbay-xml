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

import com.dogsbay.agent.acp.AcpWire.AgentInfo;
import com.dogsbay.agent.acp.AcpWire.ClientCapabilities;
import com.dogsbay.agent.session.CapabilityTier;

/**
 * What each tier offers a hosted agent, as the {@code initialize}
 * capabilities. A tier is decided when the session is opened and is never
 * changed by the agent. {@code AgentTierOfferingTest} asserts the offered
 * set per tier on the wire.
 */
public final class Tiers {

    private Tiers() {
    }

    public static ClientCapabilities capabilities(CapabilityTier tier) {
        return switch (tier) {
            case T1_COMMANDS -> new ClientCapabilities(false, false);
            case T2_READS -> new ClientCapabilities(true, false);
            case T3_DEVELOPER -> new ClientCapabilities(true, true);
        };
    }

    /**
     * Protocol v2 removed the client-served file system, so a v2 agent reads
     * and writes the disk itself whatever we advertise: it is Tier 3 by
     * construction and the UI must say so.
     */
    public static boolean isDeveloperByConstruction(AgentInfo agent) {
        return agent != null && agent.protocolVersion() >= 2;
    }

    public static String badge(CapabilityTier tier) {
        return switch (tier) {
            case T1_COMMANDS -> "T1";
            case T2_READS -> "T2";
            case T3_DEVELOPER -> "T3";
        };
    }

    public static String describe(CapabilityTier tier) {
        return switch (tier) {
            case T1_COMMANDS -> "Commands only: the editor offers its DITA tools and no file access";
            case T2_READS -> "Commands plus reading project files through the editor, unsaved buffers included";
            case T3_DEVELOPER -> "Commands plus reading and writing project files through the editor (writes pass the gate)";
        };
    }
}
