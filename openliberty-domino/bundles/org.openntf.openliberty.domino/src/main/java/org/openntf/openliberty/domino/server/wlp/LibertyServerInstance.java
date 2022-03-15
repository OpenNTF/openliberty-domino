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
package org.openntf.openliberty.domino.server.wlp;

import static java.text.MessageFormat.format;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.openntf.openliberty.domino.config.RuntimeConfigurationProvider;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.runtime.Messages;
import org.openntf.openliberty.domino.runtime.RuntimeDeploymentTask;
import org.openntf.openliberty.domino.server.AbstractJavaServerInstance;
import org.openntf.openliberty.domino.server.ServerConfiguration;
import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.LogFileWatcher;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.StreamRedirector;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;
import org.openntf.openliberty.domino.util.xml.XMLDocument;
import org.openntf.openliberty.domino.util.xml.XMLNode;
import org.openntf.openliberty.domino.util.xml.XMLNodeList;
import org.xml.sax.SAXException;

public class LibertyServerInstance extends AbstractJavaServerInstance<LibertyServerConfiguration> {
	private static final Logger log = OpenLibertyLog.getLog();

	private static final String serverFile;
	static {
		if(OpenLibertyUtil.IS_WINDOWS) {
			serverFile = "server.bat"; //$NON-NLS-1$
		} else {
			serverFile = "server"; //$NON-NLS-1$
		}
	}
	
	private final String serverName;
	private final LibertyServerConfiguration config;
	
	private final Path dominoProgramDirectory = Paths.get(OpenLibertyUtil.getDominoProgramDirectory());
	private final RuntimeConfigurationProvider runtimeConfig = OpenLibertyUtil.findRequiredExtension(RuntimeConfigurationProvider.class);
	
	private LogFileWatcher logWatcher;
	private Set<Process> subprocesses = Collections.synchronizedSet(new HashSet<>());
	
	/**
	 * Caches server configurations to WLP root paths.
	 * @since 3.0.0
	 */
	private static final Map<LibertyServerConfiguration, Path> wlpRoots = Collections.synchronizedMap(new HashMap<>());
	
	public LibertyServerInstance(String serverName, LibertyServerConfiguration config) {
		this.serverName = serverName;
		this.config = config;
	}
	
	@Override
	public String getServerName() {
		return this.serverName;
	}

	@Override
	public LibertyServerConfiguration getConfiguration() {
		return config;
	}
	
