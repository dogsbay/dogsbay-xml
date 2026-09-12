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

package com.dogsbay.agent.runtime;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.dogsbay.agent.AgentHost;
import com.dogsbay.agent.secret.KeychainAuthBackend;
import com.dogsbay.agent.secret.AgentCredentials;
import com.dogsbay.agent.secret.SecretStore;
import com.xagent.auth.AuthStorage;
import com.xagent.auth.CodexOAuth;
import com.xagent.core.Agent;
import com.xagent.permission.PermissionHandler;
import com.xagent.permission.PermissionPolicy;
import com.xagent.permission.PermissionRule;
import com.xagent.permission.PermissionSettings;
import com.xagent.permission.PermissionToolHooks;
import com.xagent.core.ToolHooks;
import com.xagent.prompt.SystemPromptBuilder;
import com.xagent.provider.ProviderConfig;
import com.xagent.provider.ProviderFactory;
import com.xagent.skill.BundledSkills;
import com.xagent.skill.Skill;
import com.xagent.skill.SkillLoader;
import com.xagent.tool.ToolRegistry;

import dev.langchain4j.model.chat.StreamingChatModel;

/**
 * Builds an {@link Agent} from an {@link AgentHost}, mirroring the xagent CLI's
 * <em>lazy</em> provider startup: the agent is constructed without contacting
 * any provider, and the model is resolved on the first prompt from the agent's
 * <em>current</em> config. This keeps the UI responsive with no credentials —
 * the user can pick a provider, paste a key, or switch to local Ollama first —
 * instead of erroring out at startup.
 */
public final class ChatAgentFactory {

    private ChatAgentFactory() {}

    /** Selectable providers (mirrors the CLI's {@code --provider} choices). */
    public static final List<String> PROVIDERS =
            List.of("anthropic", "openai", "gemini", "ollama", "openai-codex");

    /** System property pointing at an extra skills directory (override/add). */
    public static final String SKILLS_DIR_PROPERTY = "dogsbay.agent.skillsDir";
    private static final String BUNDLE_CACHE_KEY = "dogsbay";

    /**
     * Load skills the agent should know about: the bundled DITA skills (lowest
     * precedence, shipped in xagent-core), then user/project skills from the
     * standard locations ({@code .xagent/skills}, {@code ~/.xagent/skills}, …),
     * plus an optional override directory ({@code -Ddogsbay.agent.skillsDir}).
     * User/project skills override bundled ones of the same name.
     */
    public static List<Skill> loadSkills(AgentHost host) {
        SkillLoader loader = new SkillLoader();
        List<Path> extra = new ArrayList<>();
        try {
            Path bundled = BundledSkills.extract(BundledSkills.defaultCacheRoot(), BUNDLE_CACHE_KEY);
            if (bundled != null) {
                extra.add(bundled);
            }
        } catch (IOException ignore) {
            // no bundled skills available → continue with user/project only
        }
        String override = System.getProperty(SKILLS_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            extra.add(Path.of(override));
        }
        return loader.loadAll(host.workingDirectory(), extra);
    }

    /**
     * Build an agent. Always succeeds — credentials are resolved lazily on the
     * first prompt, so missing keys never break construction.
     */
    public static Agent create(AgentHost host) {
        return create(host, loadSkills(host));
    }

    /** Build an agent with a pre-loaded skill list (so callers can reuse it). */
    /** True for providers that authenticate with an API key (not Ollama/Codex). */
    public static boolean isKeyBased(String provider) {
        return KEY_BASED_PROVIDERS.contains(provider);
    }

    /**
     * Resolve the provider config, augmenting a key-based provider with a key
     * from the OS keychain when none came from CLI args / env / settings.json.
     */
    public static ProviderConfig resolveConfig(SecretStore secrets) {
        ProviderConfig config = ProviderConfig.resolve(null, null, null, null);
        if ((config.apiKey() == null || config.apiKey().isBlank())
                && isKeyBased(config.provider())) {
            String key = secrets.get(config.provider()).orElse(null);
            if (key != null && !key.isBlank()) {
                config = ProviderConfig.resolve(config.provider(), config.model(), key, null);
            }
        }
        return config;
    }

    private static volatile boolean authKeychainInit;

