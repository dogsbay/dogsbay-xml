package com.dogsbay.agent.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dogsbay.agent.ProviderChoice;
import com.dogsbay.agent.secret.AgentCredentials;

/**
 * The settings dialog has to answer "is a key stored for this provider?" — the
 * key field is blank either way — and offer the way to undo it, which until now
 * only a terminal could do.
 */
class AgentCredentialSettingsTest {

    /** An Actions that reports a credential state the test controls. */
    private static final class Host implements AgentChatPanel.Actions {
        private AgentCredentials.State state = AgentCredentials.State.MISSING;
        private List<String> stored = List.of();
        private final List<String> forgotten = new ArrayList<>();

        @Override public void submit(String text) { }
        @Override public void chooseProvider(ProviderChoice choice) { }
        @Override public void login() { }
        @Override public void command(String raw) { }
        @Override public void sessions() { }
        @Override public void abort() { }

        @Override
        public AgentCredentials.State credential(String provider) {
            return state;
        }

        @Override
        public List<String> storedCredentials() {
            return stored;
        }

        @Override
        public void forgetCredential(String provider) {
            forgotten.add(provider);
            state = AgentCredentials.State.MISSING;
            stored = List.of();
        }
    }

    private static AgentChatPanel panel(Host host, String provider) {
        return new AgentChatPanel(List.of(provider), host);
    }

