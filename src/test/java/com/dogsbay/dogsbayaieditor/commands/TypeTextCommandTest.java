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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.commands.results.CommandResult;

/** Typing: the rate, and the rule that it stops rather than following the focus. */
class TypeTextCommandTest {

    /** A keyboard that remembers what it was given, with a focus the test can move. */
    private static final class Recorder implements EditorExecutor.Keyboard {
        final StringBuilder typed = new StringBuilder();
        final AtomicReference<Object> focus = new AtomicReference<>("the editor");
        Object target;

        @Override
        public Object focusTarget() {
            target = focus.get();
            return target;
        }

        @Override
        public Object pressIfStillFocused(Object wanted, char c) {
            Object holder = focus.get();
            if (holder != wanted) {
                return holder;
            }
            typed.append(c);
            return null;
        }
    }

    @Test
    void typesEveryCharacterInOrder() throws Exception {
        Recorder keyboard = new Recorder();

        CommandResult result = EditorExecutor.type("<p>Hello</p>", 0, keyboard, ms -> { });

        assertThat(keyboard.typed.toString()).isEqualTo("<p>Hello</p>");
        assertThat(result.message()).contains("12 characters");
    }

    @Test
    void newlinesAndTabsAreKeysLikeAnyOther() throws Exception {
        Recorder keyboard = new Recorder();
        EditorExecutor.type("a\n\tb", 0, keyboard, ms -> { });
        // they reach the component as Enter and Tab, which is what makes indenting and completion behave
        assertThat(keyboard.typed.toString()).isEqualTo("a\n\tb");
    }

    @Test
    void stopsWhenSomethingTakesTheFocusAndSaysWhatDid() {
        Recorder keyboard = new Recorder();
        // a completion popup, a dialog, anything: it takes the focus after two characters
        EditorExecutor.Pause steal = ms -> {
            if (keyboard.typed.length() == 2) {
                keyboard.focus.set(new javax.swing.JTextField());
            }
        };

        assertThatThrownBy(() -> EditorExecutor.type("abcdef", 1, keyboard, steal))
                .isInstanceOf(CommandException.class)
                .hasMessageContaining("the focus moved to")
                .hasMessageContaining("JTextField")
                .hasMessageContaining("Stopped after 2 of 6 characters");

        // exactly what was typed before it moved, and nothing after: following the focus is what this refuses to do
        assertThat(keyboard.typed.toString()).isEqualTo("ab");
    }

    @Test
    void refusesToTypeForLongerThanACallIsAnsweredIn() {
        // at a watchable five a second this is reached at 150 characters, well inside the intended use; the caller's
        // request would time out while the editor typed on, and a retry would type it twice
        TypeTextCommand slowAndLong = new TypeTextCommand("x".repeat(400), 5);
        assertThat(slowAndLong.fault()).contains("Type faster");
        assertThat(new TypeTextCommand("x".repeat(400), 100).fault()).isNull();
        assertThat(new TypeTextCommand("x".repeat(40), 5).fault()).isNull();
    }

    // ── The keys themselves ──────────────────────────────────────────

    @Test
    void sendsThreeEventsPerKeyInTheOrderAKeyboardMakesThem() {
        List<KeyEvent> events = new ArrayList<>();
        javax.swing.JTextField source = new javax.swing.JTextField();

        EditorExecutor.sendKey(events::add, source, 'a');

        assertThat(events).hasSize(3);
        assertThat(events.get(0).getID()).isEqualTo(KeyEvent.KEY_PRESSED);
        assertThat(events.get(1).getID()).isEqualTo(KeyEvent.KEY_TYPED);
        assertThat(events.get(2).getID()).isEqualTo(KeyEvent.KEY_RELEASED);
        // the character rides on the typed event, which is what a text component inserts from
        assertThat(events.get(1).getKeyChar()).isEqualTo('a');
        assertThat(events.get(0).getKeyCode()).isEqualTo(KeyEvent.VK_A);
        assertThat(events.get(0).getSource()).isSameAs(source);
    }

    @Test
    void theKeysThatAreNotCharactersAreNamedRatherThanGuessed() {
        // an editor's completion and indenting key off the text inserted, but Enter and Tab have to arrive as those keys
        assertThat(EditorExecutor.keyCodeFor('\n')).isEqualTo(KeyEvent.VK_ENTER);
        assertThat(EditorExecutor.keyCodeFor('\t')).isEqualTo(KeyEvent.VK_TAB);
        assertThat(EditorExecutor.keyCodeFor('\b')).isEqualTo(KeyEvent.VK_BACK_SPACE);
        assertThat(EditorExecutor.keyCodeFor('<')).isEqualTo(KeyEvent.VK_LESS);
        assertThat(EditorExecutor.keyCodeFor('>')).isEqualTo(KeyEvent.VK_GREATER);
        // and a character AWT has no key for still types, because the character rides on the typed event
        assertThat(EditorExecutor.keyCodeFor('\u4e2d')).isEqualTo(KeyEvent.VK_UNDEFINED);
    }

    @Test
    void refusesWhenNothingHasEverHadFocus() {
        Recorder keyboard = new Recorder();
        keyboard.focus.set(null);

        assertThatThrownBy(() -> EditorExecutor.type("a", 0, keyboard, ms -> { }))
                .isInstanceOf(CommandException.class)
                .hasMessageContaining("nowhere to type");
        assertThat(keyboard.typed).isEmpty();
    }

    @Test
    void refusesEmptyText() {
        Recorder keyboard = new Recorder();
        assertThatThrownBy(() -> EditorExecutor.type("", 0, keyboard, ms -> { }))
                .isInstanceOf(CommandException.class)
                .hasMessageContaining("no text to type");
        assertThatThrownBy(() -> EditorExecutor.type(null, 0, keyboard, ms -> { }))
                .isInstanceOf(CommandException.class);
    }

    @Test
    void waitsBetweenKeysButNotAfterTheLast() throws Exception {
        Recorder keyboard = new Recorder();
        List<Long> pauses = new ArrayList<>();

        EditorExecutor.type("abc", 50, keyboard, pauses::add);

        // three keys, two gaps: a trailing pause would only delay the answer
        assertThat(pauses).containsExactly(50L, 50L);
    }

    // ── The rate ─────────────────────────────────────────────────────

    @Test
    void typesAtAWatchableRateByDefault() {
        TypeTextCommand cmd = new TypeTextCommand("hello");
        assertThat(cmd.rate()).isEqualTo(TypeTextCommand.DEFAULT_CPS);
        assertThat(cmd.gapMs()).isEqualTo(50); // 20 a second
    }

    @Test
    void zeroMeansAsFastAsTheEditorWillTakeIt() {
        assertThat(new TypeTextCommand("hello", 0).gapMs()).isZero();
    }

    @Test
    void aSillyRateIsBroughtBackToSomethingPossible() {
        // above a thousand a second the gap is shorter than the interface can honour, so it is the same as no gap
        assertThat(new TypeTextCommand("x", 100_000).rate()).isEqualTo(TypeTextCommand.MAX_CPS);
        assertThat(new TypeTextCommand("x", 100_000).gapMs()).isEqualTo(1);
        assertThat(new TypeTextCommand("x", -5).rate()).isEqualTo(TypeTextCommand.DEFAULT_CPS);
    }

    @Test
    void typingIsDrivingSoItIsNotMarkedReadOnly() {
        assertThat(ReadOnlyCommand.class.isAssignableFrom(TypeTextCommand.class)).isFalse();
        assertThat(CommandTargets.of(new TypeTextCommand("x")).isMutating()).isTrue();
    }
}
