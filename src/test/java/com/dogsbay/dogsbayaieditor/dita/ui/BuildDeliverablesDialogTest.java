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

package com.dogsbay.dogsbayaieditor.dita.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.ditaproject.Deliverable;

/** The Build Deliverables picker pre-ticks only the active deliverable. */
class BuildDeliverablesDialogTest {

    @Test
    void onlyTheActiveDeliverableIsTickedInitially() {
        Deliverable active = new Deliverable("User Guide (HTML)", Path.of("ug.ditamap"), null, "html5");
        assertEquals(Set.of("User Guide (HTML)"), BuildDeliverablesDialog.initialSelection(active),
                "active deliverable is ticked, everything else stays off");
    }

    @Test
    void nothingTickedWhenNoActiveDeliverable() {
        assertTrue(BuildDeliverablesDialog.initialSelection(null).isEmpty());
    }
}
