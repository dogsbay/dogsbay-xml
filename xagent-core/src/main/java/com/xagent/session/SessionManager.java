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
package com.xagent.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xagent.message.AgentMessage;
import com.xagent.message.AssistantMessage;
import com.xagent.message.ToolResultMessage;
import com.xagent.message.UserMessage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Manages session persistence as JSONL files in ~/.xagent/sessions/.
 * Each session is one file. First line is the header, subsequent lines are entries.
 */
public class SessionManager {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final int CURRENT_VERSION = 1;

	private final Path sessionsDir;

	public SessionManager(Path sessionsDir) {
		this.sessionsDir = sessionsDir;
	}

	public SessionManager() {
		this(Path.of(System.getProperty("user.home"), ".xagent", "sessions"));
	}

	/**
	 * Create a new session file and write the header.
	 */
	public String createSession(String cwd, String provider, String model) throws IOException {
		String sessionId = UUID.randomUUID().toString().substring(0, 8);
		var header = new SessionEntry.Header(
			sessionId, CURRENT_VERSION, Instant.now(), cwd, provider, model);
		headers.put(sessionId, header);
		writeHeader(header);
		return sessionId;
	}

	/**
	 * Claim a session id without writing anything yet. The file appears at the
	 * first {@link #appendMessage} or {@link #appendSummary}.
	 *
	 * <p>Creating it eagerly wrote a transcript for every editor launch whether
	 * or not anyone spoke to the agent: 189 of the 239 files on the machine that
	 * prompted this were a header and nothing else, burying the conversations
	 * that had something in them.
	 */
	public String reserveSession(String cwd, String provider, String model) {
		String sessionId = UUID.randomUUID().toString().substring(0, 8);
		headers.put(sessionId, new SessionEntry.Header(
			sessionId, CURRENT_VERSION, Instant.now(), cwd, provider, model));
		return sessionId;
	}

	/** Write the header a reserved session is still holding, if it has one. */
	private void materialize(String sessionId) throws IOException {
		SessionEntry.Header header = headers.get(sessionId);
		if (header != null && !Files.exists(sessionFile(sessionId))) {
			writeHeader(header);
		}
	}

	private void writeHeader(SessionEntry.Header header) throws IOException {
		Files.createDirectories(sessionsDir);
		try (var writer = Files.newBufferedWriter(sessionFile(header.id()),
				StandardOpenOption.CREATE_NEW)) {
			writer.write(entryToJson(header));
			writer.newLine();
		}
	}

	/**
	 * The header of every session this manager has created or reserved.
	 *
	 * <p>Kept after the header is written, not discarded: an append whose file
	 * has gone — deleted by another window's sweep, or lost to a failed write —
	 * would otherwise recreate it with a message as its first line, and a file
	 * with no header can never be listed or resumed again.
	 */
	private final java.util.Map<String, SessionEntry.Header> headers =
		new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Append a message entry to an existing session.
	 */
	public void appendMessage(String sessionId, AgentMessage message, String parentId) throws IOException {
		String entryId = UUID.randomUUID().toString().substring(0, 8);
		SessionEntry entry = switch (message) {
			case UserMessage u -> new SessionEntry.Message(
				entryId, parentId, u.timestamp(), "user", u.content(), false
			);
			case AssistantMessage a -> new SessionEntry.Message(
				entryId, parentId, a.timestamp(), "assistant", a.content(), a.hasToolCalls()
			);
			case ToolResultMessage t -> new SessionEntry.ToolResult(
				entryId, parentId, t.timestamp(), t.toolCallId(), t.toolName(), t.content(), t.isError()
			);
		};
		appendEntry(sessionId, entry);
	}

	/**
	 * Append a summary entry (used for session listing).
	 */
	public void appendSummary(String sessionId, String summaryText) throws IOException {
		String entryId = UUID.randomUUID().toString().substring(0, 8);
		var entry = new SessionEntry.Summary(entryId, Instant.now(), summaryText);
		appendEntry(sessionId, entry);
	}

	/**
	 * Load all entries from a session file.
	 */
	public List<SessionEntry> loadSession(String sessionId) throws IOException {
		Path file = sessionFile(sessionId);
		if (!Files.exists(file)) {
			throw new IOException("Session not found: " + sessionId);
		}
		var entries = new ArrayList<SessionEntry>();
		for (String line : Files.readAllLines(file)) {
			if (!line.isBlank()) {
				entries.add(jsonToEntry(line));
			}
		}
		return entries;
	}

