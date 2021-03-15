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

import org.openntf.openliberty.domino.config.RuntimeConfigurationProvider;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.runtime.Messages;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.commons.apache.tar.TarArchiveEntry;
import org.openntf.openliberty.domino.util.commons.apache.tar.TarArchiveInputStream;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;
import org.openntf.openliberty.domino.util.json.parser.JSONParser;
import org.openntf.openliberty.domino.util.json.parser.ParseException;

/**
 * Implementation of {@link JavaRuntimeProvider} that downloads and references
 * a build from AdoptOpenJDK.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public class AdoptOpenJDKJavaRuntimeProvider implements JavaRuntimeProvider {
	private static final Logger log = OpenLibertyLog.instance.log;
	
	public static final String API_RELEASES = "https://api.github.com/repos/AdoptOpenJDK/openjdk{0}-binaries/releases"; //$NON-NLS-1$
	
	public static final String TYPE_HOTSPOT = "HotSpot";
	public static final String TYPE_OPENJ9 = "OpenJ9";
	
	@Override
	public boolean canProvide(JVMIdentifier identifier) {
		return TYPE_HOTSPOT.equals(identifier.getType()) || TYPE_OPENJ9.equals(identifier.getType());
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Path getJavaHome(JVMIdentifier identifier) {
		String version = identifier.getVersion();
		String javaJvm = identifier.getType();
		if(StringUtil.isEmpty(javaJvm)) {
			javaJvm = "HotSpot"; //$NON-NLS-1$
		}
		boolean isJ9 = "OpenJ9".equals(javaJvm); //$NON-NLS-1$
		if(log.isLoggable(Level.FINE)) {
			log.fine(format(Messages.getString("AdoptOpenJDKJavaRuntimeProvider.configuredJavaRuntimeWithJVM"), version , javaJvm)); //$NON-NLS-1$
		}	
		
		// Check for an existing JVM
		RuntimeConfigurationProvider config = OpenLibertyUtil.findRequiredExtension(RuntimeConfigurationProvider.class);
		Path execDir = config.getBaseDirectory();
		
		Path jvmDir = execDir.resolve("jvm").resolve(version + "-" + javaJvm); //$NON-NLS-1$ //$NON-NLS-2$
		String execName = "java" + (OpenLibertyUtil.IS_WINDOWS ? ".exe" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		if(Files.exists(jvmDir.resolve("bin").resolve(execName))) { //$NON-NLS-1$
			if(log.isLoggable(Level.FINE)) {
				log.info(format(Messages.getString("AdoptOpenJDKJavaRuntimeProvider.usingDownloadedRuntime"), jvmDir)); //$NON-NLS-1$
			}
			// Assume the whole JVM exists
			return jvmDir;
		}
		
		// Otherwise, look for a release on GitHub
		if("1.8".equals(version)) { //$NON-NLS-1$
			version = "8"; //$NON-NLS-1$
		}
		String releasesUrl = format(API_RELEASES, version);
		if(log.isLoggable(Level.FINE)) {
			log.fine(format(Messages.getString("AdoptOpenJDKJavaRuntimeProvider.downloadingReleaseListFrom"), releasesUrl)); //$NON-NLS-1$
		}
		List<Map<String, Object>> releases;
		try {
			releases = OpenLibertyUtil.download(new URL(releasesUrl), is -> {
				try(Reader r = new InputStreamReader(is)) {
					return (List<Map<String, Object>>)new JSONParser().parse(r);
				} catch (ParseException e) {
					throw new IOException(e);
				}
			});
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
		
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
			throw new IllegalStateException(Messages.getString("AdoptOpenJDKJavaRuntimeProvider.unableToLocateAdoptOpenJDKBuild")); //$NON-NLS-1$
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
			.orElseThrow(() -> new IllegalStateException(format(Messages.getString("AdoptOpenJDKJavaRuntimeProvider.unableToFindAdoptOpenJDKBuildFor"), qualifier))); //$NON-NLS-1$
		if(log.isLoggable(Level.INFO)) {
			log.info(format(Messages.getString("AdoptOpenJDKJavaRuntimeProvider.downloadingAdoptOpenJDKFrom"), download.get("browser_download_url")));  //$NON-NLS-1$//$NON-NLS-2$
		}
		
		String contentType = (String)download.get("content_type"); //$NON-NLS-1$
		// TODO consider replacing with NIO filesystem operations, though they don't inherently support .tar.gz
		try {
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
					throw new IllegalStateException(format(Messages.getString("AdoptOpenJDKJavaRuntimeProvider.unsupportedContentType"), contentType)); //$NON-NLS-1$
				}
				return null;
			});
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
		
		// Mark files in the bin directory as executable
		Path bin = jvmDir.resolve("bin"); //$NON-NLS-1$
		if(Files.isDirectory(bin)) {
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
		
		return jvmDir;
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
						log.finer(format(Messages.getString("AdoptOpenJDKJavaRuntimeProvider.deployingFile"), name)); //$NON-NLS-1$
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
						log.finer(format(Messages.getString("AdoptOpenJDKJavaRuntimeProvider.deployingFile"), name)); //$NON-NLS-1$
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
