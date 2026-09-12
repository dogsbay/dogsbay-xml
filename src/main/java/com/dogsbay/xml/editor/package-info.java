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
 * Text editor with syntax highlighting. The main class is Editor (~6,000 lines)
 * which provides XML, Markdown, AsciiDoc, and DTD editing with tag completion,
 * bracket matching, and code folding. DocumentFormat and DocumentFormatRegistry
 * handle format detection by file extension. EditorPanel wraps the editor with
 * line numbers and overview margin.
 */

package com.dogsbay.xml.editor;
