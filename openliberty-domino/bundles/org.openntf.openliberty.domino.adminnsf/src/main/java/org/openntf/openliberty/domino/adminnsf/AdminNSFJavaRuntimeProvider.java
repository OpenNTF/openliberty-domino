/**
 * Copyright © 2018-2020 Jesse Gallagher
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
package org.openntf.openliberty.domino.adminnsf;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.openntf.openliberty.domino.adminnsf.util.AdminNSFUtil;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.runtime.JavaRuntimeProvider;
import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;

import com.ibm.commons.util.StringUtil;
import com.ibm.commons.util.io.StreamUtil;
import com.ibm.commons.util.io.json.JsonException;
import com.ibm.commons.util.io.json.JsonJavaFactory;
import com.ibm.commons.util.io.json.JsonParser;
import com.ibm.domino.napi.c.Os;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesFactory;
import lotus.domino.Session;

import static com.ibm.commons.util.StringUtil.format;

/**
 * Implementation of {@link JavaRuntimeProvider} that reads a Java version from the
 * admin database and either points to the Domino JRE or downloads and refrences
 * a build from AdoptOpenJDK.
 * 
 * @author Jesse Gallagher
 * @since 2.0.0
 */
public class AdminNSFJavaRuntimeProvider implements JavaRuntimeProvider {
	private static final Logger log = OpenLibertyLog.LIBERTY_LOG;
	
	public static final String ITEM_JAVAVERSION = "JavaVersion"; //$NON-NLS-1$
	public static final String ITEM_JAVAJVM = "JavaJVM"; //$NON-NLS-1$
	
	public static final String API_RELEASES = "https://api.github.com/repos/AdoptOpenJDK/openjdk{0}-binaries/releases"; //$NON-NLS-1$

