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
package org.openntf.openliberty.domino.wlp.notesapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.openntf.openliberty.domino.config.RuntimeConfigurationProvider;
import org.openntf.openliberty.domino.server.wlp.LibertyExtensionDeployer;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.commons.apache.IOUtils;

/**
 * This extension deployer auto-vivifies a bundle that provides LSXBE and NAPI
 * classes to applications.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public class NotesAPIExtension implements LibertyExtensionDeployer {
	public static final String BUNDLE_NAME = "org.openntf.openliberty.wlp.notesapi"; //$NON-NLS-1$
	public static final String URL_CORBA = "https://repo1.maven.org/maven2/org/glassfish/corba/glassfish-corba-omgapi/4.2.1/glassfish-corba-omgapi-4.2.1.jar"; //$NON-NLS-1$

	@Override
	public String getShortName() {
		return "dominoApi"; //$NON-NLS-1$
	}

	@Override
	public String getFeatureVersion() {
		return "1.0"; //$NON-NLS-1$
	}

	@Override
	public InputStream getEsaData() {
		// Build our bundle if we need it
		RuntimeConfigurationProvider config = OpenLibertyUtil.findRequiredExtension(RuntimeConfigurationProvider.class);
		Path wlp = config.getBaseDirectory();
		String dominoVersion = config.getDominoVersion();
		
		Path cacheDir = wlp.resolve("work").resolve(getClass().getName()); //$NON-NLS-1$
		if(!Files.isDirectory(cacheDir)) {
			try {
				Files.createDirectories(cacheDir);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		Path out = cacheDir.resolve(dominoVersion + ".esa"); //$NON-NLS-1$
		if(!Files.isRegularFile(out)) {
			// Make a new bundle for our version
			
			try(
				OutputStream os = Files.newOutputStream(out, StandardOpenOption.CREATE);
				ZipOutputStream zos = new ZipOutputStream(os, StandardCharsets.UTF_8)
			) {
				// Build our ESA, which will include SUBSYSTEM.MF and the bundle
				
				List<Path> embeds = getEmbeds();
				
				Collection<String> packages = new LinkedHashSet<>();
				findNotesJar().ifPresent(jar -> {
					packages.addAll(listPackages(jar));
				});
				findIbmNapi().ifPresent(jar -> {
					packages.addAll(listPackages(jar));
				});
				
				buildSubsystemMf(dominoVersion, packages, zos);
				buildApiBundle(dominoVersion, packages, embeds, zos);
				
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		
		try {
			return Files.newInputStream(out);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void buildSubsystemMf(String dominoVersion, Collection<String> packages, ZipOutputStream zos) throws IOException {
		Manifest subsystem;
		try(InputStream is = getClass().getResourceAsStream("/subsystem-template.mf")) { //$NON-NLS-1$
			subsystem = new Manifest(is);
		}
		
		Attributes attrs = subsystem.getMainAttributes();
		attrs.putValue("IBM-API-Package", //$NON-NLS-1$
			packages.stream()
				.map(p -> p + "; type=spec") //$NON-NLS-1$
				.collect(Collectors.joining(",")) //$NON-NLS-1$
		);
		attrs.putValue("Subsystem-Name", getShortName()); //$NON-NLS-1$
		String featureName = getShortName() + "-" + getFeatureVersion(); //$NON-NLS-1$
		attrs.putValue("IBM-ShortName", featureName); //$NON-NLS-1$
		attrs.putValue("Subsystem-SymbolicName", featureName + ";visibility:=public"); //$NON-NLS-1$ //$NON-NLS-2$
		attrs.putValue("Subsystem-Version", dominoVersion + ".0.0"); //$NON-NLS-1$ //$NON-NLS-2$
		
		String content = MessageFormat.format("{0};version=\"[{1}.0.0,{1}.0.0]\"", BUNDLE_NAME, dominoVersion); //$NON-NLS-1$
		attrs.putValue("Subsystem-Content", content); //$NON-NLS-1$
		
		zos.putNextEntry(new ZipEntry("OSGI-INF/SUBSYSTEM.MF")); //$NON-NLS-1$
		subsystem.write(zos);
		
	}
	
	private void buildApiBundle(String dominoVersion, Collection<String> packages, Collection<Path> embeds, ZipOutputStream esa) throws IOException {
		// This will be a shell bundle that in turn embeds the API JARs
		esa.putNextEntry(new ZipEntry(BUNDLE_NAME + "_" + dominoVersion + ".0.0.jar")); //$NON-NLS-1$ //$NON-NLS-2$
		
		// Build the embedded JAR contents
		try(ZipOutputStream zos = new ZipOutputStream(esa, StandardCharsets.UTF_8)) {
			Manifest manifest;
			try(InputStream is = getClass().getResourceAsStream("/manifest-template.mf")) { //$NON-NLS-1$
				manifest = new Manifest(is);
			}
			Attributes attrs = manifest.getMainAttributes();
			attrs.putValue("Bundle-SymbolicName", BUNDLE_NAME); //$NON-NLS-1$
			attrs.putValue("Automatic-Module-Name", BUNDLE_NAME); //$NON-NLS-1$
			attrs.putValue("Bundle-Name", BUNDLE_NAME); //$NON-NLS-1$
			attrs.putValue("Bundle-Version", dominoVersion + ".0.0"); //$NON-NLS-1$ //$NON-NLS-2$
			
			String embedNames = embeds.stream().map(Path::getFileName).map(Path::toString).collect(Collectors.joining(",")); //$NON-NLS-1$
			embedNames += ",corba.jar"; //$NON-NLS-1$
			attrs.putValue("Bundle-ClassPath", embedNames); //$NON-NLS-1$
			
			String exports = String.join(",", packages); //$NON-NLS-1$
			attrs.putValue("Export-Package", exports); //$NON-NLS-1$
			
			zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF")); //$NON-NLS-1$
			manifest.write(zos);
			
			for(Path jar : embeds) {
				zos.putNextEntry(new ZipEntry(jar.getFileName().toString()));
				Files.copy(jar, zos);
			}
			
			// Copy in CORBA to support Notes.jar in Java > 8
			OpenLibertyUtil.download(new URL(URL_CORBA), is -> {
				zos.putNextEntry(new ZipEntry("corba.jar")); //$NON-NLS-1$
				IOUtils.copy(is, zos);
				return null;
			});
		}
	}
	
	private List<Path> getEmbeds() {
		return Stream.of(
				findNotesJar(),
				findIbmCommons(),
				findIbmNapi()
			)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.collect(Collectors.toList());
	}
	
	private Optional<Path> findNotesJar() {
		Path runningJava = Paths.get(System.getProperty("java.home")); //$NON-NLS-1$
		Path notesJar = runningJava.resolve("lib").resolve("ext").resolve("Notes.jar"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		if(Files.isRegularFile(notesJar)) {
			return Optional.of(notesJar);
		} else {
			return Optional.empty();
		}
	}
	
	private Optional<Path> findIbmCommons() {
		return findOsgiEmbed("com.ibm.commons", "lwpd.commons.jar"); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	private Optional<Path> findIbmNapi() {
		return findOsgiEmbed("com.ibm.domino.napi", "lwpd.domino.napi.jar"); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	private Optional<Path> findOsgiEmbed(String bundleName, String embedName) {
		RuntimeConfigurationProvider config = OpenLibertyUtil.findRequiredExtension(RuntimeConfigurationProvider.class);
		Path domino = config.getDominoProgramDirectory();
		Path osgiSharedPath = domino.resolve("osgi").resolve("shared").resolve("eclipse").resolve("plugins"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		if(Files.isDirectory(osgiSharedPath)) {
			try {
				return Files.find(osgiSharedPath, 1, (path, attr) -> path.getFileName().toString().startsWith(bundleName + '_'))
					.findFirst()
					.flatMap(p -> {
						Path jar = p.resolve(embedName);
						if(Files.isRegularFile(jar)) {
							return Optional.of(jar);
						} else {
							return Optional.empty();
						}
					});
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		} else {
			return Optional.empty();
		}
	}
	
	private Collection<String> listPackages(Path jar) {
		Collection<String> result = new LinkedHashSet<>();
		try(
			InputStream is = Files.newInputStream(jar);
			ZipInputStream zis = new ZipInputStream(is, StandardCharsets.UTF_8)
		) {
			for(ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
				String name = entry.getName();
				if(name.endsWith(".class")) { //$NON-NLS-1$
					int last = name.lastIndexOf('/');
					if(last > -1) {
						String dir = name.substring(0, last);
						result.add(dir.replace('/', '.'));
					}
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return result;
	}
}