    /**
     * Route ChatGPT/Codex OAuth tokens through the OS keychain (once per JVM):
     * if the keychain works, register it as xagent's AuthStorage backend and
     * migrate any existing auth.json into it. If there's no keychain, leave
     * AuthStorage on its file (auth.json) as before.
     */
    private static synchronized void initAuthKeychain() {
        if (authKeychainInit) {
            return;
        }
        authKeychainInit = true;
        SecretStore store = SecretStore.keychain();
        if (keychainWorks(store)) {
            AuthStorage.useBackend(new KeychainAuthBackend(store));
            AuthStorage.migrateFileToBackend();
        }
    }

    private static boolean keychainWorks(SecretStore store) {
        String probe = "__dogsbay_probe__";
        try {
            store.set(probe, "ok");
            boolean ok = store.get(probe).filter("ok"::equals).isPresent();
            store.delete(probe);
            return ok;
        } catch (Exception noKeychain) {
            return false;
        }
    }

    /**
     * Editor/DITA steering appended to the system prompt when the host exposes
     * editor tools (i.e. running embedded, not the standalone CLI). Folds in the
     * bulk-vs-targeted rule, DITA-catalog tool choice, and — the durable fix for
     * the capstone "fabricated done" — a grounded definition of done.
     */
    static String editorSteering(boolean hasEditorTools) {
        if (!hasEditorTools) {
            return "";
        }
        return "\n\n## Working in the DogsBay editor (DITA)\n"
            + "- For anything across the whole project or a map — validating, auditing, "
            + "or a bulk/uniform edit — use the project tools (validate_project, "
            + "project_health, conref_audit, schematron_project) or write a script "
            + "(code mode). Do NOT open or read every file individually.\n"
            + "- Validate DITA with validate_document / validate_project (they resolve "
            + "the DITA catalog); the generic xml_validate can't find the DITA DTDs.\n"
            + "- Definition of done: before reporting a task complete, run project_health "
            + "(or validate_project for the validation part) and SHOW its output. Do not "
            + "claim \"valid\", \"fixed\", or \"publish-ready\" unless a tool actually "
            + "confirmed it. If a check fails, fix it and re-run — never proceed as if it "
            + "passed.\n"
            + "- SURGICAL EDITS ONLY. Change content with targeted edits (the `edit` tool's "
            + "exact find/replace, or the reference-safe refactor tools) — never reformat. "
            + "Do NOT re-serialize or normalize a document to make a change: avoid identity "
            + "XSLT/`transform`, avoid `set_document_content` for anything but a genuine "
            + "whole-file rewrite, and never re-indent or re-wrap lines you aren't changing. "
            + "Preserve comments, significant whitespace (codeblock/pre/`xml:space`), "
            + "entities, and CDATA exactly. For bulk content edits, do many small targeted "
            + "replacements — not one parse→serialize pass. Layout is owned by the editor's "
            + "formatter (Format / format-on-save), not by your edits.\n"
            + "- SEMANTIC LINE BREAKS. When you add or rewrite prose, put each sentence on "
            + "its own line (one sentence per line), keeping inline tags on the sentence "
            + "they belong to. Do NOT reflow or re-wrap existing sentences. This keeps "
            + "diffs to the lines you actually changed.";
    }

    public static Agent create(AgentHost host, List<Skill> skills) {
        return create(host, skills, new TurnCheckpoint(
                host.workingDirectory().resolve(".xagent").resolve("checkpoints")));
    }

    /**
     * As {@link #create(AgentHost, List)}, but with a caller-owned
     * {@link TurnCheckpoint} so the chat controller can drive turn boundaries
     * ({@code beginTurn}) and {@code /revert}. Tool calls capture into it before
     * they mutate (plans/agent-turn-checkpoint-undo.md).
     */
    public static Agent create(AgentHost host, List<Skill> skills, TurnCheckpoint checkpoint) {
        initAuthKeychain();
        ProviderConfig config = resolveConfig(SecretStore.keychain());

        Path cwd = host.workingDirectory();
        ToolRegistry registry = ToolRegistry.createDefault(cwd);
        var editorTools = host.editorTools();
        registry.registerAll(editorTools);

        String prompt = new SystemPromptBuilder(registry, cwd).skills(skills).build()
                + editorSteering(!editorTools.isEmpty())
                + host.currentContext().toPromptFragment();

        // Resolve the model lazily from the agent's live config so /provider and
        // key changes (setProviderConfig → invalidateModel) take effect, and a
        // missing key surfaces only when a prompt is actually sent.
        final Agent[] ref = new Agent[1];
        Supplier<StreamingChatModel> lazyModel = () -> ProviderFactory.create(ref[0].config());
        Agent agent = new Agent(lazyModel, config, prompt, registry);
        ref[0] = agent;

        installPermissions(agent, registry, host, checkpoint);
        return agent;
    }

