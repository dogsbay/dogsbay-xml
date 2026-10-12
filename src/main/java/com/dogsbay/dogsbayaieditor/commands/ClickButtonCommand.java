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

import com.dogsbay.dogsbayaieditor.commands.results.ButtonHit;

/**
 * Press a button in a dialog by its name, the way a person clicking it does.
 *
 * <p>For anything driving the editor from outside: a recording, a test, a script. {@link InvokeActionCommand}
 * covers the menus; this covers what the menus open. The alternative was clicking at a screen position measured
 * from a screenshot, which moves when a window manager adds a title bar, when the theme or the font changes,
 * when the display's scaling changes, or when the dialog gains a line of text — and a recording that mis-clicks
 * does not fail, it does something else and carries on.
 *
 * @param label  the button's text, matched on the trimmed text ignoring case and a trailing ellipsis, so
 *               {@code Add} finds {@code Add…}
 * @param window the title of the window to look in; null for the active one, which for a modal dialog is the
 *               dialog
 * @param press  whether to press it. False finds the button and reports where it is without touching it, for a
 *               driver that wants its own pointer to travel there and click, so a viewer sees which button is
 *               being pressed instead of a dialog closing by itself. It finds the button by the same rules and
 *               refuses for the same reasons; what it does not need is {@code --allow-driving}, since looking
 *               changes nothing.
 */
public record ClickButtonCommand(String label, String window, boolean press) implements Command<ButtonHit> {
}
