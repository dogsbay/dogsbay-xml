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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionPolicyTest {

	@Test
	void asksByDefault() {
		var policy = new PermissionPolicy();
		assertThat(policy.check("bash", "ls")).isEqualTo(PermissionPolicy.Verdict.ASK);
	}

	@Test
	void allowRuleAllows() {
		var policy = new PermissionPolicy(List.of(PermissionRule.parse("bash(mvn *)")), List.of());
		assertThat(policy.check("bash", "mvn test")).isEqualTo(PermissionPolicy.Verdict.ALLOW);
		assertThat(policy.check("bash", "rm -rf /")).isEqualTo(PermissionPolicy.Verdict.ASK);
	}

	@Test
	void denyBeatsAllow() {
		var policy = new PermissionPolicy(
			List.of(PermissionRule.parse("bash")),
			List.of(PermissionRule.parse("bash(rm *)"))
		);
		assertThat(policy.check("bash", "ls")).isEqualTo(PermissionPolicy.Verdict.ALLOW);
		assertThat(policy.check("bash", "rm -rf /")).isEqualTo(PermissionPolicy.Verdict.DENY);
	}

	@Test
	void sessionGrantAllows() {
		var policy = new PermissionPolicy();
		policy.grantSession(PermissionRule.parse("write"));
		assertThat(policy.check("write", "/tmp/foo")).isEqualTo(PermissionPolicy.Verdict.ALLOW);
		assertThat(policy.check("edit", "/tmp/foo")).isEqualTo(PermissionPolicy.Verdict.ASK);
	}

	@Test
	void denyBeatsSessionGrant() {
		var policy = new PermissionPolicy(List.of(), List.of(PermissionRule.parse("bash(rm *)")));
		policy.grantSession(PermissionRule.parse("bash"));
		assertThat(policy.check("bash", "rm -rf /")).isEqualTo(PermissionPolicy.Verdict.DENY);
	}

	@Test
	void grantRulesForBashAreProgramScoped() {
		var rules = PermissionPolicy.grantRulesFor("bash", "git status --short");
		assertThat(rules).containsExactly(
			new PermissionRule("bash", "git"),
			new PermissionRule("bash", "git *")
		);
		// the granted rules cover both the bare program and program-with-args
		var policy = new PermissionPolicy();
		rules.forEach(policy::grantSession);
		assertThat(policy.check("bash", "git log")).isEqualTo(PermissionPolicy.Verdict.ALLOW);
		assertThat(policy.check("bash", "git")).isEqualTo(PermissionPolicy.Verdict.ALLOW);
		assertThat(policy.check("bash", "gitfoo")).isEqualTo(PermissionPolicy.Verdict.ASK);
		assertThat(policy.check("bash", "rm -rf /")).isEqualTo(PermissionPolicy.Verdict.ASK);
	}

	@Test
	void grantRulesForOtherToolsAreToolScoped() {
		var rules = PermissionPolicy.grantRulesFor("write", "/tmp/foo.txt");
		assertThat(rules).containsExactly(new PermissionRule("write", null));
	}

	@Test
	void grantRulesForBashWithoutCommandFallsBackToToolScope() {
		assertThat(PermissionPolicy.grantRulesFor("bash", "  "))
			.containsExactly(new PermissionRule("bash", null));
	}
}
