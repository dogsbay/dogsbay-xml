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

import java.util.regex.Pattern;

/**
 * A permission rule: a tool name with an optional glob over the tool's
 * primary argument. Text forms: {@code bash}, {@code bash(mvn *)},
 * {@code write(docs/*)}.
 *
 * @param toolName the tool this rule applies to
 * @param pattern  glob over the primary argument ({@code *} and {@code ?}),
 *                 or null to match every call of the tool
 */
public record PermissionRule(String toolName, String pattern) {

	/**
	 * Parse a rule from its text form. Returns null for malformed input
	 * (empty, unbalanced parentheses, empty tool name).
	 */
	public static PermissionRule parse(String text) {
		if (text == null) return null;
		String trimmed = text.trim();
		if (trimmed.isEmpty()) return null;

		int open = trimmed.indexOf('(');
		if (open < 0) {
			if (trimmed.contains(")")) return null;
			return new PermissionRule(trimmed, null);
		}
		if (!trimmed.endsWith(")") || open == 0) return null;
		String tool = trimmed.substring(0, open).trim();
		String glob = trimmed.substring(open + 1, trimmed.length() - 1).trim();
		if (tool.isEmpty()) return null;
		return new PermissionRule(tool, glob.isEmpty() ? null : glob);
	}

	/**
	 * True if this rule applies to the given tool call.
	 */
	public boolean matches(String tool, String primaryArg) {
		if (!toolName.equals(tool)) return false;
		if (pattern == null) return true;
		if (primaryArg == null) return false;
		return globToRegex(pattern).matcher(primaryArg).matches();
	}

	/** Text form, parseable by {@link #parse}. */
	public String toText() {
		return pattern == null ? toolName : toolName + "(" + pattern + ")";
	}

	private static Pattern globToRegex(String glob) {
		StringBuilder regex = new StringBuilder();
		for (int i = 0; i < glob.length(); i++) {
			char c = glob.charAt(i);
			switch (c) {
				case '*' -> regex.append(".*");
				case '?' -> regex.append('.');
				default -> regex.append(Pattern.quote(String.valueOf(c)));
			}
		}
		return Pattern.compile(regex.toString(), Pattern.DOTALL);
	}
}
