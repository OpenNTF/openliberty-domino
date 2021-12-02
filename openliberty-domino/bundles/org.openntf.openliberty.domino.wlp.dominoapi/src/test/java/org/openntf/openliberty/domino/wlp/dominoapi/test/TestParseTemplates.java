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
