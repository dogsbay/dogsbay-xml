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

package com.dogsbay.dogsbayaieditor.plugin;

import com.dogsbay.dogsbayaieditor.DogsBayAIEditor;
import com.dogsbay.dogsbayaieditor.services.ActionRegistry;
import com.dogsbay.dogsbayaieditor.services.DocumentManager;
import com.dogsbay.dogsbayaieditor.services.EventBus;
import com.dogsbay.dogsbayaieditor.services.SchemaManager;
import com.dogsbay.dogsbayaieditor.services.ToolbarManager;
import com.dogsbay.dogsbayaieditor.services.ViewManager;

/**
 * Default implementation of {@link PluginContext} that delegates
 * to the {@link DogsBayAIEditor}'s services.
 */
public class DefaultPluginContext implements PluginContext {

    private final DogsBayAIEditor editor;
    private final UIService uiService;

    public DefaultPluginContext(DogsBayAIEditor editor) {
        this.editor = editor;
        this.uiService = new DefaultUIService(editor);
    }

    @Override
    public DocumentManager getDocumentManager() {
        return editor.getDocumentManager();
    }

    @Override
    public ViewManager getViewManager() {
        return editor.getViewManager();
    }

    @Override
    public SchemaManager getSchemaManager() {
        return editor.getSchemaManager();
    }

    @Override
    public ActionRegistry getActionRegistry() {
        return editor.getActionRegistry();
    }

    @Override
    public EventBus getEventBus() {
        return editor.getEventBus();
    }

    @Override
    public ToolbarManager getToolbarManager() {
        return editor.getToolbarManager();
    }

    @Override
    public UIService getUIService() {
        return uiService;
    }

    @Override
    public void registerFormat(com.dogsbay.xml.editor.DocumentFormat format) {
        com.dogsbay.xml.editor.DocumentFormatRegistry.register(format);
    }

    /**
     * Returns the underlying DogsBayAIEditor. This is intended for built-in
     * plugins that need deeper access during the transition to a fully
     * decoupled plugin API. Community plugins should not use this.
     */
    public DogsBayAIEditor getEditor() {
        return editor;
    }
}
