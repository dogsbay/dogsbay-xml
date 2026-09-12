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

package com.dogsbay.dogsbayaieditor.plugin.agent;

/**
 * What to tell a hosted agent about the editor's integration server.
 *
 * <p>
 * A hosted agent is handed its MCP servers once, at {@code session/new}. The
 * protocol has no way to add one later, so a session started while the
 * integration server was off can never gain the editor's tools — turning the
 * server on afterwards does nothing for it. Saying only "enable it in
 * Settings → Server" left people enabling it, watching nothing change, and
 * asking the agent why, which is a question the agent cannot answer.
 */
final class IntegrationServerNotice {

	private IntegrationServerNotice() {
	}

	/** What to say when a session opens without the editor's tools. */
	static String startedWithoutTools() {
		return "The editor's integration server is off, so this agent has no DITA tools. "
				+ "Enable it in Settings → Server, then start a new session — a running one "
				+ "keeps the tools it was given when it started.";
	}

	/** What to say when a session opens with them. */
	static String startedWithTools(boolean overHttp) {
		return "Editor tools available over " + (overHttp ? "HTTP" : "stdio") + " MCP as this session.";
	}

	/**
	 * What to tell a session that is already running, now that the server's
	 * state has changed, or null when there is nothing worth saying.
	 *
	 * @param sessionHasTools whether this session was given the editor's tools
	 * @param serverRunning   whether the integration server is reachable now
	 */
	static String afterServerChanged(boolean sessionHasTools, boolean serverRunning) {
		if (sessionHasTools && !serverRunning) {
			// The case that most needs saying: this session was given the tools
			// and they have just stopped answering. Left silent, the agent goes
			// on believing it has them and the next call fails as a transport
			// error it cannot explain.
			return "The integration server has been turned off. This session was given the editor's "
					+ "DITA tools when it started and they will now fail — turn the server back on "
					+ "in Settings → Server, or start a new session without them.";
		}
		if (sessionHasTools || !serverRunning) {
			// Already has them and still does, or had none and gains none.
			return null;
		}
		return "The integration server is on now, but this session cannot pick it up — a hosted "
				+ "agent is given its tools when it starts. Open a new session to work with the "
				+ "editor's DITA tools.";
	}
}
