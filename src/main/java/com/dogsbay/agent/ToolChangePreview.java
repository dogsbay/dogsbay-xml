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

package com.dogsbay.agent;

/**
 * A before/after preview of a mutating tool call, shown for approval so the user
 * sees the actual change rather than approving a blind yes/no. {@code before} may
 * be empty (e.g. inserting at the cursor with nothing selected).
 *
 * @param title  the tool / change being made (e.g. "replace_selection")
 * @param before the current text that will be replaced
 * @param after  the proposed new text
 */
public record ToolChangePreview(String title, String before, String after) {}
