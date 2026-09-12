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
package com.dogsbay.agent.session;

/**
 * What a session is <em>offered</em>. A tier is decided when the session is
 * opened and never by the model. Enforcement of the file-system tiers lands
 * with the ACP client; the registry records the tier from day one so audit
 * entries and the UI badge are attributable.
 */
public enum CapabilityTier {
    /** Typed editor commands only; no file channel is served. */
    T1_COMMANDS,
    /** T1 plus read-only file access. */
    T2_READS,
    /** T1 plus read and write file access. Shown with a persistent badge. */
    T3_DEVELOPER;

    public boolean allowsFileReads() {
        return this != T1_COMMANDS;
    }

    public boolean allowsFileWrites() {
        return this == T3_DEVELOPER;
    }
}