	@Override
	public void deploy() {
		try {
			LibertyServerConfiguration serverConfig = getConfiguration();
			String serverXml = serverConfig.getServerXml().getXml();
			String serverEnv = serverConfig.getServerEnv();
			String jvmOptions = serverConfig.getJvmOptions();
			String bootstrapProperties = serverConfig.getBootstrapProperties();
			Collection<Path> additionalZips = serverConfig.getAdditionalZips();
			
			Path wlp = getWlpRoot();
			if(log.isLoggable(Level.INFO)) {
				log.info(format(Messages.getString("OpenLibertyRuntime.usingRuntimeAt"), wlp)); //$NON-NLS-1$
			}
			deployExtensions(wlp);
			
			Path javaHome =  getJavaHome();
			
			if(!serverExists(wlp, serverName)) {
				sendCommand(wlp, javaHome, "create", serverName).waitFor(); //$NON-NLS-1$
			}
			if(StringUtil.isNotEmpty(serverXml)) {
				deployServerXml(serverXml);
			}
			if(StringUtil.isNotEmpty(serverEnv)) {
				deployServerEnv(serverEnv);
			}
			for(Path zip : additionalZips) {
				deployAdditionalZip(zip);
			}
			if(StringUtil.isNotEmpty(jvmOptions)) {
				deployJvmOptions(jvmOptions);
			}
			if(StringUtil.isNotEmpty(bootstrapProperties)) {
				deployBootstrapProperties(bootstrapProperties);
			}
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public synchronized void watchLogs(PrintStream out) {
		Path path = getWlpRoot();
		Path logs = path.resolve("usr").resolve("servers").resolve(this.serverName).resolve("logs"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		Path consoleLog = logs.resolve("console.log"); //$NON-NLS-1$
		if(this.logWatcher == null) {
			this.logWatcher = new LogFileWatcher(consoleLog, out);
			this.logWatcher.start();
		}
	}
	
	@Override
	public void start() {
		sendCommand(this.getWlpRoot(), this.getJavaHome(), "start", serverName); //$NON-NLS-1$
	}
	
	@Override
	public void updateConfiguration(ServerConfiguration configuration) {
		// TODO support changes other than server.xml
		LibertyServerConfiguration config = (LibertyServerConfiguration)configuration;
		this.config.setServerXml(config.getServerXml());
		try {
			deployServerXml(config.getServerXml().getXml());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		
	}
	
	public Path getWlpRoot() {
		return wlpRoots.computeIfAbsent(this.getConfiguration(), config -> {
			@SuppressWarnings("unchecked")
			RuntimeDeploymentTask<LibertyServerConfiguration> deploymentService = OpenLibertyUtil.findExtensions(RuntimeDeploymentTask.class)
					.filter(task -> task.canDeploy(config))
					.map(task -> (RuntimeDeploymentTask<LibertyServerConfiguration>)task)
					.findFirst()
					.orElseThrow(() -> new IllegalStateException(format(Messages.getString("OpenLibertyRuntime.noDeploymentFor"), config.getClass().getName()))); //$NON-NLS-1$
			try {
				return deploymentService.deploy(config);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}
	
	public void deployServerXml(String serverXml) throws IOException {
		Path xmlFile = getWlpRoot().resolve("usr").resolve("servers").resolve(serverName).resolve("server.xml"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		try(BufferedWriter w = Files.newBufferedWriter(xmlFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			w.write(serverXml);
		}
	}
	
	@Override
	public String getListeningHost() {
		Path serverXml = getWlpRoot().resolve("usr").resolve("servers").resolve(serverName).resolve("server.xml"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		if(Files.isRegularFile(serverXml)) {
			// Parse the server.xml for port information
			try {
				XMLDocument xml = new XMLDocument();
				try(InputStream is = Files.newInputStream(serverXml)) {
					xml.loadInputStream(is);
				}
				XMLNodeList nodes = xml.selectNodes("/server/httpEndpoint"); //$NON-NLS-1$
				if(!nodes.isEmpty()) {
					// Last one wins in WLP
					XMLNode node = nodes.get(nodes.size()-1);
					String host = node.getAttribute("host"); //$NON-NLS-1$
					if(StringUtil.isEmpty(host)) {
						return "localhost"; //$NON-NLS-1$
					} else {
						return host;
					}
				}
			} catch(IOException e) {
				throw new UncheckedIOException(e);
			} catch (SAXException | ParserConfigurationException e) {
				throw new RuntimeException(e);
			}
		}
		return "*"; //$NON-NLS-1$
	}
	
	@Override
	public Collection<Integer> getListeningPorts() {
		Path serverXml = getWlpRoot().resolve("usr").resolve("servers").resolve(serverName).resolve("server.xml"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		if(Files.isRegularFile(serverXml)) {
			// Parse the server.xml for port information
			try {
				XMLDocument xml = new XMLDocument();
				try(InputStream is = Files.newInputStream(serverXml)) {
					xml.loadInputStream(is);
				}
				XMLNodeList nodes = xml.selectNodes("/server/httpEndpoint"); //$NON-NLS-1$
				if(!nodes.isEmpty()) {
					// Last one wins in WLP
					XMLNode node = nodes.get(nodes.size()-1);
					String host = node.getAttribute("host"); //$NON-NLS-1$
					if(StringUtil.isEmpty(host)) {
						host = InetAddress.getLocalHost().getHostName();
					}
					String httpPort = node.getAttribute("httpPort"); //$NON-NLS-1$
					if(StringUtil.isEmpty(httpPort)) {
						// This seems to be the default when unspecified
						httpPort = "9080"; //$NON-NLS-1$
					}
					String httpsPort = node.getAttribute("httpsPort"); //$NON-NLS-1$
					return Stream.of(httpPort, httpsPort)
						.filter(StringUtil::isNotEmpty)
						.filter(p -> !"-1".equals(p)) //$NON-NLS-1$
						.map(port -> Integer.parseInt(port))
						.collect(Collectors.toList());
				}
			} catch(IOException e) {
				throw new UncheckedIOException(e);
			} catch (SAXException | ParserConfigurationException e) {
				throw new RuntimeException(e);
			}
		}
		return Collections.emptyList();
	}
	
	@Override
	public void showStatus() {
		sendCommand(getWlpRoot(), getJavaHome(), "status", serverName); //$NON-NLS-1$
	}
	
	@Override
	public void close() throws Exception {
		sendCommand(getWlpRoot(), this.getJavaHome(), "stop", serverName); //$NON-NLS-1$
		
		for(Process p : subprocesses) {
			if(p.isAlive()) {
				try {
					p.waitFor();
				} catch (InterruptedException e) {
				}
			}
		}
		
		if(this.logWatcher != null) {
			this.logWatcher.close();
		}
	}
	
	// *******************************************************************************
	// * Internal utility methods
	// *******************************************************************************
	private boolean serverExists(Path path, String serverName) {
		// TODO change to ask Liberty for a list of servers
		Path server = path.resolve("usr").resolve("servers").resolve(serverName); //$NON-NLS-1$ //$NON-NLS-2$
		return Files.isDirectory(server);
	}
	
	private void deployExtensions(Path wlp) throws IOException {
		Path lib = wlp.resolve("usr").resolve("extension").resolve("lib"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		Files.createDirectories(lib);
		Path features = lib.resolve("features"); //$NON-NLS-1$
		Files.createDirectories(features);
		
		List<LibertyExtensionDeployer> extensions = OpenLibertyUtil.findExtensions(LibertyExtensionDeployer.class).collect(Collectors.toList());
		if(extensions != null) {
			for(LibertyExtensionDeployer ext : extensions) {
				try(InputStream is = ext.getEsaData()) {
					try(ZipInputStream zis = new ZipInputStream(is)) {
						ZipEntry entry = zis.getNextEntry();
						while(entry != null) {
							String entryName = entry.getName();
							
							// Deploy .jar entries to the lib folder
							if(entryName.toLowerCase().endsWith(".jar") && !entryName.contains("/")) { //$NON-NLS-1$ //$NON-NLS-2$
								Path dest = lib.resolve(entryName);
								if(!Files.exists(dest)) {
									Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
								}
							}
							
							// Look for SUBSYSTEM.MF, parse its info, and deploy to the features directory
							if("OSGI-INF/SUBSYSTEM.MF".equalsIgnoreCase(entryName)) { //$NON-NLS-1$
								Manifest mf = new Manifest(zis);
								String shortName = mf.getMainAttributes().getValue("IBM-ShortName"); //$NON-NLS-1$
								if(StringUtil.isEmpty(shortName)) {
									throw new IllegalArgumentException(format(
											Messages.getString("OpenLibertyRuntime.esaSubsystemNoShortName"), //$NON-NLS-1$
											ext));
								}
								Path mfDest = features.resolve(shortName + ".mf"); //$NON-NLS-1$
								if(!Files.exists(mfDest)) {
									try(OutputStream os = Files.newOutputStream(mfDest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
										mf.write(os);
									}
								}
							}

							zis.closeEntry();
							entry = zis.getNextEntry();
						}
						
					}
				}
			}
		}
	}

	private Process sendCommand(Path path, Path javaHome, String command, Object... args) {
		try {
			Path serverScript = path.resolve("bin").resolve(serverFile); //$NON-NLS-1$
			
			List<String> commands = new ArrayList<>();
			commands.add(serverScript.toString());
			commands.add(command);
			for(Object arg : args) {
				commands.add(StringUtil.toString(arg));
			}
			
			ProcessBuilder pb = new ProcessBuilder().command(commands);
			
			Map<String, String> env = pb.environment();
			env.put("JAVA_HOME", javaHome.toString()); //$NON-NLS-1$
			
			String sysPath = System.getenv("PATH"); //$NON-NLS-1$
			sysPath += File.pathSeparator + dominoProgramDirectory;
			env.put("PATH", sysPath); //$NON-NLS-1$
			
			env.put("Domino_HTTP", getServerBase()); //$NON-NLS-1$
			
			if(log.isLoggable(Level.FINE)) {
				OpenLibertyLog.getLog().fine(format(Messages.getString("OpenLibertyRuntime.executingCommand"), pb.command())); //$NON-NLS-1$
			}
			Process process = pb.start();
			subprocesses.add(process);
			
			DominoThreadFactory.getExecutor().submit(new StreamRedirector(process.getInputStream()));
			DominoThreadFactory.getExecutor().submit(new StreamRedirector(process.getErrorStream()));
			
			return process;
		} catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	/**
	 * Determines the base URL to use for local requests to the server to pass to the WLP environment
	 */
	private String getServerBase() {
		int port = runtimeConfig.getDominoPort();
		String protocol = runtimeConfig.isDominoHttps() ? "https" : "http"; //$NON-NLS-1$ //$NON-NLS-2$
		String host = runtimeConfig.getDominoHostName();
		
		return protocol + "://" + host + ":" + port; //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	private void deployServerEnv(String serverEnv) throws IOException {
		Path xmlFile = getWlpRoot().resolve("usr").resolve("servers").resolve(serverName).resolve("server.env"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		try(Writer w = Files.newBufferedWriter(xmlFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			w.write(serverEnv);
		}
	}
	/** @since 2.0.0 */
	private void deployJvmOptions(String jvmOptions) throws IOException {
		Path file = getWlpRoot().resolve("usr").resolve("servers").resolve(serverName).resolve("jvm.options"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		try(Writer w = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			w.write(jvmOptions);
		}
	}
	/** @since 2.0.0 */
	private void deployBootstrapProperties(String bootstrapProperties) throws IOException {
		Path file = getWlpRoot().resolve("usr").resolve("servers").resolve(serverName).resolve("bootstrap.properties"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		try(Writer w = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			w.write(bootstrapProperties);
		}
	}
	
	private void deployAdditionalZip(Path zip) throws IOException {
		Path serverBase = getWlpRoot().resolve("usr").resolve("servers").resolve(serverName); //$NON-NLS-1$ //$NON-NLS-2$
		try(InputStream is = Files.newInputStream(zip)) {
			try(ZipInputStream zis = new ZipInputStream(is)) {
				ZipEntry entry = zis.getNextEntry();
				while(entry != null) {
					String name = entry.getName();
					
					if(StringUtil.isNotEmpty(name)) {
						if(OpenLibertyLog.instance.log.isLoggable(Level.FINE)) {
							OpenLibertyLog.instance.log.fine(format(Messages.getString("OpenLibertyRuntime.deployingFile"), name)); //$NON-NLS-1$
						}
						
						Path outputPath = serverBase.resolve(name);
						if(entry.isDirectory()) {
							Files.createDirectories(outputPath);
						} else {
							Files.createDirectories(outputPath.getParent());
							Files.copy(zis, outputPath, StandardCopyOption.REPLACE_EXISTING);
						}
					}
					
					zis.closeEntry();
					entry = zis.getNextEntry();
				}
			}
		}
		Files.deleteIfExists(zip);
	}
}
