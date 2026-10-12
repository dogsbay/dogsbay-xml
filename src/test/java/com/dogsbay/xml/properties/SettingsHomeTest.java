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
package com.dogsbay.xml.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SettingsHomeTest {

    @AfterEach
    void restore() {
        SettingsHome.useDefault();
    }

    @Test
    void defaultsToTheDogsbayFolderUnderTheUserHome() {
        assertThat(SettingsHome.isOverridden()).isFalse();
        assertThat(SettingsHome.root())
                .isEqualTo(System.getProperty("user.home") + File.separator + ".dogsbay" + File.separator);
    }

    @Test
    void endsInASeparatorSoANameCanBeAppendedDirectly() {
        SettingsHome.setRoot("/tmp/one");
        assertThat(SettingsHome.root()).endsWith(File.separator);
        // which is what the callers rely on: they append a name with no separator of their own
        assertThat(SettingsHome.root() + "frameworks").isEqualTo("/tmp/one" + File.separator + "frameworks");
        SettingsHome.setRoot("/tmp/two" + File.separator);
        assertThat(SettingsHome.root()).isEqualTo("/tmp/two" + File.separator);
    }

    @Test
    void answersFromTheOverrideOnEveryCall() {
        SettingsHome.setRoot("/tmp/recording");
        assertThat(SettingsHome.isOverridden()).isTrue();
        assertThat(SettingsHome.root()).isEqualTo("/tmp/recording" + File.separator);
        // the point of the whole class: a later call sees the change, where a constant could not
        SettingsHome.setRoot("/tmp/other");
        assertThat(SettingsHome.root()).isEqualTo("/tmp/other" + File.separator);
    }

    @Test
    void goesBackToTheDefault() {
        SettingsHome.setRoot("/tmp/recording");
        SettingsHome.useDefault();
        assertThat(SettingsHome.isOverridden()).isFalse();
        assertThat(SettingsHome.root()).startsWith(System.getProperty("user.home"));
    }

    @Test
    void refusesNothingInPlaceOfAFolder() {
        assertThatThrownBy(() -> SettingsHome.setRoot(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SettingsHome.setRoot("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(SettingsHome.isOverridden()).isFalse();
    }

    @Test
    void takesAFolderNameExactlyAsGiven() {
        // a trailing space is a legal folder name; trimming it would send settings somewhere other than the folder
        // the caller created and checked
        SettingsHome.setRoot("/tmp/with space ");
        assertThat(SettingsHome.root()).isEqualTo("/tmp/with space " + File.separator);
    }
}