    /**
     * Gate non read-only tools behind approval: read-only tools bypass, rules in
     * {@code ~/.xagent/settings.json} (+ project) apply, and anything undecided
     * prompts the user via {@link SwingPermissionHandler}. "Always allow" persists
     * a rule to the project settings (shared with the CLI). This also covers
     * xagent's own write/edit/bash tools, not just the editor mutations.
     */
    private static void installPermissions(Agent agent, ToolRegistry registry, AgentHost host,
            TurnCheckpoint checkpoint) {
        Path cwd = host.workingDirectory();
        PermissionSettings.Rules rules = PermissionSettings.load(PermissionSettings.defaultFiles(cwd));
        PermissionPolicy policy = new PermissionPolicy(rules.allow(), rules.deny());
        PermissionHandler handler = new SwingPermissionHandler(host::dialogParent, host);

        Path projectSettings = cwd.resolve(".xagent").resolve("settings.json");
        Consumer<PermissionRule> persister = rule -> {
            try {
                PermissionSettings.appendAllowRule(projectSettings, rule);
            } catch (IOException ignore) {
                // best-effort persistence; the session grant still applies
            }
        };

        // Delegate runs first in the hook chain: checkpoint captures each target
        // file before it mutates (for /revert), then validation appends feedback to
        // the (already-permitted) tool result.
        ToolHooks delegate = new CompositeToolHooks(
                new CheckpointToolHooks(checkpoint, host),
                new ValidationFeedbackHooks(host),
                new SurgicalEditAdvisor());
        agent.setToolHooks(new PermissionToolHooks(registry, policy, handler, delegate, persister));
    }

    /** The providers whose credential is an API key, in the order shown. */
    public static final List<String> KEY_BASED_PROVIDERS = List.of("openai", "anthropic", "gemini");

    /**
     * What is stored to authenticate with, and how to forget it. Keeps the
     * keychain and the OAuth store behind one object so the settings dialog
     * never has to know which of the two a provider uses.
     */
    public static AgentCredentials credentials() {
        initAuthKeychain();
        AuthStorage storage = new AuthStorage();
        AgentCredentials.OAuth oauth = new AgentCredentials.OAuth() {
            @Override
            public boolean signedIn() {
                return storage.get(CodexOAuth.PROVIDER_ID).isPresent();
            }

            @Override
            public void forget() throws IOException {
                storage.remove(CodexOAuth.PROVIDER_ID);
            }
        };
        AgentCredentials.FallbackKey fallback = new AgentCredentials.FallbackKey() {
            @Override
            public java.util.Optional<String> get() {
                return java.util.Optional.ofNullable(ProviderConfig.savedApiKey());
            }

            @Override
            public void clear() throws IOException {
                ProviderConfig.clearSavedApiKey();
            }
        };
        return new AgentCredentials(SecretStore.keychain(), oauth, fallback, PROVIDERS, KEY_BASED_PROVIDERS,
                CodexOAuth.PROVIDER_ID);
    }

    /**
     * A one-line hint when {@code config}'s provider lacks usable credentials,
     * or {@code null} when it's ready (or needs none, e.g. Ollama). Ported from
     * {@code XAgentCli.authHint} so embedded and CLI guidance match.
     */
    public static String authHint(ProviderConfig config) {
        String provider = config.provider();
        if ("openai-codex".equals(provider)) {
            boolean loggedIn = new AuthStorage().get(CodexOAuth.PROVIDER_ID).isPresent();
            return loggedIn ? null
                    : "Not signed in to ChatGPT. Pick a provider below — Ollama needs no key — "
                            + "or sign in to ChatGPT via the xagent CLI (/login).";
        }
        if (config.apiKey() == null && isKeyBased(provider)) {
            return "No API key for " + provider
                    + ". Pick a provider below (Ollama needs no key) or paste an API key.";
        }
        return null;
    }
}
