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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.runtime.Messages;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.commons.apache.tar.TarArchiveEntry;
import org.openntf.openliberty.domino.util.commons.apache.tar.TarArchiveInputStream;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;
import org.openntf.openliberty.domino.util.json.parser.JSONParser;
import org.openntf.openliberty.domino.util.json.parser.ParseException;

public abstract class AbstractDownloadingJavaRuntimeProvider implements JavaRuntimeProvider {
	private static final Logger log = OpenLibertyLog.instance.log;
	
	@SuppressWarnings("unchecked")
	protected static List<Map<String, Object>> fetchGitHubReleasesList(String providerName, String releasesUrl) {
		if(log.isLoggable(Level.FINE)) {
			log.fine(format(Messages.getString("JavaRuntimeProvider.downloadingReleaseListFrom"), providerName, releasesUrl)); //$NON-NLS-1$
		}
		try {
			return OpenLibertyUtil.download(new URL(releasesUrl), (contentType, is) -> {
				try(Reader r = new InputStreamReader(is)) {
					return (List<Map<String, Object>>)new JSONParser().parse(r);
				} catch (ParseException e) {
					throw new IOException(e);
				}
			});
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	protected static void download(String url, Path jvmDir) {
		// TODO consider replacing with NIO filesystem operations, though they don't inherently support .tar.gz
		try {
			OpenLibertyUtil.download(new URL(url), (contentType, is) -> {
				switch(String.valueOf(contentType)) {
				case "application/zip": //$NON-NLS-1$
					try(ZipInputStream zis = new ZipInputStream(is)) {
						extract(zis, jvmDir);
					}
					break;
				case "application/x-compressed-tar": //$NON-NLS-1$
				case "application/gzip": //$NON-NLS-1$
					try(GZIPInputStream gzis = new GZIPInputStream(is)) {
						try(TarArchiveInputStream tis = new TarArchiveInputStream(gzis)) {
							extract(tis, jvmDir);
						}
					}
					break;
				default:
					throw new IllegalStateException(format(Messages.getString("JavaRuntimeProvider.unsupportedContentType"), contentType)); //$NON-NLS-1$
				}
				return null;
			});
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	protected static void markExecutables(Path jvmDir) {
		Path bin = jvmDir.resolve("bin"); //$NON-NLS-1$
		if(Files.isDirectory(bin)) {
			markExecutablesInBinDir(bin);
		}
		Path jreBin = jvmDir.resolve("jre").resolve("bin"); //$NON-NLS-1$ //$NON-NLS-2$
		if(Files.isDirectory(jreBin)) {
			markExecutablesInBinDir(jreBin);
		}
	}
	
	protected static void markExecutablesInBinDir(Path bin) {
		if(bin.getFileSystem().supportedFileAttributeViews().contains("posix")) { //$NON-NLS-1$
			try {
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
			} catch(IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}
	
	protected static void extract(ZipInputStream zis, Path dest) throws IOException {
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
						log.finer(format(Messages.getString("JavaRuntimeProvider.deployingFile"), name)); //$NON-NLS-1$
					}
					
					Path path = dest.resolve(name);
					if(entry.isDirectory()) {
						Files.createDirectories(path);
					} else {
						Files.createDirectories(path.getParent());
						Files.copy(zis, path, StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
			
			zis.closeEntry();
			entry = zis.getNextEntry();
		}
	}
	
	protected static void extract(TarArchiveInputStream tis, Path dest) throws IOException {
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
						log.finer(format(Messages.getString("JavaRuntimeProvider.deployingFile"), name)); //$NON-NLS-1$
					}
					
					Path path = dest.resolve(name);
					if(entry.isDirectory()) {
						Files.createDirectories(path);
					} else {
						Files.createDirectories(path.getParent());
						Files.copy(tis, path, StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
			
			entry = tis.getNextTarEntry();
		}
	}
	
	protected static String getOsName() {
		return OpenLibertyUtil.IS_LINUX ? "linux" : "windows"; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
