/*
 * Copyright © 2018-2021 Jesse Gallagher
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

import static java.text.MessageFormat.format;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.openntf.openliberty.domino.adminnsf.util.AdminNSFUtil;
import org.openntf.openliberty.domino.adminnsf.util.json.parser.JSONParser;
import org.openntf.openliberty.domino.adminnsf.util.json.parser.ParseException;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.runtime.JavaRuntimeProvider;
import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.commons.apache.tar.TarArchiveEntry;
import org.openntf.openliberty.domino.util.commons.apache.tar.TarArchiveInputStream;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesFactory;
import lotus.domino.Session;

/**
 * Implementation of {@link JavaRuntimeProvider} that reads a Java version from the
 * admin database and either points to the Domino JRE or downloads and refrences
 * a build from AdoptOpenJDK.
 * 
 * @author Jesse Gallagher
 * @since 2.0.0
 */
public class AdminNSFJavaRuntimeProvider implements JavaRuntimeProvider {
	private static final Logger log = OpenLibertyLog.instance.log;
	
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
							log.fine(format(Messages.getString("AdminNSFJavaRuntimeProvider.configuredJavaRuntimeWithJVM"), javaVersion , javaJvm)); //$NON-NLS-1$
						}	
						
						// Check for an existing JVM
						String execDirName = config.getItemValueString(AdminNSFRuntimeDeployment.ITEM_BASEDIRECTORY);
						Path execDir;
						if(StringUtil.isEmpty(execDirName)) {
							execDir = Paths.get(OpenLibertyUtil.getDominoProgramDirectory()).resolve("wlp"); //$NON-NLS-1$
						} else {
							execDir = Paths.get(execDirName);
						}
						
						Path jvmDir = execDir.resolve("jvm").resolve(javaVersion + "-" + javaJvm); //$NON-NLS-1$ //$NON-NLS-2$
						String execName = "java" + (OpenLibertyUtil.IS_WINDOWS ? ".exe" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						if(Files.exists(jvmDir.resolve("bin").resolve(execName))) { //$NON-NLS-1$
							if(log.isLoggable(Level.FINE)) {
								log.info(format(Messages.getString("AdminNSFJavaRuntimeProvider.usingDownloadedRuntime"), jvmDir)); //$NON-NLS-1$
							}
							// Assume the whole JVM exists
							return jvmDir;
						}
						
						// Otherwise, look for a release on GitHub
						if("1.8".equals(javaVersion)) { //$NON-NLS-1$
							javaVersion = "8"; //$NON-NLS-1$
						}
						String releasesUrl = format(API_RELEASES, javaVersion);
						if(log.isLoggable(Level.FINE)) {
							log.fine(format(Messages.getString("AdminNSFJavaRuntimeProvider.downloadingReleaseListFrom"), releasesUrl)); //$NON-NLS-1$
						}
						@SuppressWarnings("unchecked")
						List<Map<String, Object>> releases = OpenLibertyUtil.download(new URL(releasesUrl), is -> {
							try(Reader r = new InputStreamReader(is)) {
								return (List<Map<String, Object>>)new JSONParser().parse(r);
							} catch (ParseException e) {
								throw new IOException(e);
							}
						});
						
						// Find any applicable releases, in order, as some releases may contain only certain platforms
						List<Map<String, Object>> validReleases = releases.stream()
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
							.filter(release -> release.containsKey("assets")) //$NON-NLS-1$
							.collect(Collectors.toList());
						if(validReleases.isEmpty()) {
							throw new IllegalStateException(Messages.getString("AdminNSFJavaRuntimeProvider.unableToLocateAdoptOpenJDKBuild")); //$NON-NLS-1$
						}
						
						// HotSpot:
						//    Linux: https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.6%2B10/OpenJDK11U-jdk_x64_linux_hotspot_11.0.6_10.tar.gz
						//    Windows x64: https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.6%2B10/OpenJDK11U-jdk_x64_windows_hotspot_11.0.6_10.zip
						//    Windows x86: https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.6%2B10/OpenJDK11U-jdk_x86-32_windows_hotspot_11.0.6_10.zip
						// OpenJ9:
						//    Linux: https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.6%2B10_openj9-0.18.1/OpenJDK11U-jdk_x64_linux_openj9_11.0.6_10_openj9-0.18.1.tar.gz
						//    Windows x64: https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.6%2B10_openj9-0.18.1/OpenJDK11U-jdk_x64_windows_openj9_11.0.6_10_openj9-0.18.1.zip
						String qualifier = format("jdk_{0}_{1}", getOsArch(), getOsName()); //$NON-NLS-1$
						@SuppressWarnings("unchecked")
						Map<String, Object> download = validReleases.stream()
							.map(release -> (List<Map<String, Object>>)release.get("assets")) //$NON-NLS-1$
							.flatMap(Collection::stream)
							.filter(asset -> !StringUtil.toString(asset.get("name")).contains("-testimage")) //$NON-NLS-1$ //$NON-NLS-2$
							.filter(asset -> !StringUtil.toString(asset.get("name")).contains("-debugimage")) //$NON-NLS-1$ //$NON-NLS-2$
							.filter(asset -> StringUtil.toString(asset.get("name")).contains("-" + qualifier + "_")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							.filter(asset -> "application/x-compressed-tar".equals(asset.get("content_type")) || "application/zip".equals(asset.get("content_type"))) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
							.findFirst()
							.orElseThrow(() -> new IllegalStateException(format(Messages.getString("AdminNSFJavaRuntimeProvider.unableToFindAdoptOpenJDKBuildFor"), qualifier))); //$NON-NLS-1$
						if(log.isLoggable(Level.INFO)) {
							log.info(format(Messages.getString("AdminNSFJavaRuntimeProvider.downloadingAdoptOpenJDKFrom"), download.get("browser_download_url")));  //$NON-NLS-1$//$NON-NLS-2$
						}
						
						String contentType = (String)download.get("content_type"); //$NON-NLS-1$
						// TODO consider replacing with NIO filesystem operations, though they don't inherently support .tar.gz
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
								throw new IllegalStateException(format(Messages.getString("AdminNSFJavaRuntimeProvider.unsupportedContentType"), contentType)); //$NON-NLS-1$
							}
							return null;
						});
						
						// Mark files in the bin directory as executable
						Path bin = jvmDir.resolve("bin"); //$NON-NLS-1$
						if(Files.isDirectory(bin)) {
							if(bin.getFileSystem().supportedFileAttributeViews().contains("posix")) { //$NON-NLS-1$
								Files.list(bin)
									.filter(Files::isRegularFile)
									.forEach(p -> {
										try {
											Set<PosixFilePermission> perms = EnumSet.copyOf(Files.getPosixFilePermissions(p));
											perms.add(PosixFilePermission.OWNER_EXECUTE);
											perms.add(PosixFilePermission.GROUP_EXECUTE);
											perms.add(PosixFilePermission.OTHERS_EXECUTE);
											Files.setPosixFilePermissions(p, perms);
										} catch (IOException e) {
											throw new RuntimeException(e);
										}
									});
							}
						}
						
						return jvmDir;
					}
				} finally {
					session.recycle();
				}
			}).get();
		} catch (Exception e) {
			if(log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, Messages.getString("AdminNSFJavaRuntimeProvider.exceptionLocatingRuntime"), e); //$NON-NLS-1$
				e.printStackTrace(OpenLibertyLog.instance.out);
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
						log.finer(format(Messages.getString("AdminNSFJavaRuntimeProvider.deployingFile"), name)); //$NON-NLS-1$
					}
					
					Path path = dest.resolve(name);
					if(entry.isDirectory()) {
						Files.createDirectories(path);
					} else {
						Files.copy(zis, path, StandardCopyOption.REPLACE_EXISTING);
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
						log.finer(format(Messages.getString("AdminNSFJavaRuntimeProvider.deployingFile"), name)); //$NON-NLS-1$
					}
					
					Path path = dest.resolve(name);
					if(entry.isDirectory()) {
						Files.createDirectories(path);
					} else {
						Files.copy(tis, path, StandardCopyOption.REPLACE_EXISTING);
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
