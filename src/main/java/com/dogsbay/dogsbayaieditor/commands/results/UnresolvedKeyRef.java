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

package com.dogsbay.dogsbayaieditor.commands.results;

/**
 * A key reference one deliverable's build could not resolve, with no {@code @href} to fall back on.
 *
 * <p>Per deliverable, and that is the point: the key can be defined in the project and missing from the scope
 * this deliverable builds, so the link vanishes from this output and no other. The health report resolves
 * against the project's root map and cannot see it.
 *
 * @param deliverable the deliverable whose build could not resolve it
 * @param file        the source file holding the reference, relative to the project root
 * @param line        1-based line of the referencing element, or -1 when the note carried none
 * @param key         the key name the reference asked for
 */
public record UnresolvedKeyRef(String deliverable, String file, int line, String key) {
}
