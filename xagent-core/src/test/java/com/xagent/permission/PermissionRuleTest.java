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
package com.xagent.permission;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionRuleTest {

	@Test
	void parsesBareToolName() {
		var rule = PermissionRule.parse("bash");
		assertThat(rule).isEqualTo(new PermissionRule("bash", null));
	}

	@Test
	void parsesToolWithPattern() {
		var rule = PermissionRule.parse("bash(mvn *)");
		assertThat(rule).isEqualTo(new PermissionRule("bash", "mvn *"));
	}

	@Test
	void parsesTrimsWhitespace() {
		var rule = PermissionRule.parse("  write( docs/* )  ");
		assertThat(rule).isEqualTo(new PermissionRule("write", "docs/*"));
	}

	@Test
	void parseEmptyPatternMeansWholeTool() {
		var rule = PermissionRule.parse("bash()");
		assertThat(rule).isEqualTo(new PermissionRule("bash", null));
	}

	@Test
	void parseRejectsMalformed() {
		assertThat(PermissionRule.parse(null)).isNull();
		assertThat(PermissionRule.parse("")).isNull();
		assertThat(PermissionRule.parse("   ")).isNull();
		assertThat(PermissionRule.parse("(mvn *)")).isNull();
		assertThat(PermissionRule.parse("bash(mvn *")).isNull();
		assertThat(PermissionRule.parse("bash)")).isNull();
	}

	@Test
	void bareToolMatchesAnyArgument() {
		var rule = new PermissionRule("bash", null);
		assertThat(rule.matches("bash", "rm -rf /")).isTrue();
		assertThat(rule.matches("bash", null)).isTrue();
		assertThat(rule.matches("write", "anything")).isFalse();
	}

	@Test
	void globMatchesPrefix() {
		var rule = PermissionRule.parse("bash(mvn *)");
		assertThat(rule.matches("bash", "mvn test")).isTrue();
		assertThat(rule.matches("bash", "mvn clean install")).isTrue();
		assertThat(rule.matches("bash", "mvn")).isFalse();
		assertThat(rule.matches("bash", "gradle build")).isFalse();
	}

	@Test
	void globSpecialCharactersAreLiteral() {
		var rule = PermissionRule.parse("write(docs/*.md)");
		assertThat(rule.matches("write", "docs/readme.md")).isTrue();
		assertThat(rule.matches("write", "docs/sub/readme.md")).isTrue();
		assertThat(rule.matches("write", "docsXreadme.md")).isFalse();
	}

	@Test
	void questionMarkMatchesSingleCharacter() {
		var rule = PermissionRule.parse("bash(ls ?)");
		assertThat(rule.matches("bash", "ls a")).isTrue();
		assertThat(rule.matches("bash", "ls ab")).isFalse();
	}

	@Test
	void patternWithNoArgDoesNotMatch() {
		var rule = PermissionRule.parse("bash(mvn *)");
		assertThat(rule.matches("bash", null)).isFalse();
	}

	@Test
	void toTextRoundTrips() {
		assertThat(PermissionRule.parse("bash(mvn *)").toText()).isEqualTo("bash(mvn *)");
		assertThat(PermissionRule.parse("edit").toText()).isEqualTo("edit");
	}
}
