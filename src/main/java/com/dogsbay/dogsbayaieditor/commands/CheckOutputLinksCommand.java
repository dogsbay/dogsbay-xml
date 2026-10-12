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

import java.util.List;

import com.dogsbay.dogsbayaieditor.commands.results.BrokenRef;

/**
 * Report links in built output that lead nowhere: a page that is not there, a fragment no element carries, an image that
 * was never copied.
 *
 * <p>Checking the source is not the same thing. A reference can be sound in the DITA and still break in the output, because
 * a topic was filtered out of this deliverable, a chunking rule moved content into another page, a copy-to renamed one, or
 * an image was referenced from a topic the build did not carry. Those faults exist only after a build and are what a reader
 * actually meets.
 *
 * <p>Links to other sites are not followed: this asks only whether the output is whole in itself, which is a question that
 * can be answered without a network and the same way every time.
 *
 * @param dir the folder the build wrote.
 */
public record CheckOutputLinksCommand(String dir) implements Command<List<BrokenRef>>, ReadOnlyCommand {
}
