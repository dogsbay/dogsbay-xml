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

package com.dogsbay.agent.secret;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * What the agent has stored to authenticate with, and how to forget it.
 *
 * <p>
 * Two kinds of credential sit behind one question. A key-based provider keeps
 * an API key in the {@link SecretStore} under its own name; the ChatGPT/Codex
 * provider keeps OAuth tokens somewhere else entirely. Everything else — Ollama
 * — needs nothing. Without this, "is a key stored for gemini?" had no answer
 * inside the editor, and the only way to undo a sign-in was a terminal.
 *
 * <p>
 * Told which providers are which rather than working it out, so it can be
 * tested without a keychain.
 */
public final class AgentCredentials {

	/** What is stored for one provider. */
	public enum State {
		/** The provider authenticates with nothing (Ollama). */
		NOT_NEEDED,
		/** It needs a credential and there isn't one. */
		MISSING,
		/** A key or a sign-in is stored. */
		STORED
	}

	/** The sign-in half, kept behind an interface so tests need no browser. */
	public interface OAuth {
		boolean signedIn();

		void forget() throws Exception;
	}

	/**
	 * The plaintext key a machine with no credential store falls back to, in
	 * {@code ~/.xagent/settings.json}.
	 *
	 * <p>
	 * One flat field, not a per-provider entry, so it answers for whichever
	 * provider is in use. Reading only the keychain meant the settings dialog
	 * reported "No key stored" over a key that was plainly there, and Forget
	 * removed nothing while saying it had.
	 */
	public interface FallbackKey {
		/** The stored key, or empty. */
		java.util.Optional<String> get();

		void clear() throws Exception;
	}

	private final SecretStore secrets;
	private final OAuth oauth;
	private final FallbackKey fallback;
	private final List<String> providers;
	private final List<String> keyBased;
	private final String oauthProvider;

	/**
	 * @param secrets      where API keys live when there is a credential store
	 * @param oauth        the ChatGPT sign-in
	 * @param fallback     the plaintext key used when there is not
	 * @param providers    every selectable provider, in the order to report them
	 * @param keyBased     those authenticating with an API key
	 * @param oauthProvider the one authenticating with a sign-in, or null
	 */
	public AgentCredentials(SecretStore secrets, OAuth oauth, FallbackKey fallback, List<String> providers,
			List<String> keyBased, String oauthProvider) {
		this.secrets = secrets;
		this.oauth = oauth;
		this.fallback = fallback;
		this.providers = List.copyOf(providers);
		this.keyBased = List.copyOf(keyBased);
		this.oauthProvider = oauthProvider;
	}

	/** What is stored for one provider. */
	public State stateOf(String provider) {
		if (provider == null) {
			return State.NOT_NEEDED;
		}
		if (provider.equals(oauthProvider)) {
			return oauth.signedIn() ? State.STORED : State.MISSING;
		}
		if (!keyBased.contains(provider)) {
			return State.NOT_NEEDED;
		}
		return held(secrets.get(provider)) || held(fallback.get()) ? State.STORED : State.MISSING;
	}

	private static boolean held(java.util.Optional<String> key) {
		return key.filter(value -> !value.isBlank()).isPresent();
	}

	/** Every provider with something stored, in the order they were declared. */
	public List<String> stored() {
		List<String> found = new ArrayList<>();
		for (String provider : providers) {
			if (stateOf(provider) == State.STORED) {
				found.add(provider);
			}
		}
		return found;
	}

	/**
	 * Forget what is stored for one provider. Doing this to a provider with
	 * nothing stored is not an error — it is the state the caller wanted.
	 *
	 * <p>
	 * Verified by reading back, and throws when the credential survived. The
	 * keychain layer treats a delete as best-effort and swallows its own
	 * failures, so without this a locked keyring produced "Forgot the API key"
	 * over a key that was still there.
	 */
	public void forget(String provider) throws Exception {
		if (provider == null) {
			return;
		}
		if (provider.equals(oauthProvider)) {
			oauth.forget();
		} else if (keyBased.contains(provider)) {
			secrets.delete(provider);
			// The fallback key too, whichever provider was asked about: it is one
			// field serving all of them, so leaving it would hand it straight to
			// the next provider selected.
			fallback.clear();
		} else {
			return;
		}
		if (stateOf(provider) == State.STORED) {
			throw new IOException("the credential for '" + provider
					+ "' is still in the store — the keychain may be locked or unavailable");
		}
	}

	/** What {@link #forgetAll} managed, and what it did not. */
	public record Forgotten(List<String> gone, List<String> failed) {
		public Forgotten {
			gone = List.copyOf(gone);
			failed = List.copyOf(failed);
		}
	}

	/**
	 * Forget every stored credential. Sessions and transcripts are not touched:
	 * losing a conversation by surprise is worse than signing in again.
	 *
	 * <p>
	 * Every provider is attempted, not only the ones {@link #stored} reports:
	 * an entry the store cannot read back — blank, or behind a locked keyring —
	 * would otherwise be the one thing "forget everything" could never remove.
	 * One failure does not stop the rest, and both lists come back, so the
	 * caller can say what actually happened rather than abandon the report with
	 * the stack.
	 */
	public Forgotten forgetAll() {
		List<String> gone = new ArrayList<>();
		List<String> failed = new ArrayList<>();
		for (String provider : providers) {
			boolean wasStored = stateOf(provider) == State.STORED;
			try {
				forget(provider);
				if (wasStored) {
					gone.add(provider);
				}
			} catch (Exception e) {
				failed.add(provider);
			}
		}
		return new Forgotten(gone, failed);
	}
}