    @Test
    @DisplayName("a stored key is named, and can be forgotten from here")
    void aStoredKeyIsNamedAndForgettable() throws Exception {
        Host host = new Host();
        host.state = AgentCredentials.State.STORED;
        host.stored = List.of("gemini");
        var text = new AtomicReference<String>();

        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel panel = panel(host, "gemini");
            panel.setConfirmer((message, title) -> true);   // "yes" without a dialog
            Container form = panel.buildSettingsContent();
            text.set(labels(form));

            JButton forget = button(form, "Forget key");
            assertThat(forget.isEnabled()).isTrue();
            forget.doClick();

            text.set(labels(form));
        });

        // After the click the form says so itself, rather than waiting for a reopen.
        assertThat(host.forgotten).containsExactly("gemini");
        assertThat(text.get()).contains("No key stored for gemini.");
    }

    @Test
    @DisplayName("nothing stored means nothing to forget")
    void nothingStoredDisablesTheButtons() throws Exception {
        Host host = new Host();
        var shown = new AtomicReference<String>();
        var enabled = new AtomicReference<Boolean>();
        var allEnabled = new AtomicReference<Boolean>();

        SwingUtilities.invokeAndWait(() -> {
            Container form = panel(host, "gemini").buildSettingsContent();
            shown.set(labels(form));
            enabled.set(button(form, "Forget key").isEnabled());
            allEnabled.set(button(form, "Forget all sign-ins…").isEnabled());
        });

        assertThat(shown.get()).contains("No key stored for gemini.");
        assertThat(enabled.get()).isFalse();
        assertThat(allEnabled.get()).isFalse();
    }

    @Test
    @DisplayName("the ChatGPT provider signs out rather than forgetting a key")
    void codexSignsOut() throws Exception {
        Host host = new Host();
        host.state = AgentCredentials.State.STORED;
        host.stored = List.of("openai-codex");
        var shown = new AtomicReference<String>();
        var hasKeyButton = new AtomicReference<Boolean>();

        SwingUtilities.invokeAndWait(() -> {
            Container form = panel(host, "openai-codex").buildSettingsContent();
            shown.set(labels(form));
            hasKeyButton.set(find(form, "Forget key") != null);
            assertThat(button(form, "Sign out").isEnabled()).isTrue();
        });

        assertThat(shown.get()).contains("Signed in to ChatGPT.");
        assertThat(hasKeyButton.get()).isFalse();
    }

    @Test
    @DisplayName("a provider that needs no credential offers no way to forget one")
    void ollamaOffersNothingToForget() throws Exception {
        Host host = new Host();
        host.state = AgentCredentials.State.NOT_NEEDED;
        var shown = new AtomicReference<String>();
        var visible = new AtomicReference<Boolean>();

        SwingUtilities.invokeAndWait(() -> {
            Container form = panel(host, "ollama").buildSettingsContent();
            shown.set(labels(form));
            visible.set(find(form, "Forget key").isVisible());
        });

        assertThat(shown.get()).contains("ollama needs no key.");
        assertThat(visible.get()).isFalse();
    }

    @Test
    @DisplayName("something stored anywhere enables forget-all")
    void forgetAllFollowsWhatIsStored() throws Exception {
        Host host = new Host();
        host.stored = List.of("gemini", "openai-codex");
        var allEnabled = new AtomicReference<Boolean>();

        SwingUtilities.invokeAndWait(() -> {
            Container form = panel(host, "gemini").buildSettingsContent();
            allEnabled.set(button(form, "Forget all sign-ins…").isEnabled());
        });

        assertThat(allEnabled.get()).isTrue();
    }

    @Test
    @DisplayName("a provider that signs in shows no API key box")
    void signInProviderHidesTheKeyBox() throws Exception {
        Host host = new Host();
        host.state = AgentCredentials.State.STORED;
        host.stored = List.of("openai-codex");
        var keyBoxShown = new AtomicReference<Boolean>();
        var signInShown = new AtomicReference<Boolean>();

        SwingUtilities.invokeAndWait(() -> {
            Container form = panel(host, "openai-codex").buildSettingsContent();
            keyBoxShown.set(showing(form, JPasswordField.class));
            signInShown.set(find(form, "Sign in to ChatGPT").isVisible());
        });

        // An API key box above a ChatGPT sign-in asks you to ignore one of them.
        assertThat(keyBoxShown.get()).isFalse();
        // Already signed in: the offer to sign in again is noise.
        assertThat(signInShown.get()).isFalse();
    }

    @Test
    @DisplayName("a key-based provider shows the key box and no sign-in button")
    void keyProviderShowsTheKeyBox() throws Exception {
        Host host = new Host();
        var keyBoxShown = new AtomicReference<Boolean>();
        var signInShown = new AtomicReference<Boolean>();

        SwingUtilities.invokeAndWait(() -> {
            Container form = panel(host, "gemini").buildSettingsContent();
            keyBoxShown.set(showing(form, JPasswordField.class));
            signInShown.set(find(form, "Sign in to ChatGPT").isVisible());
        });

        assertThat(keyBoxShown.get()).isTrue();
        assertThat(signInShown.get()).isFalse();
    }

    @Test
    @DisplayName("a provider needing nothing shows neither a key box nor a sign-in")
    void ollamaShowsNeither() throws Exception {
        Host host = new Host();
        host.state = AgentCredentials.State.NOT_NEEDED;
        var keyBoxShown = new AtomicReference<Boolean>();
        var signInShown = new AtomicReference<Boolean>();

        SwingUtilities.invokeAndWait(() -> {
            Container form = panel(host, "ollama").buildSettingsContent();
            keyBoxShown.set(showing(form, JPasswordField.class));
            signInShown.set(find(form, "Sign in to ChatGPT").isVisible());
        });

        assertThat(keyBoxShown.get()).isFalse();
        assertThat(signInShown.get()).isFalse();
    }

    @Test
    @DisplayName("not being signed in offers the sign-in")
    void signedOutOffersTheSignIn() throws Exception {
        Host host = new Host();
        host.state = AgentCredentials.State.MISSING;
        var signInShown = new AtomicReference<Boolean>();
        var forgetEnabled = new AtomicReference<Boolean>();

        SwingUtilities.invokeAndWait(() -> {
            Container form = panel(host, "openai-codex").buildSettingsContent();
            signInShown.set(find(form, "Sign in to ChatGPT").isVisible());
            forgetEnabled.set(button(form, "Sign out").isEnabled());
        });

        assertThat(signInShown.get()).isTrue();
        assertThat(forgetEnabled.get()).isFalse();
    }

    /** True if a component of this type is present and visible all the way up to the form. */
    private static boolean showing(Container root, Class<?> type) {
        Component found = findByType(root, type);
        return found != null && visibleUpTo(found, root);
    }

    private static Component findByType(Container from, Class<?> type) {
        for (Component c : from.getComponents()) {
            if (type.isInstance(c)) {
                return c;
            }
            if (c instanceof Container inner) {
                Component deeper = findByType(inner, type);
                if (deeper != null) {
                    return deeper;
                }
            }
        }
        return null;
    }

    /** Hiding a row hides what is in it, so every ancestor up to the form counts. */
    private static boolean visibleUpTo(Component c, Container root) {
        for (Component walk = c; walk != null; walk = walk.getParent()) {
            if (!walk.isVisible()) {
                return false;
            }
            if (walk == root) {
                return true;
            }
        }
        return true;
    }

    @Test
    @DisplayName("forgetting a key is confirmed first, and cancelling forgets nothing")
    void forgettingIsAskedAboutFirst() throws Exception {
        Host host = new Host();
        host.state = AgentCredentials.State.STORED;
        host.stored = List.of("gemini");
        var asked = new AtomicReference<String>();

        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel panel = panel(host, "gemini");
            panel.setConfirmer((message, title) -> {
                asked.set(message);
                return false;   // cancelled
            });
            Container form = panel.buildSettingsContent();
            button(form, "Forget key").doClick();
        });

        // One click, no undo: it has to ask, and Cancel has to mean it.
        assertThat(asked.get()).contains("Forget the stored API key for gemini");
        assertThat(asked.get()).contains("Sessions are kept");
        assertThat(host.forgotten).isEmpty();
    }

    @Test
    @DisplayName("signing out is confirmed in its own words")
    void signingOutSaysWhatItIs() throws Exception {
        Host host = new Host();
        host.state = AgentCredentials.State.STORED;
        host.stored = List.of("openai-codex");
        var asked = new AtomicReference<String>();

        SwingUtilities.invokeAndWait(() -> {
            AgentChatPanel panel = panel(host, "openai-codex");
            panel.setConfirmer((message, title) -> {
                asked.set(message);
                return true;
            });
            Container form = panel.buildSettingsContent();
            button(form, "Sign out").doClick();
        });

        assertThat(asked.get()).contains("Sign out of ChatGPT");
        assertThat(host.forgotten).containsExactly("openai-codex");
    }

    @Test
    @DisplayName("the panel's idea of the sign-in provider matches the real one")
    void theSignInProviderIdMatchesCodex() {
        // The ui package does not depend on runtime, so the id is spelled out
        // here as well as there. A divergence would silently break the sign-in
        // button's visibility and the Sign out / Forget key label, with no
        // compile error to catch it.
        assertThat(com.xagent.auth.CodexOAuth.PROVIDER_ID).isEqualTo("openai-codex");
    }

    private static String labels(Container from) {
        StringBuilder text = new StringBuilder();
        collectLabels(from, text);
        return text.toString();
    }

    private static void collectLabels(Container from, StringBuilder into) {
        for (Component c : from.getComponents()) {
            if (c instanceof JLabel label && label.getText() != null) {
                into.append(label.getText()).append('\n');
            }
            if (c instanceof Container inner) {
                collectLabels(inner, into);
            }
        }
    }

    private static JButton button(Container from, String text) {
        JButton found = find(from, text);
        assertThat(found).as("a button labelled '" + text + "'").isNotNull();
        return found;
    }

    private static JButton find(Container from, String text) {
        for (Component c : from.getComponents()) {
            if (c instanceof JButton button && text.equals(button.getText())) {
                return button;
            }
            if (c instanceof Container inner) {
                JButton deeper = find(inner, text);
                if (deeper != null) {
                    return deeper;
                }
            }
        }
        return null;
    }
}