	/**
	 * Reconstruct the message list from a session's entries.
	 */
	public List<AgentMessage> loadMessages(String sessionId) throws IOException {
		var entries = loadSession(sessionId);
		var messages = new ArrayList<AgentMessage>();
		for (var entry : entries) {
			switch (entry) {
				case SessionEntry.Message m -> {
					switch (m.role()) {
						case "user" -> messages.add(new UserMessage(m.content(), m.timestamp()));
						case "assistant" -> messages.add(
							new AssistantMessage(m.content(), m.timestamp(), null, null)
						);
						default -> {}
					}
				}
				case SessionEntry.ToolResult t -> messages.add(
					new ToolResultMessage(t.toolCallId(), t.toolName(), t.content(), t.isError(), t.timestamp())
				);
				default -> {}
			}
		}
		return messages;
	}

	/**
	 * List all sessions, most recent first.
	 */
	public List<SessionInfo> listSessions() throws IOException {
		if (!Files.exists(sessionsDir)) {
			return List.of();
		}
		var sessions = new ArrayList<SessionInfo>();
		try (Stream<Path> files = Files.list(sessionsDir)) {
			for (Path file : files.filter(f -> f.toString().endsWith(".jsonl")).toList()) {
				try {
					String firstLine = Files.readAllLines(file).stream().findFirst().orElse(null);
					if (firstLine == null) continue;
					var header = (SessionEntry.Header) jsonToEntry(firstLine);

					// Find summary or first user message for display
					String preview = "";
					var allLines = Files.readAllLines(file);
					for (int i = allLines.size() - 1; i >= 1; i--) {
						var entry = jsonToEntry(allLines.get(i));
						if (entry instanceof SessionEntry.Summary s) {
							preview = stripEditorContext(s.text());
							break;
						}
					}
					// Sessions recorded before the summary excluded the editor
					// context have the context as their summary, truncated past
					// the marker. It names nothing, so fall through to the message.
					if (isEditorContext(preview)) {
						preview = "";
					}
					if (preview.isEmpty()) {
						for (int i = 1; i < allLines.size(); i++) {
							var entry = jsonToEntry(allLines.get(i));
							if (entry instanceof SessionEntry.Message m && "user".equals(m.role())) {
								String said = stripEditorContext(m.content());
								if (said.isBlank()) {
									continue;   // context only — keep looking for what was asked
								}
								preview = truncate(said, 80);
								break;
							}
						}
					}

					sessions.add(new SessionInfo(header.id(), header.timestamp(), header.provider(),
						header.model(), header.cwd(), preview));
				} catch (Exception ignored) {
					// Skip corrupted session files
				}
			}
		}
		sessions.sort(Comparator.comparing(SessionInfo::timestamp).reversed());
		return sessions;
	}

	/** Whether this text is the injected context block rather than anything said. */
	private static boolean isEditorContext(String text) {
		return text != null && text.stripLeading().startsWith(CONTEXT_HEADING);
	}

	/** The first line of the block the editor prepends; see {@code AgentContext}. */
	private static final String CONTEXT_HEADING = "## Editor context";

	/**
	 * The user's own words, without the editor context prepended to the first
	 * message. Every session's first message begins with that block, so
	 * previewing it made every row read "## Editor context - Mode: Agent - …"
	 * and no session could be told from another.
	 */
	public static String stripEditorContext(String content) {
		if (content == null) {
			return "";
		}
		int marker = content.lastIndexOf(CONTEXT_MARKER);
		return (marker >= 0 ? content.substring(marker + CONTEXT_MARKER.length()) : content).strip();
	}

	/**
	 * What the editor puts between the context it injects and what the user
	 * typed. Defined here because both the sender and the reader of a
	 * transcript need it, and two copies would drift.
	 */
	public static final String CONTEXT_MARKER = "\n\nUser request:\n";

	/**
	 * Get the header for a session.
	 */
	public SessionEntry.Header getHeader(String sessionId) throws IOException {
		var entries = loadSession(sessionId);
		if (entries.isEmpty() || !(entries.getFirst() instanceof SessionEntry.Header h)) {
			throw new IOException("Invalid session file: " + sessionId);
		}
		return h;
	}

	/**
	 * Find the most recent session ID, or null if none.
	 */
	public String lastSessionId() throws IOException {
		var sessions = listSessions();
		return sessions.isEmpty() ? null : sessions.getFirst().id();
	}

	public Path sessionFile(String sessionId) {
		return sessionsDir.resolve(sessionId + ".jsonl");
	}

	/**
	 * Delete one session's transcript.
	 *
	 * @return true when a file was removed, false when there was nothing there
	 * @throws IOException              if the file exists but cannot be deleted
	 * @throws IllegalArgumentException if the id is not a plain session id — it
	 *                                  becomes a file name, so a path separator
	 *                                  or {@code ..} in it must not reach the
	 *                                  filesystem
	 */
	public boolean deleteSession(String sessionId) throws IOException {
		if (sessionId == null || !SESSION_ID.matcher(sessionId).matches()) {
			throw new IllegalArgumentException("Not a session id: " + sessionId);
		}
		return Files.deleteIfExists(sessionFile(sessionId));
	}

