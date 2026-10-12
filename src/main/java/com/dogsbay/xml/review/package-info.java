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
/**
 * Agent proposals in DITA's own markup: {@code status="new|deleted|changed"}
 * with {@code rev} as the author, and {@code <draft-comment>}. The model
 * works by textual splices into the document source, as the map editor and
 * refactorings do, so untouched content stays byte for byte as it was.
 * No Swing, no editor, no library dependencies.
 *
 * <p>See {@code plans/tracked-changes-and-comments.md}.
 */
package com.dogsbay.xml.review;
