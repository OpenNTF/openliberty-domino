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
package org.openntf.openliberty.domino.adminnsf;

import static com.ibm.commons.util.StringUtil.format;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.openntf.openliberty.domino.adminnsf.config.AdminNSFProperties;
import org.openntf.openliberty.domino.adminnsf.util.AdminNSFUtil;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.runtime.RuntimeDeploymentTask;

import com.ibm.commons.util.StringUtil;
import com.ibm.commons.util.io.StreamUtil;
import com.ibm.domino.napi.c.Os;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.Session;

public class AdminNSFRuntimeDeployment implements RuntimeDeploymentTask {
	private static final Logger log = OpenLibertyLog.LIBERTY_LOG;
	
	public static final String ITEM_BASEDIRECTORY = "BaseDirectory";
	public static final String ITEM_VERSION = "Version";
	public static final String ITEM_ARTIFACT = "Artfiact";
	public static final String ITEM_MAVENREPO = "MavenRepo";

	@Override
	public Path call() throws IOException {
		try {
			Session session = NotesFactory.createSession();
			try {
				Database adminNsf = AdminNSFUtil.getAdminDatabase(session);
				Document config = AdminNSFUtil.getConfigurationDocument(adminNsf);
				String execDirName = config.getItemValueString(ITEM_BASEDIRECTORY);
				Path execDir;
				if(StringUtil.isEmpty(execDirName)) {
					execDir = Paths.get(Os.OSGetExecutableDirectory()).resolve("wlp");
				} else {
					execDir = Paths.get(execDirName);
				}
				
				String version = config.getItemValueString(ITEM_VERSION);
				if(StringUtil.isEmpty(version)) {
					version = AdminNSFProperties.instance.getDefaultVersion();
				}
				
				Path wlp = execDir.resolve(format("wlp-{0}", version));
				
				if(!Files.isDirectory(wlp)) {
					// If it doesn't yet exist, download and deploy a new runtime
					if(log.isLoggable(Level.INFO)) {
						log.info("Deploying new runtime");
					}
					
					String artifact = config.getItemValueString(ITEM_ARTIFACT);
					if(StringUtil.isEmpty(artifact)) {
						artifact = AdminNSFProperties.instance.getDefaultArtifact();
					}
					
					Path downloadDir = execDir.resolve("download");
					Path wlpPackage = downloadDir.resolve(buildZipName(artifact, version));
					if(!Files.exists(wlpPackage)) {
						Files.createDirectories(downloadDir);
						
						// Download from Maven
						String mavenRepo = config.getItemValueString(ITEM_MAVENREPO);
						if(StringUtil.isEmpty(mavenRepo)) {
							mavenRepo = AdminNSFProperties.instance.getDefaultMavenRepo();
						}
						URL url = buildDownloadURL(mavenRepo, artifact, version);
						if(log.isLoggable(Level.INFO)) {
							log.info("Downloading runtime from " + url);
						}
						if(log.isLoggable(Level.INFO)) {
							log.info("Storing runtime download at " + wlpPackage);
						}
						try(OutputStream os = Files.newOutputStream(wlpPackage, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
							// Domino defaults to using old protocols - bump this up for our needs here so the connection succeeds
							String protocols = StringUtil.toString(System.getProperty("https.protocols"));
							try {
								System.setProperty("https.protocols", "TLSv1.2");
								HttpURLConnection conn = (HttpURLConnection)url.openConnection();
								int responseCode = conn.getResponseCode();
								try {
									if(responseCode != HttpURLConnection.HTTP_OK) {
										throw new IOException("Received unexpected response code " + responseCode + " from URL " + url);
									}
									try(InputStream is = conn.getInputStream()) {
										StreamUtil.copyStream(is, os);
									}
								} finally {
									conn.disconnect();
								}
							} finally {
								System.setProperty("https.protocols", protocols);
							}
						}
					}
						
					// Now extract the ZIP
					Files.createDirectories(wlp);
					try(InputStream is = Files.newInputStream(wlpPackage)) {
						try(ZipInputStream zis = new ZipInputStream(is)) {
							ZipEntry entry = zis.getNextEntry();
							while(entry != null) {
								String name = entry.getName();
								if(name.startsWith("wlp/")) {
									// Remove the standard prefix
									name = name.substring(4);
								}
								
								if(StringUtil.isNotEmpty(name)) {
									if(log.isLoggable(Level.FINE)) {
										log.fine(format("Deploying file {0}", name));
									}
									
									Path path = wlp.resolve(name);
									if(entry.isDirectory()) {
										Files.createDirectories(path);
									} else {
										try(OutputStream os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
											StreamUtil.copyStream(zis, os);
										}
									}
								}
								
								zis.closeEntry();
								entry = zis.getNextEntry();
							}
						}
					}
				}
				
				return wlp;
			} finally {
				session.recycle();
			}
		} catch(NotesException e) {
			throw new IOException(e);
		}
	}

	// *******************************************************************************
	// * Internal utility methods
	// *******************************************************************************
	
	private static URL buildDownloadURL(String mavenRepo, String artifact, String version) throws MalformedURLException {
		String base = mavenRepo;
		if(!base.endsWith("/")) {
			base += base;
		}
		URL url = new URL(base);
		
		String artifactPart = getArtifactId(artifact);
		String groupPart = artifact.substring(0, artifact.length()-artifactPart.length()-1).replace('.', '/');
		url = new URL(url, groupPart + '/' + artifactPart + '/' + version + '/');
		
		String zipName = buildZipName(artifact, version);
		url = new URL(url, zipName);
		
		return url;
	}
	
	private static String buildZipName(String artifact, String version) {
		String artifactId  = getArtifactId(artifact);
		return artifactId + '-' + version + ".zip";
	}
	
	private static String getArtifactId(String artifact) {
		int colonIndex = artifact.indexOf(':');
		if(colonIndex < 1 || colonIndex == artifact.length()+1) {
			throw new IllegalArgumentException("Illegal Maven artifact ID: " + artifact);
		}
		return artifact.substring(colonIndex+1);
	}
}
