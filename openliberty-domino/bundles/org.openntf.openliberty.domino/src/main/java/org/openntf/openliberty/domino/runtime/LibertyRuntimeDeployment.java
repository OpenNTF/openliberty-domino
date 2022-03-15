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
package org.openntf.openliberty.domino.runtime;

import static java.text.MessageFormat.format;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.openntf.openliberty.domino.config.RuntimeConfigurationProvider;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.server.ServerConfiguration;
import org.openntf.openliberty.domino.server.wlp.LibertyServerConfiguration;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;

public class LibertyRuntimeDeployment implements RuntimeDeploymentTask<LibertyServerConfiguration> {
	private static final Logger log = OpenLibertyLog.instance.log;
	
	public static final String DEFAULT_VERSION = "21.0.0.2"; //$NON-NLS-1$
	public static final String DEFAULT_ARTIFACT = "io.openliberty:openliberty-runtime"; //$NON-NLS-1$
	public static final String DEFAULT_MAVENREPO = "https://repo.maven.apache.org/maven2/"; //$NON-NLS-1$
	
	public static final String URL_CORBA = "https://repo1.maven.org/maven2/org/glassfish/corba/glassfish-corba-omgapi/4.2.1/glassfish-corba-omgapi-4.2.1.jar"; //$NON-NLS-1$

	@Override
	public boolean canDeploy(ServerConfiguration serverConfig) {
		return serverConfig instanceof LibertyServerConfiguration;
	}
	
	@Override
	public Path deploy(LibertyServerConfiguration config) throws IOException {
		RuntimeConfigurationProvider runtimeConfig = OpenLibertyUtil.findRequiredExtension(RuntimeConfigurationProvider.class);
		Path execDir = runtimeConfig.getBaseDirectory();
		
		String version = config.getLibertyVersion();
		if(StringUtil.isEmpty(version)) {
			version = DEFAULT_VERSION;
		}
		
		Path wlp = execDir.resolve(format("wlp-{0}", version)); //$NON-NLS-1$
		
		if(!Files.isDirectory(wlp)) {
			// If it doesn't yet exist, download and deploy a new runtime
			if(log.isLoggable(Level.INFO)) {
				log.info(Messages.getString("StandardRuntimeDeployment.deployingNewRuntime")); //$NON-NLS-1$
			}
			
			String artifact = config.getLibertyArtifact();
			if(StringUtil.isEmpty(artifact)) {
				artifact = DEFAULT_ARTIFACT;
			}
			
			Path downloadDir = execDir.resolve("download"); //$NON-NLS-1$
			Path wlpPackage = downloadDir.resolve(buildZipName(artifact, version));
			if(!Files.exists(wlpPackage)) {
				Files.createDirectories(downloadDir);
				
				// Download from Maven
				String mavenRepo = config.getLibertyMavenRepo();
				if(StringUtil.isEmpty(mavenRepo)) {
					mavenRepo = DEFAULT_MAVENREPO;
				}
				URL url = buildDownloadURL(mavenRepo, artifact, version);
				if(log.isLoggable(Level.INFO)) {
					log.info(format(Messages.getString("StandardRuntimeDeployment.downloadingRuntimeFrom"), url)); //$NON-NLS-1$
				}
				if(log.isLoggable(Level.INFO)) {
					log.info(format(Messages.getString("StandardRuntimeDeployment.storingRuntimeAt"), wlpPackage)); //$NON-NLS-1$
				}
				OpenLibertyUtil.download(url, is -> {
					Files.copy(is, wlpPackage, StandardCopyOption.REPLACE_EXISTING);
					return null;
				});
			}
				
			// Now extract the ZIP
			Files.createDirectories(wlp);
			try(InputStream is = Files.newInputStream(wlpPackage)) {
				try(ZipInputStream zis = new ZipInputStream(is)) {
					ZipEntry entry = zis.getNextEntry();
					while(entry != null) {
						String name = entry.getName();
						if(name.startsWith("wlp/")) { //$NON-NLS-1$
							// Remove the standard prefix
							name = name.substring(4);
						}
						
						if(StringUtil.isNotEmpty(name)) {
							if(log.isLoggable(Level.FINER)) {
								log.finer(format(Messages.getString("StandardRuntimeDeployment.deployingFile"), name)); //$NON-NLS-1$
							}
							
							Path path = wlp.resolve(name);
							if(entry.isDirectory()) {
								Files.createDirectories(path);
							} else {
								Files.copy(zis, path, StandardCopyOption.REPLACE_EXISTING);
							}
						}
						
						zis.closeEntry();
						entry = zis.getNextEntry();
					}
				}
			}
		}
		
		verifyRuntime(wlp);
		
		return wlp;
	}

	// *******************************************************************************
	// * Internal utility methods
	// *******************************************************************************
	
	private static URL buildDownloadURL(String mavenRepo, String artifact, String version) throws MalformedURLException {
		String base = mavenRepo;
		if(!base.endsWith("/")) { //$NON-NLS-1$
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
		return artifactId + '-' + version + ".zip"; //$NON-NLS-1$
	}
	
	private static String getArtifactId(String artifact) {
		int colonIndex = artifact.indexOf(':');
		if(colonIndex < 1 || colonIndex == artifact.length()+1) {
			throw new IllegalArgumentException(format(Messages.getString("StandardRuntimeDeployment.illegalArtifactId"), artifact)); //$NON-NLS-1$
		}
		return artifact.substring(colonIndex+1);
	}
	
	private void verifyRuntime(Path wlp) throws IOException {
		// TODO handle more than execution bits
		if(!OpenLibertyUtil.IS_WINDOWS) {
			Path exec = wlp.resolve("bin").resolve("server"); //$NON-NLS-1$ //$NON-NLS-2$
			if(!Files.isExecutable(exec)) {
				Set<PosixFilePermission> perm = EnumSet.copyOf(Files.getPosixFilePermissions(exec));
				perm.add(PosixFilePermission.OWNER_EXECUTE);
				Files.setPosixFilePermissions(exec, perm);
			}
		}
	}
}
