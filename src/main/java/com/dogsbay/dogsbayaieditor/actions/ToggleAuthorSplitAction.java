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

package com.dogsbay.dogsbayaieditor.actions;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JCheckBoxMenuItem;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.DogsBayView;

/**
 * Toggles the Editor + Author side-by-side split for the active document — the
 * intra-document pairing in the independently-switchable view model (the Editor/Author +
 * Preview pairings come from the Preview-split action). XML-only, like the Author view.
 */
public class ToggleAuthorSplitAction extends AbstractAction {

    private final DogsBayAIEditor parent;

    public ToggleAuthorSplitAction(DogsBayAIEditor parent) {
        super("Split: Editor + Author");
        putValue(SMALL_ICON, DogsBayImageLoader.get()
                .getImage("com/dogsbay/dogsbayaieditor/icons/sidebar/layout-panel.png"));
        putValue(SHORT_DESCRIPTION, "Show the XML source and the Author view side by side");
        this.parent = parent;
        setEnabled(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DogsBayView view = parent.getView();
        if (view != null) {
            // Route through the View-menu checkbox so the shared listener performs the
            // toggle — including the "ensure the document is well-formed" feedback and
            // returning to the live single view when toggled off.
            JCheckBoxMenuItem item = view.getAuthorSplitItem();
            item.setSelected(!item.isSelected());
        }
    }
}
