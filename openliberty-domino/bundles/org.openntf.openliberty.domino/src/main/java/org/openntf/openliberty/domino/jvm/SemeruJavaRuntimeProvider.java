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
package org.openntf.openliberty.domino.jvm;

import static java.text.MessageFormat.format;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.openntf.openliberty.domino.config.RuntimeConfigurationProvider;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.runtime.Messages;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;

/**
 * Implementation of {@link JavaRuntimeProvider} that downloads and references
 * a build from IBM's Semeru distribution.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public class SemeruJavaRuntimeProvider extends AbstractDownloadingJavaRuntimeProvider {
	private static final Logger log = OpenLibertyLog.instance.log;
	
	public static final String API_RELEASES = "https://api.github.com/repos/ibmruntimes/semeru{0}-binaries/releases?per_page=100"; //$NON-NLS-1$
	
	public static final String TYPE_OPENJ9 = "OpenJ9"; //$NON-NLS-1$
	
	public static final String PROVIDER_NAME = "IBM Semeru"; //$NON-NLS-1$
	
	@Override
	public boolean canProvide(JVMIdentifier identifier) {
		return TYPE_OPENJ9.equals(identifier.getType());
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Path getJavaHome(JVMIdentifier identifier) {
		String version = identifier.getVersion();
		String javaJvm = identifier.getType();
		if(log.isLoggable(Level.FINE)) {
			log.fine(format(Messages.getString("JavaRuntimeProvider.configuredJavaRuntimeWithJVM"), version , javaJvm)); //$NON-NLS-1$
		}	
		
		// Check for an existing JVM
		RuntimeConfigurationProvider config = OpenLibertyUtil.findRequiredExtension(RuntimeConfigurationProvider.class);
		Path execDir = config.getBaseDirectory();
		
		Path jvmDir = execDir.resolve("jvm").resolve(version + "-" + javaJvm); //$NON-NLS-1$ //$NON-NLS-2$
		String execName = "java" + (OpenLibertyUtil.IS_WINDOWS ? ".exe" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		if(Files.exists(jvmDir.resolve("bin").resolve(execName))) { //$NON-NLS-1$
			if(log.isLoggable(Level.FINE)) {
				log.info(format(Messages.getString("JavaRuntimeProvider.usingDownloadedRuntime"), jvmDir)); //$NON-NLS-1$
			}
			// Assume the whole JVM exists
			return jvmDir;
		}
		
		// Otherwise, look for a release on GitHub
		if("1.8".equals(version)) { //$NON-NLS-1$
			version = "8"; //$NON-NLS-1$
		}
		String releasesUrl = format(API_RELEASES, version);
		// TODO support pagination here (page= and per_page query params, 
		List<Map<String, Object>> releases = fetchGitHubReleasesList(PROVIDER_NAME, releasesUrl);
		
		// Find any applicable releases, in order, as some releases may contain only certain platforms
		List<Map<String, Object>> validReleases = releases.stream()
			.filter(release -> !(Boolean)release.get("prerelease")) //$NON-NLS-1$
			.filter(release -> !(Boolean)release.get("draft")) //$NON-NLS-1$
			.filter(release -> release.containsKey("assets")) //$NON-NLS-1$
			.collect(Collectors.toList());
		if(validReleases.isEmpty()) {
			throw new IllegalStateException(format(Messages.getString("JavaRuntimeProvider.unableToLocateJDKBuild"), PROVIDER_NAME, identifier, releasesUrl)); //$NON-NLS-1$
		}
		
		// HotSpot:
		//    Linux: https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.6%2B10/OpenJDK11U-jdk_x64_linux_hotspot_11.0.6_10.tar.gz
		//    Windows x64: https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.6%2B10/OpenJDK11U-jdk_x64_windows_hotspot_11.0.6_10.zip
		//    Windows x86: https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.6%2B10/OpenJDK11U-jdk_x86-32_windows_hotspot_11.0.6_10.zip
		// OpenJ9:
		//    Linux: https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.6%2B10_openj9-0.18.1/OpenJDK11U-jdk_x64_linux_openj9_11.0.6_10_openj9-0.18.1.tar.gz
		//    Windows x64: https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.6%2B10_openj9-0.18.1/OpenJDK11U-jdk_x64_windows_openj9_11.0.6_10_openj9-0.18.1.zip
		String qualifier = format("jdk_{0}_{1}", getOsArch(), getOsName()); //$NON-NLS-1$
		Map<String, Object> download = validReleases.stream()
			.map(release -> (List<Map<String, Object>>)release.get("assets")) //$NON-NLS-1$
			.flatMap(Collection::stream)
			.filter(asset -> !StringUtil.toString(asset.get("name")).contains("-testimage")) //$NON-NLS-1$ //$NON-NLS-2$
			.filter(asset -> !StringUtil.toString(asset.get("name")).contains("-debugimage")) //$NON-NLS-1$ //$NON-NLS-2$
			.filter(asset -> StringUtil.toString(asset.get("name")).contains("-" + qualifier + "_")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			.filter(asset -> "application/x-compressed-tar".equals(asset.get("content_type")) || "application/zip".equals(asset.get("content_type"))) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(format(Messages.getString("JavaRuntimeProvider.unableToFindJDKBuildFor"), PROVIDER_NAME, qualifier))); //$NON-NLS-1$
		if(log.isLoggable(Level.INFO)) {
			log.info(format(Messages.getString("JavaRuntimeProvider.downloadingJDKFrom"), PROVIDER_NAME, download.get("browser_download_url")));  //$NON-NLS-1$//$NON-NLS-2$
		}
		
		String contentType = (String)download.get("content_type"); //$NON-NLS-1$
		download((String)download.get("browser_download_url"), contentType, jvmDir); //$NON-NLS-1$
		
		markExecutables(jvmDir);
		
		return jvmDir;
	}

	private static String getOsArch() {
		String arch = System.getProperty("os.arch"); //$NON-NLS-1$
		if("x86_64".equals(arch) || "amd64".equals(arch)) { //$NON-NLS-1$ //$NON-NLS-2$
			return "x64"; //$NON-NLS-1$
		} else {
			return "x86"; //$NON-NLS-1$
		}
	}
}
