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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.KeyStroke;

import com.dogsbay.xml.browser.HtmlPreviewManager;
import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.DogsBayImageLoader;
import com.dogsbay.dogsbayaieditor.DogsBayView;

/**
 * Action that opens an HTML preview of the current document in a tab.
 *
 * @author DogsBay Ltd
 */
public class OpenPreviewAction extends AbstractAction {
    private DogsBayAIEditor parent = null;

    public OpenPreviewAction(DogsBayAIEditor parent) {
        super("Preview in Tab");

        putValue(MNEMONIC_KEY, Integer.valueOf('w'));
        putValue(SMALL_ICON, DogsBayImageLoader.get().getImage(DogsBayView.BROWSER_ICON));
        putValue(SHORT_DESCRIPTION, "Preview the document as HTML in an editor tab");
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));

        this.parent = parent;

        setEnabled(false);
    }

    public void actionPerformed(ActionEvent e) {
        HtmlPreviewManager.openPreview(parent);
    }
}
