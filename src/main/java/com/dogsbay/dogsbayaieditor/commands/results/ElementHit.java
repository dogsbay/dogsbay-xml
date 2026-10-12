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

package com.dogsbay.dogsbayaieditor.commands.results;

/**
 * Something found on screen by the text on it: what it is, what it says, and where it is.
 *
 * <p>The middle is what a driver points at. Nothing here presses anything — a recording moves its own pointer
 * to {@code centreX, centreY} and clicks there, so the pointer is still visible in the video and only its
 * aim comes from the name.
 *
 * @param what    the kind that was asked for: {@code row}, {@code field}, {@code tab}, and so on
 * @param text    the text as the thing itself spells it, which may differ in case from what was asked
 * @param window  the title of the window it was found in
 * @param x       the left edge, in screen coordinates
 * @param y       the top edge, in screen coordinates
 * @param width   its width
 * @param height  its height
 * @param centreX the middle, in screen coordinates — where to point
 * @param centreY the middle, in screen coordinates — where to point
 */
public record ElementHit(String what, String text, String window,
        int x, int y, int width, int height, int centreX, int centreY) {
}
