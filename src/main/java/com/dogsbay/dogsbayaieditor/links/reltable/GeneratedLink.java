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

package com.dogsbay.dogsbayaieditor.links.reltable;

import java.io.File;

/**
 * One related-link a reltable would generate at output: from {@code source} topic to
 * {@code target} topic, originating in {@code row} of the reltable.
 *
 * @param source the topic the link appears on
 * @param target the topic it links to
 * @param text   display text (the target's navtitle / file name)
 * @param row    0-based row index in the reltable (traceability)
 */
public record GeneratedLink(File source, File target, String text, int row) {}
