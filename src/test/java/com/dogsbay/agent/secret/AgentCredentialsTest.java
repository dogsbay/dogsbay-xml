package com.dogsbay.agent.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What is stored to authenticate with, and what forgetting it does. */
class AgentCredentialsTest {

	private static final List<String> PROVIDERS =
			List.of("anthropic", "openai", "gemini", "ollama", "openai-codex");
	private static final List<String> KEY_BASED = List.of("openai", "anthropic", "gemini");

	/** An in-memory keychain. */
	private static final class Keys implements SecretStore {
		private final Map<String, String> held = new HashMap<>();

		@Override
		public Optional<String> get(String account) {
			return Optional.ofNullable(held.get(account));
		}

		@Override
		public void set(String account, String secret) {
			held.put(account, secret);
		}

		@Override
		public void delete(String account) {
			held.remove(account);
		}
	}

	/** A sign-in that needs no browser. */
	private static final class SignIn implements AgentCredentials.OAuth {
		private boolean in;

		@Override
		public boolean signedIn() {
			return in;
		}

		@Override
		public void forget() {
			in = false;
		}
	}

	/** The plaintext key a machine with no credential store falls back to. */
	private static final class Fallback implements AgentCredentials.FallbackKey {
		private String key;

		@Override
		public Optional<String> get() {
			return Optional.ofNullable(key);
		}

		@Override
		public void clear() {
			key = null;
		}
	}

	private final Keys keys = new Keys();
	private final SignIn signIn = new SignIn();
	private final Fallback fallback = new Fallback();
	private final AgentCredentials credentials =
			new AgentCredentials(keys, signIn, fallback, PROVIDERS, KEY_BASED, "openai-codex");

	@Test
	@DisplayName("a provider needing no credential is never reported as missing one")
	void ollamaNeedsNothing() {
		assertThat(credentials.stateOf("ollama")).isEqualTo(AgentCredentials.State.NOT_NEEDED);
	}

	@Test
	@DisplayName("a stored key is visible, an absent one is not")
	void keysAreReported() {
		assertThat(credentials.stateOf("gemini")).isEqualTo(AgentCredentials.State.MISSING);

		keys.set("gemini", "AIza-secret");

		assertThat(credentials.stateOf("gemini")).isEqualTo(AgentCredentials.State.STORED);
	}

	@Test
	@DisplayName("a blank key counts as no key")
	void blankIsNotAKey() {
		keys.set("openai", "   ");

		assertThat(credentials.stateOf("openai")).isEqualTo(AgentCredentials.State.MISSING);
	}

	@Test
	@DisplayName("the ChatGPT provider reports its sign-in, not a key")
	void codexReportsItsSignIn() {
		assertThat(credentials.stateOf("openai-codex")).isEqualTo(AgentCredentials.State.MISSING);

		signIn.in = true;

		assertThat(credentials.stateOf("openai-codex")).isEqualTo(AgentCredentials.State.STORED);
	}

	@Test
	@DisplayName("forgetting a key removes it and leaves the others alone")
	void forgettingOneLeavesTheRest() throws Exception {
		keys.set("gemini", "AIza-secret");
		keys.set("openai", "sk-secret");

		credentials.forget("gemini");

		assertThat(credentials.stateOf("gemini")).isEqualTo(AgentCredentials.State.MISSING);
		assertThat(credentials.stateOf("openai")).isEqualTo(AgentCredentials.State.STORED);
	}

	@Test
	@DisplayName("forgetting the ChatGPT provider signs out")
	void forgettingCodexSignsOut() throws Exception {
		signIn.in = true;

		credentials.forget("openai-codex");

		assertThat(signIn.signedIn()).isFalse();
	}

	@Test
	@DisplayName("forgetting a provider with nothing stored is not an error")
	void forgettingNothingIsFine() throws Exception {
		credentials.forget("gemini");
		credentials.forget("ollama");
		credentials.forget(null);

		assertThat(credentials.stored()).isEmpty();
	}

	@Test
	@DisplayName("everything stored is listed in the declared order")
	void storedIsListedInOrder() {
		keys.set("gemini", "AIza-secret");
		keys.set("anthropic", "sk-ant-secret");
		signIn.in = true;

		assertThat(credentials.stored()).containsExactly("anthropic", "gemini", "openai-codex");
	}

	@Test
	@DisplayName("forgetting all clears keys and the sign-in, and reports what went")
	void forgetAllClearsEverything() {
		keys.set("gemini", "AIza-secret");
		signIn.in = true;

		AgentCredentials.Forgotten result = credentials.forgetAll();

		assertThat(result.gone()).containsExactly("gemini", "openai-codex");
		assertThat(result.failed()).isEmpty();
		assertThat(credentials.stored()).isEmpty();
		assertThat(signIn.signedIn()).isFalse();
	}

	@Test
	@DisplayName("forgetting all when nothing is stored reports nothing")
	void forgetAllOnACleanMachine() {
		AgentCredentials.Forgotten result = credentials.forgetAll();

		assertThat(result.gone()).isEmpty();
		assertThat(result.failed()).isEmpty();
	}

