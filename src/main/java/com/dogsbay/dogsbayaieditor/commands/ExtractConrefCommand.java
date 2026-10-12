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

package com.dogsbay.dogsbayaieditor.commands;

import com.dogsbay.dogsbayaieditor.commands.results.RefactorResult;

/**
 * Extract-to-conref: move the element with the given id from a topic into
 * a reuse topic (created if missing) and replace it with a {@code conref}
 * stub pointing at its new home. Dry-run by default.
 *
 * @param file      the topic containing the element
 * @param elementId the id of the element to extract
 * @param to        the reuse topic that receives the element
 * @param apply     true to execute; false returns the plan only
 */
public record ExtractConrefCommand(
    String file,
    String elementId,
    String to,
    boolean apply
) implements Command<RefactorResult> {}
