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

import org.junit.jupiter.api.Test;

import com.xagent.permission.PermissionDecision;

/**
 * The dialog itself is interactive (can't be unit-tested without blocking), so
 * we verify the choice→decision mapping in isolation. "Closed dialog" (-1) and
 * any unexpected index must be the safe default: DENY.
 */
class SwingPermissionHandlerTest {

    @Test
    void mapsOptionIndexToDecision() {
        assertThat(SwingPermissionHandler.mapChoice(0)).isEqualTo(PermissionDecision.ALLOW);
        assertThat(SwingPermissionHandler.mapChoice(1)).isEqualTo(PermissionDecision.ALLOW_SESSION);
        assertThat(SwingPermissionHandler.mapChoice(2)).isEqualTo(PermissionDecision.ALLOW_ALWAYS);
        assertThat(SwingPermissionHandler.mapChoice(3)).isEqualTo(PermissionDecision.DENY);
    }

    @Test
    void closedOrUnknownChoiceDenies() {
        assertThat(SwingPermissionHandler.mapChoice(-1)).isEqualTo(PermissionDecision.DENY);
        assertThat(SwingPermissionHandler.mapChoice(99)).isEqualTo(PermissionDecision.DENY);
    }
}