	@Test
	@DisplayName("a store that keeps the key anyway is reported as a failure, not a success")
	void aDeleteThatDidNotTakeIsNotSuccess() {
		// A locked keyring: the keychain layer swallows its own delete failure,
		// so the only way to know is to read back.
		SecretStore stubborn = new SecretStore() {
			@Override public Optional<String> get(String account) { return Optional.of("still-here"); }
			@Override public void set(String account, String secret) { }
			@Override public void delete(String account) { }
		};
		AgentCredentials credentials = new AgentCredentials(stubborn, signIn, new Fallback(), PROVIDERS,
				KEY_BASED, "openai-codex");

		assertThatThrownBy(() -> credentials.forget("gemini"))
				.hasMessageContaining("still in the store");
	}

	@Test
	@DisplayName("one provider failing does not stop the others")
	void oneFailureDoesNotStopTheRest() {
		// gemini cannot be removed; anthropic can.
		SecretStore halfBroken = new SecretStore() {
			@Override public Optional<String> get(String account) {
				return "gemini".equals(account) ? Optional.of("stuck") : keys.get(account);
			}
			@Override public void set(String account, String secret) { keys.set(account, secret); }
			@Override public void delete(String account) { keys.delete(account); }
		};
		keys.set("anthropic", "sk-ant-secret");
		AgentCredentials credentials = new AgentCredentials(halfBroken, signIn, new Fallback(), PROVIDERS,
				KEY_BASED, "openai-codex");

		AgentCredentials.Forgotten result = credentials.forgetAll();

		assertThat(result.gone()).containsExactly("anthropic");
		assertThat(result.failed()).containsExactly("gemini");
	}

	@Test
	@DisplayName("an entry too broken to read back is still attempted")
	void anUnreadableEntryIsStillDeleted() {
		// A blank value reads as "nothing stored", so stored() never names it —
		// and forgetting only what stored() names would leave it there for good.
		keys.set("openai", "   ");

		credentials.forgetAll();

		assertThat(keys.get("openai")).isEmpty();
	}

	// ── the machine with no credential store ────────────────────────────

	@Test
	@DisplayName("a key in the plaintext fallback counts as stored")
	void theFallbackKeyIsSeen() {
		assertThat(credentials.stateOf("gemini")).isEqualTo(AgentCredentials.State.MISSING);

		fallback.key = "sk-in-a-file";

		// Reading only the keychain reported "No key stored" over a key that was
		// plainly there, and greyed out the button that would have removed it.
		assertThat(credentials.stateOf("gemini")).isEqualTo(AgentCredentials.State.STORED);
		assertThat(credentials.stored()).contains("gemini");
	}

	@Test
	@DisplayName("forgetting removes the plaintext key, not just the keychain entry")
	void forgettingClearsTheFallback() throws Exception {
		fallback.key = "sk-in-a-file";

		credentials.forget("gemini");

		assertThat(fallback.get()).isEmpty();
		assertThat(credentials.stateOf("gemini")).isEqualTo(AgentCredentials.State.MISSING);
	}

	@Test
	@DisplayName("the plaintext key goes whichever provider was asked about")
	void theFallbackIsNotPerProvider() throws Exception {
		fallback.key = "sk-in-a-file";

		credentials.forget("anthropic");

		// It is one field serving every provider, so leaving it would hand it
		// straight to the next one selected.
		assertThat(credentials.stateOf("gemini")).isEqualTo(AgentCredentials.State.MISSING);
	}

	@Test
	@DisplayName("forgetting everything clears the plaintext key too")
	void forgetAllClearsTheFallback() {
		keys.set("gemini", "AIza-secret");
		fallback.key = "sk-in-a-file";
		signIn.in = true;

		AgentCredentials.Forgotten result = credentials.forgetAll();

		assertThat(fallback.get()).isEmpty();
		assertThat(result.failed()).isEmpty();
		assertThat(credentials.stored()).isEmpty();
	}

	@Test
	@DisplayName("a provider needing no key is unaffected by the fallback")
	void ollamaIgnoresTheFallback() {
		fallback.key = "sk-in-a-file";

		assertThat(credentials.stateOf("ollama")).isEqualTo(AgentCredentials.State.NOT_NEEDED);
	}

	@Test
	@DisplayName("a fallback that cannot be cleared is reported, not claimed as success")
	void aStubbornFallbackIsAFailure() {
		AgentCredentials.FallbackKey stuck = new AgentCredentials.FallbackKey() {
			@Override public Optional<String> get() { return Optional.of("sk-stuck"); }
			@Override public void clear() { }
		};
		AgentCredentials credentials = new AgentCredentials(keys, signIn, stuck, PROVIDERS, KEY_BASED,
				"openai-codex");

		assertThatThrownBy(() -> credentials.forget("gemini")).hasMessageContaining("still in the store");
	}
}
