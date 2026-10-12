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
package com.xagent.tool.dita;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xagent.tool.operations.BashOperations;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class DitaBuildToolTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final Path CWD = Path.of("/work");

	private record StubCall(String command, Path cwd, Duration timeout) {}

	private static BashOperations stub(int exitCode, String stdout, String stderr,
		AtomicReference<StubCall> capture) {
		return new BashOperations() {
			@Override
			public BashResult exec(String command, Path cwd, Duration timeout,
				Consumer<String> onOutput, Supplier<Boolean> isCancelled) {
				if (capture != null) {
					capture.set(new StubCall(command, cwd, timeout));
				}
				return new BashResult(exitCode, stdout, stderr);
			}
		};
	}

	private static ObjectNode params(String map) {
		var params = MAPPER.createObjectNode();
		params.put("map", map);
		return params;
	}

	@Test
	void buildsExpectedCommand() {
		var capture = new AtomicReference<StubCall>();
		var tool = new DitaBuildTool(CWD, stub(0, "BUILD SUCCESSFUL", "", capture));
		var p = params("doc/guide.ditamap");
		p.put("transtype", "pdf");
		p.put("output", "build/pdf");

		tool.execute("id", p, () -> false, null);

		assertThat(capture.get().command())
			.isEqualTo("dita --input=doc/guide.ditamap --format=pdf --output=build/pdf");
		assertThat(capture.get().cwd()).isEqualTo(CWD);
		assertThat(capture.get().timeout()).isEqualTo(Duration.ofSeconds(600));
	}

	@Test
	void quotesArgumentsWithSpaces() {
		var capture = new AtomicReference<StubCall>();
		var tool = new DitaBuildTool(CWD, stub(0, "", "", capture));

		tool.execute("id", params("my docs/guide.ditamap"), () -> false, null);

		assertThat(capture.get().command()).contains("--input='my docs/guide.ditamap'");
	}

	@Test
	void successReportsTranstypeAndOutput() {
		var tool = new DitaBuildTool(CWD, stub(0, "BUILD SUCCESSFUL in 12s", "", null));

		var result = tool.execute("id", params("guide.ditamap"), () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("succeeded").contains("html5").contains("out");
	}

	@Test
	void successIncludesWarnings() {
		String log = """
			Processing...
			[WARN] [DOTX032E] topics/intro.dita:8:15: No short description found.
			BUILD SUCCESSFUL
			""";
		var tool = new DitaBuildTool(CWD, stub(0, log, "", null));

		var result = tool.execute("id", params("guide.ditamap"), () -> false, null);

		assertThat(result.isError()).isFalse();
		assertThat(result.content()).contains("1 warning");
		assertThat(result.content()).contains("topics/intro.dita:8:15: warning:");
	}

	@Test
	void failureParsesStructuredErrors() {
		String log = """
			Processing guide.ditamap
			[ERROR] [DOTJ012F] file:/work/topics/install.dita:14:9: Element step not allowed here.
			[ERROR] Failed to parse file:/work/topics/setup.dita:3:1: premature end of file
			BUILD FAILED
			""";
		var tool = new DitaBuildTool(CWD, stub(1, log, "", null));

		var result = tool.execute("id", params("guide.ditamap"), () -> false, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("build failed (exit 1)").contains("2 errors");
		assertThat(result.content()).contains("topics/install.dita:14:9: error:");

		var issues = result.details().get("issues");
		assertThat(issues.size()).isEqualTo(2);
		assertThat(issues.get(0).get("code").asText()).isEqualTo("DOTJ012F");
		assertThat(issues.get(0).get("file").asText()).endsWith("topics/install.dita");
		assertThat(issues.get(0).get("line").asInt()).isEqualTo(14);
		assertThat(issues.get(1).get("line").asInt()).isEqualTo(3);
	}

	@Test
	void failureWithoutParseableErrorsShowsLogTail() {
		var tool = new DitaBuildTool(CWD, stub(127, "", "bash: dita: command not found", null));

		var result = tool.execute("id", params("guide.ditamap"), () -> false, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("command not found");
	}

	@Test
	void missingMapIsRejected() {
		var tool = new DitaBuildTool(CWD, stub(0, "", "", null));

		var result = tool.execute("id", MAPPER.createObjectNode(), () -> false, null);

		assertThat(result.isError()).isTrue();
		assertThat(result.content()).contains("map");
	}

	@Test
	void customTimeoutAndCommandAreHonored() {
		var capture = new AtomicReference<StubCall>();
		var tool = new DitaBuildTool(CWD, stub(0, "", "", capture));
		var p = params("guide.ditamap");
		p.put("dita_cmd", "/opt/dita-ot/bin/dita");
		p.put("timeout_seconds", 60);

		tool.execute("id", p, () -> false, null);

		assertThat(capture.get().command()).startsWith("/opt/dita-ot/bin/dita ");
		assertThat(capture.get().timeout()).isEqualTo(Duration.ofSeconds(60));
	}

	@Test
	void toolIsMutating() {
		var tool = new DitaBuildTool(CWD, stub(0, "", "", null));
		assertThat(tool.isReadOnly()).isFalse();
	}
}
