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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Evaluates tool calls against deny rules, allow rules, and session grants.
 * Precedence: deny &gt; allow (configured or session-granted) &gt; ask.
 */
public class PermissionPolicy {

	/** Outcome of a policy check. */
	public enum Verdict { ALLOW, DENY, ASK }

	private final List<PermissionRule> allowRules;
	private final List<PermissionRule> denyRules;
	private final List<PermissionRule> sessionGrants = new CopyOnWriteArrayList<>();

	public PermissionPolicy(List<PermissionRule> allowRules, List<PermissionRule> denyRules) {
		this.allowRules = new CopyOnWriteArrayList<>(allowRules);
		this.denyRules = new CopyOnWriteArrayList<>(denyRules);
	}

	public PermissionPolicy() {
		this(List.of(), List.of());
	}

	public Verdict check(String toolName, String primaryArg) {
		for (var rule : denyRules) {
			if (rule.matches(toolName, primaryArg)) return Verdict.DENY;
		}
		for (var rule : allowRules) {
			if (rule.matches(toolName, primaryArg)) return Verdict.ALLOW;
		}
		for (var rule : sessionGrants) {
			if (rule.matches(toolName, primaryArg)) return Verdict.ALLOW;
		}
		return Verdict.ASK;
	}

	/** Add a session-scoped grant (not persisted). */
	public void grantSession(PermissionRule rule) {
		sessionGrants.add(rule);
	}

	public List<PermissionRule> allowRules() {
		return List.copyOf(allowRules);
	}

	public List<PermissionRule> denyRules() {
		return List.copyOf(denyRules);
	}

	public List<PermissionRule> sessionGrants() {
		return List.copyOf(sessionGrants);
	}

	/** Add a configured allow rule (caller is responsible for persistence). */
	public void addAllowRule(PermissionRule rule) {
		allowRules.add(rule);
	}

	/**
	 * The rules a session/always approval should grant for this call.
	 * For bash, grants are program-scoped (the command's first token),
	 * not all of bash; other tools grant at tool level.
	 */
	public static List<PermissionRule> grantRulesFor(String toolName, String primaryArg) {
		if ("bash".equals(toolName) && primaryArg != null && !primaryArg.isBlank()) {
			String firstToken = primaryArg.strip().split("\\s+", 2)[0];
			var rules = new ArrayList<PermissionRule>();
			rules.add(new PermissionRule(toolName, firstToken));
			rules.add(new PermissionRule(toolName, firstToken + " *"));
			return rules;
		}
		return List.of(new PermissionRule(toolName, null));
	}
}
