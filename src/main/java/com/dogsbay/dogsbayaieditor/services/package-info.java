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
 * Extracted services that were part of the DogsBayAIEditor monolith.
 * Each service is a focused class handling one responsibility:
 * ActionRegistry (actions), MenuBuilder (menus), DocumentManager (document lifecycle),
 * ViewManager (view/tab switching), EventBus (pub/sub), SchemaManager (grammar/validation),
 * ToolbarManager (toolbar). All are accessible to plugins via PluginContext.
 */

package com.dogsbay.dogsbayaieditor.services;