	/**
	 * Delete sessions last written more than {@code days} days ago.
	 *
	 * <p>A transcript is the readable record of what an agent did to a project,
	 * so nothing here runs unasked: the caller decides the age, and zero or
	 * fewer deletes nothing at all rather than everything.
	 *
	 * @param days how old a session must be to go; 0 or less is a no-op
	 * @return the ids deleted, oldest first
	 */
	public List<String> deleteSessionsOlderThan(int days) throws IOException {
		if (days <= 0) {
			return List.of();
		}
		Instant cutoff = Instant.now().minus(java.time.Duration.ofDays(days));
		// Age is what the caller asked about, so the ids come back in that
		// order — sorting the ids themselves would order random hex.
		var deleted = new ArrayList<java.util.Map.Entry<Instant, String>>();
		try (Stream<Path> files = list(sessionsDir)) {
			for (Path file : files.filter(f -> f.toString().endsWith(".jsonl")).toList()) {
				try {
					Instant modified = Files.getLastModifiedTime(file).toInstant();
					if (modified.isBefore(cutoff)) {
						String name = file.getFileName().toString();
						Files.delete(file);
						deleted.add(java.util.Map.entry(modified,
							name.substring(0, name.length() - ".jsonl".length())));
					}
				} catch (IOException ignored) {
					// a file that will not go is left alone; the rest still run
				}
			}
		}
		deleted.sort(java.util.Map.Entry.comparingByKey());
		return deleted.stream().map(java.util.Map.Entry::getValue).toList();
	}

	/**
	 * Give a session a name of its own.
	 *
	 * <p>Stored as a summary entry, which is what the picker shows and what the
	 * agent writes for the first thing asked of it — so a rename is simply a
	 * later, better answer to the same question.
	 *
	 * @param title the name; blank restores the automatic one
	 */
	public void renameSession(String sessionId, String title) throws IOException {
		String named = title == null ? "" : title.strip();
		if (named.isEmpty() && !Files.exists(sessionFile(sessionId))) {
			return;   // nothing said, nothing named: there is nothing to clear
		}
		// An empty summary is how a name is cleared: the reader falls through to
		// the first thing asked, which is the name it would have had.
		appendSummary(sessionId, named);
	}

	/**
	 * A session's name — its last summary, or the first thing asked of it.
	 *
	 * <p>Reads the one file rather than the whole directory, because callers ask
	 * this on the event thread after every turn.
	 */
	public String sessionTitle(String sessionId) {
		try {
			String name = "";
			for (SessionEntry entry : loadSession(sessionId)) {
				if (entry instanceof SessionEntry.Summary s) {
					name = stripEditorContext(s.text());
				}
			}
			if (!name.isBlank() && !isEditorContext(name)) {
				return name;
			}
			for (SessionEntry entry : loadSession(sessionId)) {
				if (entry instanceof SessionEntry.Message m && "user".equals(m.role())) {
					String asked = stripEditorContext(m.content());
					if (!asked.isBlank()) {
						return truncate(asked, 80);
					}
				}
			}
		} catch (IOException noSuchSession) {
			// a session with no file yet has no name
		}
		return "";
	}

	/** Whether this session already carries a name of its own. */
	public boolean hasName(String sessionId) {
		try {
			for (SessionEntry entry : loadSession(sessionId)) {
				if (entry instanceof SessionEntry.Summary s && !s.text().isBlank()) {
					return true;
				}
			}
		} catch (IOException noSuchSession) {
			return false;
		}
		return false;
	}

	/**
	 * Delete transcripts that never got past their header.
	 *
	 * <p>Sessions used to be created when the editor started rather than when
	 * someone spoke, so a machine accumulates one empty file per launch. They
	 * hold no conversation, so there is nothing to lose and nothing to weigh:
	 * this runs without asking, unlike {@link #deleteSessionsOlderThan}.
	 *
	 * @return how many were removed
	 */
	public int deleteEmptySessions() throws IOException {
		int removed = 0;
		try (Stream<Path> files = list(sessionsDir)) {
			for (Path file : files.filter(f -> f.toString().endsWith(".jsonl")).toList()) {
				try {
					if (isHeaderOnly(file)) {
						Files.delete(file);
						removed++;
					}
				} catch (IOException ignored) {
					// unreadable or undeletable: leave it, carry on
				}
			}
		}
		return removed;
	}

