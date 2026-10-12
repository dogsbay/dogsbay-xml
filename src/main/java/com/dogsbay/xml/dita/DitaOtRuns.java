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

package com.dogsbay.xml.dita;

/**
 * One in-process DITA-OT run at a time. DITA-OT keeps process-wide state (its configuration and the
 * store factory among it), and two builds in one JVM collide: the second fails with "Unsupported store
 * type file". Nothing in the editor ran two at once until two live previews republished together
 * (2026-09-19); a validation or a Build Deliverables during a preview's republish would collide the same
 * way. Every in-process run holds this lock, so they queue instead.
 */
public final class DitaOtRuns {

    /** Held for the whole of an in-process DITA-OT run. */
    public static final Object LOCK = new Object();

    private DitaOtRuns() {
    }
}
