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

import com.dogsbay.dogsbayaieditor.commands.results.RangeHit;

/**
 * Where a stretch of the open document is on screen, boxed line by line.
 *
 * <p>{@link LocateCommand} answers for one character, found by the words around it. A caller
 * drawing a box over a phrase had to ask once per character and work out the wrapping itself.
 * This answers for the whole stretch: one rectangle per visual line, and whether the reader can
 * see all of it.
 *
 * <p>It takes offsets rather than text because the caller that wants this already has them — it
 * asked the document where a node is, or counted them itself. Finding a stretch by its words is
 * {@code locate}'s job, and the two are meant to be used together.
 *
 * <p>It changes nothing, so it needs no {@code --allow-driving}: looking is looking, and a
 * {@code screenshot} already shows the same thing.
 *
 * @param start where the stretch begins, as a character offset counted from 0
 * @param end   where it ends, not included
 */
public record LocateRangeCommand(int start, int end)
        implements Command<RangeHit>, ReadOnlyCommand {
}
