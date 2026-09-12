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

import com.dogsbay.xml.editor.DocumentFormat;
import com.dogsbay.dogsbayaieditor.services.ActionRegistry;
import com.dogsbay.dogsbayaieditor.services.DocumentManager;
import com.dogsbay.dogsbayaieditor.services.EventBus;
import com.dogsbay.dogsbayaieditor.services.SchemaManager;
import com.dogsbay.dogsbayaieditor.services.ToolbarManager;
import com.dogsbay.dogsbayaieditor.services.ViewManager;

/**
 * Provides plugins with access to application services.
 * Plugins should use this instead of referencing DogsBayAIEditor directly,
 * keeping a clean separation between the plugin and the host application.
 */
public interface PluginContext {

    /** Access document lifecycle operations (open, close, save). */
    DocumentManager getDocumentManager();

    /** Access view/tab management operations. */
    ViewManager getViewManager();

    /** Access schema/grammar/validation operations. */
    SchemaManager getSchemaManager();

    /** Access the action registry for looking up or registering actions. */
    ActionRegistry getActionRegistry();

    /** Access the event bus for publish/subscribe messaging. */
    EventBus getEventBus();

    /** Access toolbar management. */
    ToolbarManager getToolbarManager();

    /** Access the UI service for contributing menus, tabs, panels, and status messages. */
    UIService getUIService();

    /**
     * Register a document format for file extension detection, syntax highlighting,
     * outline, and preview. Formats registered earlier take priority when multiple
     * formats match the same extension.
     */
    void registerFormat(DocumentFormat format);
}
