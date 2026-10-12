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

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.DogsBayView;

/**
 * Switches the active document to the Author (WYSIWYG) view — one of the three peer views
 * in the toolbar Views group (Editor / Author / Preview). Enabled only for XML documents
 * (the Author view is XML-only), matching the in-view Author nav button.
 */
public class ShowAuthorViewAction extends AbstractAction {

    private final DogsBayAIEditor parent;

    public ShowAuthorViewAction(DogsBayAIEditor parent) {
        super("Author");
        putValue(SMALL_ICON, DogsBayImageLoader.get().getImage(DogsBayView.AUTHOR_ICON));
        putValue(SHORT_DESCRIPTION, "Show the WYSIWYG Author view");
        this.parent = parent;
        setEnabled(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DogsBayView view = parent.getView();
        if (view != null) {
            // Route through the Author nav button so the shared listener performs the
            // switch — including the "ensure the document is well-formed" feedback and
            // selection restore when the active document's XML isn't well-formed.
            view.getAuthorButton().setSelected(true);
        }
    }
}
