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
package com.xagent.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryTest {

	@TempDir
	Path tempDir;

	@Test
	void createDefaultRegistersAllTools() {
		var registry = ToolRegistry.createDefault(tempDir);

		assertThat(registry.size()).isEqualTo(12);
		assertThat(registry.get("read")).isNotNull();
		assertThat(registry.get("write")).isNotNull();
		assertThat(registry.get("edit")).isNotNull();
		assertThat(registry.get("bash")).isNotNull();
		assertThat(registry.get("grep")).isNotNull();
		assertThat(registry.get("find")).isNotNull();
		assertThat(registry.get("ls")).isNotNull();
		assertThat(registry.get("xml_validate")).isNotNull();
		assertThat(registry.get("xpath")).isNotNull();
		assertThat(registry.get("xml_format")).isNotNull();
		assertThat(registry.get("xslt")).isNotNull();
		assertThat(registry.get("dita_build")).isNotNull();
	}

	@Test
	void toToolSpecifications() {
		var registry = ToolRegistry.createDefault(tempDir);
		var specs = registry.toToolSpecifications();

		assertThat(specs).hasSize(12);
		assertThat(specs.get(0).name()).isEqualTo("read");
		assertThat(specs.get(0).description()).isNotEmpty();
		assertThat(specs.get(0).parameters()).isNotNull();
	}

	@Test
	void registerCustomTool() {
		var registry = new ToolRegistry();
		assertThat(registry.size()).isEqualTo(0);

		registry.register(new ReadTool(tempDir, new com.xagent.tool.operations.DefaultFileOperations()));
		assertThat(registry.size()).isEqualTo(1);
		assertThat(registry.get("read")).isNotNull();
	}
}