	/** A file with a header and no entries — a session nobody said anything in. */
	private static boolean isHeaderOnly(Path file) throws IOException {
		try (Stream<String> lines = Files.lines(file)) {
			return lines.filter(l -> !l.isBlank()).limit(2).count() <= 1;
		}
	}

	/** How many transcripts are on disk. */
	public int sessionCount() throws IOException {
		try (Stream<Path> files = list(sessionsDir)) {
			return (int) files.filter(f -> f.toString().endsWith(".jsonl")).count();
		}
	}

	/**
	 * The files in {@code dir}, or nothing when there is no such directory —
	 * including when it is removed between the check and the listing, which
	 * {@code Files.exists} followed by {@code Files.list} does not cover.
	 */
	private static Stream<Path> list(Path dir) throws IOException {
		try {
			return Files.list(dir);
		} catch (java.nio.file.NoSuchFileException | java.nio.file.NotDirectoryException absent) {
			return Stream.empty();
		}
	}

	/** A session id as it appears in a file name: hex, no separators. */
	private static final java.util.regex.Pattern SESSION_ID =
		java.util.regex.Pattern.compile("[A-Za-z0-9_-]{1,64}");

	private void appendEntry(String sessionId, SessionEntry entry) throws IOException {
		// A reserved session becomes a file here, at its first entry — and the
		// header has to land before the entry, or the file is unreadable.
		materialize(sessionId);
		Path file = sessionFile(sessionId);
		try (var writer = Files.newBufferedWriter(file,
			StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
			writer.write(entryToJson(entry));
			writer.newLine();
		}
	}

	String entryToJson(SessionEntry entry) {
		ObjectNode node = MAPPER.createObjectNode();
		switch (entry) {
			case SessionEntry.Header h -> {
				node.put("type", "header");
				node.put("id", h.id());
				node.put("version", h.version());
				node.put("timestamp", h.timestamp().toString());
				node.put("cwd", h.cwd());
				node.put("provider", h.provider());
				node.put("model", h.model());
			}
			case SessionEntry.Message m -> {
				node.put("type", "message");
				node.put("id", m.id());
				if (m.parentId() != null) node.put("parentId", m.parentId());
				node.put("timestamp", m.timestamp().toString());
				node.put("role", m.role());
				node.put("content", m.content());
				node.put("hasToolCalls", m.hasToolCalls());
			}
			case SessionEntry.ToolResult t -> {
				node.put("type", "toolResult");
				node.put("id", t.id());
				if (t.parentId() != null) node.put("parentId", t.parentId());
				node.put("timestamp", t.timestamp().toString());
				node.put("toolCallId", t.toolCallId());
				node.put("toolName", t.toolName());
				node.put("content", t.content());
				node.put("isError", t.isError());
			}
			case SessionEntry.Summary s -> {
				node.put("type", "summary");
				node.put("id", s.id());
				node.put("timestamp", s.timestamp().toString());
				node.put("text", s.text());
			}
		}
		return node.toString();
	}

	SessionEntry jsonToEntry(String json) throws IOException {
		JsonNode node = MAPPER.readTree(json);
		String type = node.path("type").asText();
		return switch (type) {
			case "header" -> new SessionEntry.Header(
				node.path("id").asText(),
				node.path("version").asInt(),
				Instant.parse(node.path("timestamp").asText()),
				node.path("cwd").asText(),
				node.path("provider").asText(),
				node.path("model").asText()
			);
			case "message" -> new SessionEntry.Message(
				node.path("id").asText(),
				node.has("parentId") ? node.path("parentId").asText() : null,
				Instant.parse(node.path("timestamp").asText()),
				node.path("role").asText(),
				node.path("content").asText(),
				node.path("hasToolCalls").asBoolean()
			);
			case "toolResult" -> new SessionEntry.ToolResult(
				node.path("id").asText(),
				node.has("parentId") ? node.path("parentId").asText() : null,
				Instant.parse(node.path("timestamp").asText()),
				node.path("toolCallId").asText(),
				node.path("toolName").asText(),
				node.path("content").asText(),
				node.path("isError").asBoolean()
			);
			case "summary" -> new SessionEntry.Summary(
				node.path("id").asText(),
				Instant.parse(node.path("timestamp").asText()),
				node.path("text").asText()
			);
			default -> throw new IOException("Unknown entry type: " + type);
		};
	}

	private static String truncate(String text, int maxLen) {
		if (text == null) return "";
		String singleLine = text.replace("\n", " ");
		if (singleLine.length() <= maxLen) return singleLine;
		return singleLine.substring(0, maxLen - 3) + "...";
	}

	/**
	 * Info record for session listing.
	 */
	public record SessionInfo(
		String id,
		Instant timestamp,
		String provider,
		String model,
		String cwd,
		String preview
	) {}
}
