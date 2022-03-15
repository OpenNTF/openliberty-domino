/*
 * Copyright © 2018-2022 Jesse Gallagher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openntf.openliberty.domino.wlp.dominoapi.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Manifest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TestParseTemplates {
	@ParameterizedTest
	@ValueSource(strings = {"/subsystem-template.mf", "/manifest-template.mf"})
	public void testManifest(String res) throws IOException {
		try(InputStream is = getClass().getResourceAsStream(res)) {
			new Manifest(is);
		}
	}
}
