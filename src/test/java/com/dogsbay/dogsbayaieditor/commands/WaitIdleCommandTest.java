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

import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import com.dogsbay.dogsbayaieditor.commands.results.CommandResult;

/**
 * Waiting for the interface to settle.
 *
 * <p>What the interface is doing is given to the waiting rather than read from the machine running the tests, so each case
 * describes one situation exactly: quiet, never quiet, or not answering at all.
 */
class WaitIdleCommandTest {

    // ── What was asked for ───────────────────────────────────────────

    @Test
    void waitsForAModestQuietPeriodByDefault() {
        WaitIdleCommand cmd = new WaitIdleCommand();
        assertThat(cmd.quiet()).isEqualTo(WaitIdleCommand.DEFAULT_QUIET_MS);
        assertThat(cmd.timeout()).isEqualTo(WaitIdleCommand.DEFAULT_TIMEOUT_MS);
        assertThat(cmd.fault()).isNull();
    }

    @Test
    void takesTheCallersOwnPeriods() {
        WaitIdleCommand cmd = new WaitIdleCommand(50, 2000);
        assertThat(cmd.quiet()).isEqualTo(50);
        assertThat(cmd.timeout()).isEqualTo(2000);
        assertThat(cmd.fault()).isNull();
    }

    @Test
    void zeroQuietIsAllowedBecauseItMeansDrainAndAnswer() {
        assertThat(new WaitIdleCommand(0, 1000).quiet()).isZero();
        assertThat(new WaitIdleCommand(0, 1000).fault()).isNull();
    }

    @Test
    void refusesAQuietPeriodTheLimitCannotContain() {
        // measured before this check existed: these failed every time, reporting a busy interface that was in fact idle,
        // because a quiet period is only proved by two readings that far apart
        assertThat(new WaitIdleCommand(500, 200).fault()).isNotNull();
        assertThat(new WaitIdleCommand(150, 100).fault()).contains("longer of the two");
        assertThat(new WaitIdleCommand(100, 100).fault()).isNotNull();
        assertThat(new WaitIdleCommand(50, 2000).fault()).isNull();
    }

    @Test
    void refusesAQuietPeriodNoLiveWindowCouldEverShow() {
        // the text caret blinks twice a second, so nothing above that is ever observed in a real editor
        assertThat(new WaitIdleCommand(600, 30_000).fault()).contains("caret");
        assertThat(new WaitIdleCommand(WaitIdleCommand.MAX_QUIET_MS, 30_000).fault()).isNull();
    }

    @Test
    void nonsensicalPeriodsFallBackRatherThanWaitingForeverOrNotAtAll() {
        assertThat(new WaitIdleCommand(-1, -1).quiet()).isEqualTo(WaitIdleCommand.DEFAULT_QUIET_MS);
        assertThat(new WaitIdleCommand(-1, -1).timeout()).isEqualTo(WaitIdleCommand.DEFAULT_TIMEOUT_MS);
        assertThat(new WaitIdleCommand(10, 0).timeout()).isEqualTo(WaitIdleCommand.DEFAULT_TIMEOUT_MS);
    }

    @Test
    void willNotBeTalkedIntoWaitingForDays() {
        assertThat(new WaitIdleCommand(50, Integer.MAX_VALUE).timeout()).isEqualTo(WaitIdleCommand.MAX_TIMEOUT_MS);
    }

    @Test
    void waitingChangesNothingAndNeedsNoPermission() {
        // read-only, so outside the write gate and outside --allow-driving: a caller may always ask
        assertThat(CommandTargets.of(new WaitIdleCommand()).isMutating()).isFalse();
        assertThat(ReadOnlyCommand.class.isAssignableFrom(WaitIdleCommand.class)).isTrue();
    }

    // ── The waiting itself ───────────────────────────────────────────

    @Test
    void settlesOnceTheQueueHasBeenQuietForLongEnough() throws Exception {
        long before = System.currentTimeMillis();

        CommandResult result = EditorExecutor.waitForIdle(30, 2000, within -> true, () -> true);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("Settled after");
        // the quiet period is observed rather than assumed: proving it takes two readings that far apart
        assertThat(System.currentTimeMillis() - before).isGreaterThanOrEqualTo(30);
    }

    @Test
    void givesUpWhenTheQueueNeverGoesQuiet() {
        // what a runaway repaint looks like from outside: the interface answers, but there is always more waiting
        assertThatThrownBy(() -> EditorExecutor.waitForIdle(50, 300, within -> true, () -> false))
                .isInstanceOf(CommandException.class)
                .hasMessageContaining("still busy after 300 ms");
    }

    @Test
    void answersWithinTheLimitEvenWhenTheInterfaceNeverAnswers() {
        long before = System.currentTimeMillis();

        // a wedged event thread: the round trip never completes. Waiting on that without a limit is the hang this command
        // exists to turn into an answer, and it was exactly that, because the trip ran before the deadline was read.
        assertThatThrownBy(() -> EditorExecutor.waitForIdle(20, 200, within -> false, () -> true))
                .isInstanceOf(CommandException.class)
                .hasMessageContaining("did not answer");

        assertThat(System.currentTimeMillis() - before).isLessThan(2000);
    }

    @Test
    void neverGivesTheInterfaceMoreTimeThanRemains() {
        List<Long> allowed = new ArrayList<>();

        assertThatThrownBy(() -> EditorExecutor.waitForIdle(20, 60, within -> {
            allowed.add(within);
            return true;
        }, () -> false)).isInstanceOf(CommandException.class);

        // the property that keeps the limit honest however long the interface takes to answer
        assertThat(allowed).isNotEmpty();
        assertThat(allowed).allSatisfy(within -> assertThat(within).isBetween(1L, 60L));
    }

    @Test
    void waitsNoLongerThanItWasTold() {
        long before = System.currentTimeMillis();

        assertThatThrownBy(() -> EditorExecutor.waitForIdle(50, 200, within -> true, () -> false))
                .isInstanceOf(CommandException.class);

        // the command exists to replace a sleep, so it must not become a longer one
        assertThat(System.currentTimeMillis() - before).isLessThan(700);
    }

    @Test
    void settlesAgainstTheWindowsOwnQueue() throws Exception {
        // once against the real thing, so the emptiness check and the round trip are known to fit together
        CommandResult result = EditorExecutor.waitForIdle(20, 4000, within -> {
            try {
                SwingUtilities.invokeAndWait(() -> { });
                return true;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }, () -> Toolkit.getDefaultToolkit().getSystemEventQueue().peekEvent() == null);

        assertThat(result.success()).isTrue();
    }
}
