/**
 * Copyright © 2018-2019 Jesse Gallagher
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
package org.openntf.openliberty.domino.userregistry;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.openntf.openliberty.domino.ext.ExtensionDeployer;

public class UserRegistryExtension implements ExtensionDeployer {
	private final String symbolicName;
	private final String bundleVersion;
	
	public UserRegistryExtension() throws IOException {
		String name = null;
		String version = null;
		try(InputStream is = getClass().getResourceAsStream("/ext/plugin.jar")) {
			try(ZipInputStream zis = new ZipInputStream(is)) {
				ZipEntry entry = zis.getNextEntry();
				while(entry != null) {
					if(entry.getName().replace('\\', '/').equals("META-INF/MANIFEST.MF")) {
						Manifest mf = new Manifest(zis);
						name = mf.getMainAttributes().getValue("Bundle-SymbolicName");
						int semiIndex = name.indexOf(';');
						if(semiIndex > -1) {
							name = name.substring(0, semiIndex);
						}
						version = mf.getMainAttributes().getValue("Bundle-Version");
					}
					
					zis.closeEntry();
					entry = zis.getNextEntry();
				}
			}
		}
		
		symbolicName = name;
		bundleVersion = version;
	}

	@Override
	public List<InputStream> getBundleData() {
		return Arrays.asList(
			getClass().getResourceAsStream("/ext/plugin.jar")
		);
	}

	@Override
	public List<String> getBundleFileNames() {
		return Arrays.asList(symbolicName + "_" + bundleVersion + ".jar");
	}

	@Override
	public String getFeatureId() {
		return "dominoUserRegistry-1.0";
	}
	
	@Override
	public String getSubsystemContent() {
		String core = symbolicName + ";version=" + bundleVersion;
		StringBuilder content = new StringBuilder();
		content.append("org.openntf.notes.java.api; version=0.0.0");
		content.append(',');
		content.append(core);
		return content.toString();
	}
	
	@Override
	public String getSubsystemVersion() {
		return bundleVersion;
	}
}
