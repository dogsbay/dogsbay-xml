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

import com.dogsbay.dogsbayaieditor.commands.results.LivePreviewStatus;

/**
 * Start or stop a deliverable's live preview in the running editor: the deliverable is
 * published with the {@code dogsbay} transtype, {@code dogsbay site dev} serves it, and
 * changes in the project republish it. Starting returns at once; poll
 * {@link LivePreviewStatusCommand} until the preview is ready.
 *
 * @param root        the project root
 * @param deliverable the deliverable to preview; null picks the only dogsbay deliverable,
 *                    else the active one
 * @param action      {@code start} or {@code stop}
 */
public record LivePreviewCommand(
    String root,
    String deliverable,
    String action
) implements Command<LivePreviewStatus> {}
