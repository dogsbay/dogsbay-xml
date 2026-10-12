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

package com.dogsbay.xml.author.model.ops;

import java.util.List;

import com.dogsbay.xml.author.model.AuthorBlock;
import com.dogsbay.xml.author.model.AuthorDocument;
import com.dogsbay.xml.author.model.BlockOp;
import com.dogsbay.xml.author.model.InlineRun;

/** Replaces the full inline text of a text block. */
public final class SetTextOp implements BlockOp {

    private final AuthorBlock block;
    private final List<InlineRun> newRuns;
    private final List<InlineRun> oldRuns;

    public SetTextOp(AuthorBlock block, List<InlineRun> newRuns) {
        this.block = block;
        this.newRuns = List.copyOf(newRuns);
        this.oldRuns = block.getText();
    }

    @Override
    public void apply(AuthorDocument doc) {
        doc.setBlockText(block, newRuns);
    }

    @Override
    public void revert(AuthorDocument doc) {
        doc.setBlockText(block, oldRuns);
    }
}
