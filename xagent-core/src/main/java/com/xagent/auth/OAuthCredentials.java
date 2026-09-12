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
package com.xagent.auth;

/**
 * Stored OAuth credentials for a provider.
 *
 * @param accessToken  bearer token for API requests
 * @param refreshToken rotating refresh token (replaced on every refresh)
 * @param expiresAtMs  epoch millis when the access token expires
 * @param accountId    provider account id required in request headers
 *                     (ChatGPT-Account-Id for openai-codex), may be null
 */
public record OAuthCredentials(
	String accessToken,
	String refreshToken,
	long expiresAtMs,
	String accountId
) {
	/** True when the access token is expired or about to expire. */
	public boolean isExpired(long nowMs, long safetyWindowMs) {
		return nowMs >= expiresAtMs - safetyWindowMs;
	}
}
