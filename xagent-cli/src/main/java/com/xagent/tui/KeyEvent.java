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
package com.xagent.tui;

/**
 * Parsed keystroke event from terminal input.
 */
public sealed interface KeyEvent {
	record Character(char ch) implements KeyEvent {}
	record Enter() implements KeyEvent {}
	record Backspace() implements KeyEvent {}
	record Delete() implements KeyEvent {}
	record Up() implements KeyEvent {}
	record Down() implements KeyEvent {}
	record Left() implements KeyEvent {}
	record Right() implements KeyEvent {}
	record Home() implements KeyEvent {}
	record End() implements KeyEvent {}
	record PageUp() implements KeyEvent {}
	record PageDown() implements KeyEvent {}
	record CtrlC() implements KeyEvent {}
	record CtrlD() implements KeyEvent {}
	record Escape() implements KeyEvent {}
	record Tab() implements KeyEvent {}
	record ScrollUp() implements KeyEvent {}
	record ScrollDown() implements KeyEvent {}
	record Unknown() implements KeyEvent {}
}
