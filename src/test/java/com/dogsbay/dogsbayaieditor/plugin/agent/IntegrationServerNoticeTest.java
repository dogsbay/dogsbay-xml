package com.dogsbay.dogsbayaieditor.plugin.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A hosted agent is handed its MCP servers at session start and can never be
 * given more, so turning the integration server on does nothing for a session
 * already running. The old message said only "enable it in Settings → Server",
 * which read as though that were enough.
 */
class IntegrationServerNoticeTest {

	@Test
	@DisplayName("a session opening without tools is told a new session is needed")
	void openingWithoutToolsSaysWhatToDo() {
		String note = IntegrationServerNotice.startedWithoutTools();

		assertThat(note).contains("Settings → Server");
		// The half that was missing: enabling it does not reach this session.
		assertThat(note).contains("start a new session");
	}

	@Test
	@DisplayName("turning the server on tells a session that cannot use it")
	void serverComingOnTellsTheSessionsItCannotHelp() {
		String note = IntegrationServerNotice.afterServerChanged(false, true);

		assertThat(note).isNotNull();
		assertThat(note).contains("new session");
	}

	@Test
	@DisplayName("a session that already has the tools, and still does, is left alone")
	void aSessionWithToolsIsLeftAlone() {
		assertThat(IntegrationServerNotice.afterServerChanged(true, true)).isNull();
	}

	@Test
	@DisplayName("a session whose tools have just been switched off is told so")
	void losingTheServerIsWorthSaying() {
		String note = IntegrationServerNotice.afterServerChanged(true, false);

		// Silence here was the worst case: the agent has been told it has the
		// editor's tools, and the next call fails as a transport error it
		// cannot explain.
		assertThat(note).isNotNull();
		assertThat(note).contains("turned off");
	}

	@Test
	@DisplayName("turning the server off says nothing to a session that never had it")
	void turningItOffIsNotWorthSaying() {
		assertThat(IntegrationServerNotice.afterServerChanged(false, false)).isNull();
	}

	@Test
	@DisplayName("the transport is named when tools are there")
	void toolsNameTheirTransport() {
		assertThat(IntegrationServerNotice.startedWithTools(true)).contains("HTTP");
		assertThat(IntegrationServerNotice.startedWithTools(false)).contains("stdio");
	}
}
