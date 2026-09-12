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
package com.xagent.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Redacts likely secrets from text before it enters the conversation history
 * (and therefore the model prompt and persisted session files). Conservative
 * by design: it targets exact values of known credential env vars plus a small
 * set of high-confidence key shapes, so it rarely touches legitimate content.
 */
public final class SecretMasker {

	public static final String REDACTED = "***REDACTED***";

	/** Env vars whose exact values are redacted wherever they appear. */
	private static final String[] ENV_SECRET_VARS = {
		"OPENAI_API_KEY", "ANTHROPIC_API_KEY", "GEMINI_API_KEY"
	};

	/** High-confidence credential shapes. */
	private static final List<Pattern> PATTERNS = List.of(
		Pattern.compile("sk-ant-[A-Za-z0-9_-]{20,}"),
		Pattern.compile("sk-[A-Za-z0-9_-]{20,}"),          // OpenAI-style
		Pattern.compile("AIza[A-Za-z0-9_-]{20,}"),         // Google / Gemini
		Pattern.compile("gh[pousr]_[A-Za-z0-9]{20,}"),     // GitHub tokens
		Pattern.compile("AKIA[A-Z0-9]{16}"),               // AWS access key id
		Pattern.compile("xox[baprs]-[A-Za-z0-9-]{10,}"),   // Slack tokens
		Pattern.compile("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"), // JWT
		Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----")
	);

	private SecretMasker() {}

	/** Mask secrets in {@code text}, using credential env-var values + key shapes. */
	public static String mask(String text) {
		return mask(text, envSecretValues());
	}

	/**
	 * Mask secrets in {@code text}. {@code exactSecrets} are redacted by exact
	 * substring match (used for env-var values); key-shape patterns always apply.
	 * Exposed for tests so env state needn't be manipulated.
	 */
	static String mask(String text, Collection<String> exactSecrets) {
		if (text == null || text.isEmpty()) {
			return text;
		}
		String result = text;
		for (String secret : exactSecrets) {
			if (secret != null && secret.length() >= 8 && result.contains(secret)) {
				result = result.replace(secret, REDACTED);
			}
		}
		for (Pattern pattern : PATTERNS) {
			result = pattern.matcher(result).replaceAll(REDACTED);
		}
		return result;
	}

	private static Collection<String> envSecretValues() {
		var values = new ArrayList<String>();
		for (String var : ENV_SECRET_VARS) {
			String value = System.getenv(var);
			if (value != null && !value.isBlank()) {
				values.add(value);
			}
		}
		return values;
	}
}