	@Override
	public Path getJavaHome() {
		try {
			return DominoThreadFactory.executor.submit(() -> {
				Session session = NotesFactory.createSession();
				try {
					Database adminNsf = AdminNSFUtil.getAdminDatabase(session);
					Document config = AdminNSFUtil.getConfigurationDocument(adminNsf);
					String javaVersion = config.getItemValueString(ITEM_JAVAVERSION);
					if(StringUtil.isEmpty(javaVersion) || "Domino".equals(javaVersion)) { //$NON-NLS-1$
						return Paths.get(System.getProperty("java.home")); //$NON-NLS-1$
					} else {
						String javaJvm = config.getItemValueString(ITEM_JAVAJVM);
						if(StringUtil.isEmpty(javaJvm)) {
							javaJvm = "HotSpot"; //$NON-NLS-1$
						}
						boolean isJ9 = "OpenJ9".equals(javaJvm); //$NON-NLS-1$
						if(log.isLoggable(Level.FINE)) {
							log.fine(format("Configured to use Java runtime {0} with {1} JVM", javaVersion , javaJvm));
						}	
						
						// Check for an existing JVM
						String execDirName = config.getItemValueString(AdminNSFRuntimeDeployment.ITEM_BASEDIRECTORY);
						Path execDir;
						if(StringUtil.isEmpty(execDirName)) {
							execDir = Paths.get(Os.OSGetExecutableDirectory()).resolve("wlp"); //$NON-NLS-1$
						} else {
							execDir = Paths.get(execDirName);
						}
						
						Path jvmDir = execDir.resolve("jvm").resolve(javaVersion + "-" + javaJvm); //$NON-NLS-1$ //$NON-NLS-2$
						String execName = "java" + (OpenLibertyUtil.IS_WINDOWS ? ".exe" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						if(Files.exists(jvmDir.resolve("bin").resolve(execName))) { //$NON-NLS-1$
							if(log.isLoggable(Level.FINE)) {
								log.info(format("Using already-downloaded runtime at {0}", jvmDir));
							}
							// Assume the whole JVM exists
							return jvmDir;
						}
						
						// Otherwise, look for a release on GitHub
						if("1.8".equals(javaVersion)) { //$NON-NLS-1$
							javaVersion = "8"; //$NON-NLS-1$
						}
						String releasesUrl = StringUtil.format(API_RELEASES, javaVersion);
						if(log.isLoggable(Level.FINE)) {
							log.fine(format("Downloading release list from {0}", releasesUrl));
						}
						@SuppressWarnings("unchecked")
						List<Map<String, Object>> releases = OpenLibertyUtil.download(new URL(releasesUrl), is -> {
							try(Reader r = new InputStreamReader(is)) {
								return (List<Map<String, Object>>)JsonParser.fromJson(JsonJavaFactory.instanceEx2, r);
							} catch (JsonException e) {
								throw new IOException(e);
							}
						});
						
						@SuppressWarnings("unchecked")
						List<Map<String, Object>> assets = releases.stream()
							.filter(release -> !(Boolean)release.get("prerelease")) //$NON-NLS-1$
							.filter(release -> !(Boolean)release.get("draft")) //$NON-NLS-1$
							.filter(release -> {
								String name = StringUtil.toString(release.get("name")); //$NON-NLS-1$
								if(isJ9) {
									return name.contains("_openj9"); //$NON-NLS-1$
								} else {
									return !name.contains("_openj9"); //$NON-NLS-1$
								}
							})
							.findFirst()
							.map(release -> (List<Map<String, Object>>)release.get("assets")) //$NON-NLS-1$
							.orElseThrow(() -> new IllegalStateException("Unable to locate AdoptOpenJDK build in release list"));
						
						// HotSpot:
						//    Linux: https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.6%2B10/OpenJDK11U-jdk_x64_linux_hotspot_11.0.6_10.tar.gz
						//    Windows x64: https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.6%2B10/OpenJDK11U-jdk_x64_windows_hotspot_11.0.6_10.zip
						//    Windows x86: https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.6%2B10/OpenJDK11U-jdk_x86-32_windows_hotspot_11.0.6_10.zip
						// OpenJ9:
						//    Linux: https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.6%2B10_openj9-0.18.1/OpenJDK11U-jdk_x64_linux_openj9_11.0.6_10_openj9-0.18.1.tar.gz
						//    Windows x64: https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.6%2B10_openj9-0.18.1/OpenJDK11U-jdk_x64_windows_openj9_11.0.6_10_openj9-0.18.1.zip
						String qualifier = format("jdk_{0}_{1}", getOsArch(), getOsName()); //$NON-NLS-1$
						Map<String, Object> download = assets.stream()
							.filter(asset -> StringUtil.toString(asset.get("name")).contains("-" + qualifier + "_")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							.filter(asset -> "application/x-compressed-tar".equals(asset.get("content_type")) || "application/zip".equals(asset.get("content_type"))) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
							.findFirst()
							.orElseThrow(() -> new IllegalStateException(format("Unable to find AdoptOpenJDK build for qualifier {0}", qualifier)));
						if(log.isLoggable(Level.INFO)) {
							log.info(format("Downloading AdoptOpenJDK runtime from {0}", download.get("browser_download_url"))); //$NON-NLS-2$
						}
						
						String contentType = (String)download.get("content_type"); //$NON-NLS-1$
						OpenLibertyUtil.download(new URL((String)download.get("browser_download_url")), is -> { //$NON-NLS-1$
							if("application/zip".equals(contentType)) { //$NON-NLS-1$
								try(ZipInputStream zis = new ZipInputStream(is)) {
									extract(zis, jvmDir);
								}
							} else if("application/x-compressed-tar".equals(contentType)) { //$NON-NLS-1$
								try(GZIPInputStream gzis = new GZIPInputStream(is)) {
									try(TarArchiveInputStream tis = new TarArchiveInputStream(gzis)) {
										extract(tis, jvmDir);
									}
								}
							} else {
								throw new IllegalStateException(format("Unsupported content type {0}", contentType));
							}
							return null;
						});
						
						return jvmDir;
					}
				} finally {
					session.recycle();
				}
			}).get();
		} catch (Exception e) {
			if(log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, "Exception while locating Java runtime", e);
				e.printStackTrace(OpenLibertyLog.out);
			}
			throw new RuntimeException(e);
		}
	}
	
	private static void extract(ZipInputStream zis, Path dest) throws IOException {
		ZipEntry entry = zis.getNextEntry();
		while(entry != null) {
			String name = entry.getName();
			
			if(StringUtil.isNotEmpty(name)) {
				// The first directory is a container
				int slashIndex = name.indexOf('/');
				if(slashIndex > -1) {
					name = name.substring(slashIndex+1);
				}
				
				if(StringUtil.isNotEmpty(name)) {
					if(log.isLoggable(Level.FINER)) {
						log.finer(format("Deploying file {0}", name));
					}
					
					Path path = dest.resolve(name);
					if(entry.isDirectory()) {
						Files.createDirectories(path);
					} else {
						try(OutputStream os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
							StreamUtil.copyStream(zis, os);
						}
					}
				}
			}
			
			zis.closeEntry();
			entry = zis.getNextEntry();
		}
	}
	
	private static void extract(TarArchiveInputStream tis, Path dest) throws IOException {
		TarArchiveEntry entry = tis.getNextTarEntry();
		while(entry != null) {
			String name = entry.getName();

			if(StringUtil.isNotEmpty(name)) {
				// The first directory is a container
				int slashIndex = name.indexOf('/');
				if(slashIndex > -1) {
					name = name.substring(slashIndex+1);
				}
				
				if(StringUtil.isNotEmpty(name)) {
					if(log.isLoggable(Level.FINER)) {
						log.finer(format("Deploying file {0}", name));
					}
					
					Path path = dest.resolve(name);
					if(entry.isDirectory()) {
						Files.createDirectories(path);
					} else {
						try(OutputStream os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
							StreamUtil.copyStream(tis, os);
						}
					}
				}
			}
			
			entry = tis.getNextTarEntry();
		}
	}
	
	private static String getOsName() {
		return OpenLibertyUtil.IS_LINUX ? "linux" : "windows"; //$NON-NLS-1$ //$NON-NLS-2$
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
