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
package org.openntf.openliberty.domino.runtime;

import static java.text.MessageFormat.format;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.openntf.openliberty.domino.ext.ExtensionDeployer;
import org.openntf.openliberty.domino.ext.RuntimeService;
import org.openntf.openliberty.domino.jvm.JVMIdentifier;
import org.openntf.openliberty.domino.jvm.JavaRuntimeProvider;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.server.AbstractJavaServerConfiguration;
import org.openntf.openliberty.domino.server.LibertyServerConfiguration;
import org.openntf.openliberty.domino.server.ServerConfiguration;
import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.commons.ibm.StreamUtil;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.domino.View;

public enum OpenLibertyRuntime implements Runnable {
	instance;

	private static final String serverFile;
	static {
		if(OpenLibertyUtil.IS_WINDOWS) {
			serverFile = "server.bat"; //$NON-NLS-1$
		} else {
			serverFile = "server"; //$NON-NLS-1$
		}
	}
	
	private final BlockingQueue<RuntimeTask> taskQueue = new LinkedBlockingDeque<RuntimeTask>();
	private final List<RuntimeService> runtimeServices = OpenLibertyUtil.findExtensions(RuntimeService.class).collect(Collectors.toList());
	
	private Set<String> startedServers = Collections.synchronizedSet(new HashSet<>());
	private Set<Process> subprocesses = Collections.synchronizedSet(new HashSet<>());
	// Flag used by sendCommand to check whether the whole system is shutting down
	private boolean terminating;
	
	/**
	 * Maps server names to their configurations.
	 * @since 3.0.0
	 */
	private Map<String, LibertyServerConfiguration> serverConfigurations = new HashMap<>();
	/**
	 * Caches JVM identifiers mapped to Java home paths.
	 * @since 3.0.0
	 */
	private Map<JVMIdentifier, Path> javaHomes = new HashMap<>();
	/**
	 * Caches server configurations to WLP root paths.
	 * @since 3.0.0
	 */
	private Map<LibertyServerConfiguration, Path> wlpRoots = new HashMap<>();
	
	private Path dominoProgramDirectory;
	private Logger log;
	
	private final Map<Path, Future<?>> watcherThreads = new HashMap<>();
	private final Map<Path, ScheduledFuture<?>> fileTouchThreads = new HashMap<>();

	@Override
	public void run() {
		log = OpenLibertyLog.instance.log;
		
		if(log.isLoggable(Level.INFO)) {
			log.info(format(Messages.getString("OpenLibertyRuntime.0"))); //$NON-NLS-1$
		}
		
		dominoProgramDirectory = Paths.get(OpenLibertyUtil.getDominoProgramDirectory());
		
		try {
			runtimeServices.forEach(DominoThreadFactory.executor::submit);
			
			while(!Thread.interrupted()) {
				RuntimeTask command = taskQueue.take();
				if(command != null) {
					if(log.isLoggable(Level.FINER)) {
						log.finer(format(Messages.getString("OpenLibertyRuntime.receivedCommand"), command)); //$NON-NLS-1$
					}
					switch(command.type) {
					case START: {
						// Make sure the server exists

						String serverName = (String)command.args[0];
						Path wlp = findWlpRoot(serverName);
						sendCommand(wlp, findJavaHome(serverName), "start", command.args); //$NON-NLS-1$
						if(serverExists(wlp, serverName)) {
							watchLog(wlp, serverName);
							Path fwlp = wlp;
							runtimeServices.forEach(service -> {
								DominoThreadFactory.executor.submit(() -> service.notifyServerStart(fwlp, serverName));
							});
						}
						break;
					}
					case STOP: {
						String serverName = (String)command.args[0];
						Path wlp = findWlpRoot(serverName);
						sendCommand(wlp, findJavaHome(serverName), "stop", command.args); //$NON-NLS-1$
						stopWatchLogs(wlp, serverName);
						if(serverExists(wlp, serverName)) {
							Path fwlp = wlp;
							runtimeServices.forEach(service -> {
								DominoThreadFactory.executor.submit(() -> service.notifyServerStop(fwlp, serverName));
							});
						}
						break;
					}
					case CREATE_SERVER: {
						String serverName = (String)command.args[0];
						LibertyServerConfiguration serverConfig = this.serverConfigurations.get(serverName);
						String serverXml = serverConfig.getServerXml().getXml();
						String serverEnv = serverConfig.getServerEnv();
						String jvmOptions = serverConfig.getJvmOptions();
						String bootstrapProperties = serverConfig.getBootstrapProperties();
						Collection<Path> additionalZips = serverConfig.getAdditionalZips();

						Path wlp = deployRuntime(serverConfig);
						this.wlpRoots.put(serverConfig, wlp);
						if(log.isLoggable(Level.INFO)) {
							log.info(format(Messages.getString("OpenLibertyRuntime.usingRuntimeAt"), wlp)); //$NON-NLS-1$
						}
						deployExtensions(wlp);
						
						Path javaHome = findJavaHome(serverConfig);
						
						if(!serverExists(wlp, serverName)) {
							sendCommand(wlp, javaHome, "create", serverName).waitFor(); //$NON-NLS-1$
						}
						if(StringUtil.isNotEmpty(serverXml)) {
							deployServerXml(wlp, serverName, serverXml);
						}
						if(StringUtil.isNotEmpty(serverEnv)) {
							deployServerEnv(wlp, serverName, serverEnv);
						}
						for(Path zip : additionalZips) {
							deployAdditionalZip(wlp, serverName, zip);
						}
						if(StringUtil.isNotEmpty(jvmOptions)) {
							deployJvmOptions(wlp, serverName, jvmOptions);
						}
						if(StringUtil.isNotEmpty(bootstrapProperties)) {
							deployBootstrapProperties(wlp, serverName, bootstrapProperties);
						}
						Path fwlp = wlp;
						runtimeServices.forEach(service -> {
							DominoThreadFactory.executor.submit(() -> service.notifyServerDeploy(fwlp, serverName));
						});
						break;
					}
					case UPDATE_SERVERXML: {
						String serverName = (String)command.args[0];
						String serverXml = (String)command.args[1];
						Path wlp = findWlpRoot(serverName);
						deployServerXml(wlp, serverName, serverXml);
						break;
					}
					case STATUS: {
						for(String serverName : startedServers) {
							Path wlp = findWlpRoot(serverName);
							sendCommand(wlp, findJavaHome(serverName), "status", serverName); //$NON-NLS-1$
						}
						break;
					}
					}
					
				}
			}
		} catch(InterruptedException e) {
			// That's fine
		} catch(Throwable t) {
			if(log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, Messages.getString("OpenLibertyRuntime.unexpectedException"), t); //$NON-NLS-1$
				t.printStackTrace(OpenLibertyLog.instance.out);
			}
		} finally {
			terminating = true;
			for(String serverName : startedServers) {
				try {
					if(log.isLoggable(Level.INFO)) {
						log.info(format(Messages.getString("OpenLibertyRuntime.shuttingDownServer"), serverName)); //$NON-NLS-1$
					}
					Path wlp = findWlpRoot(serverName);
					sendCommand(wlp, findJavaHome(serverName), "stop", serverName); //$NON-NLS-1$
				} catch (IOException | NotesException e) {
					// Nothing to do here
				}
			}
			
			for(Process p : subprocesses) {
				if(p.isAlive()) {
					try {
						p.waitFor();
					} catch (InterruptedException e) {
					}
				}
			}
			
			if(log.isLoggable(Level.INFO)) {
				log.info(Messages.getString("OpenLibertyRuntime.shutdown")); //$NON-NLS-1$
			}
		}
	}
	
	/**
	 * Registers the named server with the runtime. This should be called before performing any
	 * further operations.
	 * 
	 * @param serverName the name of the server to register
	 * @param config a configuration object representing the server
	 * @since 3.0.0
	 */
	public void registerServer(String serverName, LibertyServerConfiguration config) {
		this.serverConfigurations.put(serverName, config);
	}
	
	public void startServer(String serverName) {
		taskQueue.add(new RuntimeTask(RuntimeTask.Type.START, serverName));
		startedServers.add(serverName);
	}
	
	public void stopServer(String serverName) {
		taskQueue.add(new RuntimeTask(RuntimeTask.Type.STOP, serverName));
		startedServers.remove(serverName);
	}
	
	public void createServer(String serverName) {
		taskQueue.add(new RuntimeTask(RuntimeTask.Type.CREATE_SERVER, serverName));
	}
	
	public void deployServerXml(String serverName, String serverXml) {
		taskQueue.add(new RuntimeTask(RuntimeTask.Type.UPDATE_SERVERXML, serverName, serverXml));
	}
	
	/**
	 * Outputs the server status to the Domino console.
	 * @since 1.2.0
	 */
	public void showStatus() {
		taskQueue.add(new RuntimeTask(RuntimeTask.Type.STATUS));
	}
	
	// *******************************************************************************
	// * Internal utility methods
	// *******************************************************************************
	
	private static class RuntimeTask {
		enum Type {
			START, STOP, CREATE_SERVER, STATUS, UPDATE_SERVERXML
		}
		private final Type type;
		private final Object[] args;
		
		RuntimeTask(Type type, Object... args) {
			this.type = type;
			this.args = args;
		}

		@Override
		public String toString() {
			return MessageFormat.format("RuntimeTask [type={0}, args={1}]", type, Arrays.toString(args)); //$NON-NLS-1$
		}
	}
	
	private <T extends ServerConfiguration> Path deployRuntime(T config) throws IOException {
		@SuppressWarnings("unchecked")
		RuntimeDeploymentTask<T> deploymentService = OpenLibertyUtil.findExtensions(RuntimeDeploymentTask.class)
				.filter(task -> task.canDeploy(config))
				.map(task -> (RuntimeDeploymentTask<T>)task)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(format(Messages.getString("OpenLibertyRuntime.noDeploymentFor"), config.getClass().getName())));
		return deploymentService.deploy(config);
	}
	
	private void deployServerXml(Path path, String serverName, String serverXml) throws IOException {
		Path xmlFile = path.resolve("usr").resolve("servers").resolve(serverName).resolve("server.xml"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		try(OutputStream os = Files.newOutputStream(xmlFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			try(PrintStream ps = new PrintStream(os)) {
				ps.print(serverXml);
			}
		}
	}
	private void deployServerEnv(Path path, String serverName, String serverEnv) throws IOException {
		Path xmlFile = path.resolve("usr").resolve("servers").resolve(serverName).resolve("server.env"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		try(Writer w = Files.newBufferedWriter(xmlFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			w.write(serverEnv);
		}
	}
	/** @since 2.0.0 */
	private void deployJvmOptions(Path path, String serverName, String jvmOptions) throws IOException {
		Path file = path.resolve("usr").resolve("servers").resolve(serverName).resolve("jvm.options"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		try(Writer w = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			w.write(jvmOptions);
		}
	}
	/** @since 2.0.0 */
	private void deployBootstrapProperties(Path path, String serverName, String bootstrapProperties) throws IOException {
		Path file = path.resolve("usr").resolve("servers").resolve(serverName).resolve("bootstrap.properties"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		try(Writer w = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			w.write(bootstrapProperties);
		}
	}
	
	private void deployAdditionalZip(Path path, String serverName, Path zip) throws IOException {
		Path serverBase = path.resolve("usr").resolve("servers").resolve(serverName); //$NON-NLS-1$ //$NON-NLS-2$
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

	private Process sendCommand(Path path, Path javaHome, String command, Object... args) throws IOException, NotesException {
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
		
		if(!terminating) {
			DominoThreadFactory.executor.submit(new StreamRedirector(process.getInputStream()));
			DominoThreadFactory.executor.submit(new StreamRedirector(process.getErrorStream()));
		}
		
		return process;
	}
	
	private synchronized void watchLog(Path path, String serverName) {
		Path logs = path.resolve("usr").resolve("servers").resolve(serverName).resolve("logs"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		if(!watcherThreads.containsKey(logs)) {
			if(!Files.exists(logs)) {
				try {
					Files.createDirectories(logs);
				} catch (IOException e) {
					e.printStackTrace(OpenLibertyLog.instance.out);
				}
			}
			String consoleLog = "console.log"; //$NON-NLS-1$
			watcherThreads.put(logs, DominoThreadFactory.executor.submit(() -> {
				try(WatchService watchService = FileSystems.getDefault().newWatchService()) {
					long pos = 0;
					logs.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
					
					while(true) {
						WatchKey key = watchService.poll(25, TimeUnit.MILLISECONDS);
						if(key == null) {
							Thread.yield();
							continue;
						}
						
						for(WatchEvent<?> event : key.pollEvents()) {
							WatchEvent.Kind<?> kind = event.kind();
							
							@SuppressWarnings("unchecked")
							WatchEvent<Path> ev = (WatchEvent<Path>)event;
							Path file = ev.context();
							
							if(kind == StandardWatchEventKinds.OVERFLOW) {
								Thread.yield();
								continue;
							} else if(kind == StandardWatchEventKinds.ENTRY_MODIFY && file.getFileName().toString().equals(consoleLog)) {
								// Then read whatever we haven't read
								try(InputStream is = Files.newInputStream(logs.resolve(file), StandardOpenOption.READ)) {
									if(pos > 0) {
										if(Files.size(logs.resolve(file)) < pos) {
											// It must have been truncated
											pos = 0;
										} else {
											is.skip(pos);
										}
									}
									
									String newContent = StreamUtil.readString(is);
									pos += newContent.length();
									
									OpenLibertyLog.instance.out.println(newContent);
								}
							}
							
							if(!key.reset()) {
								break;
							}
						}
					}
				} catch (IOException e) {
					e.printStackTrace(OpenLibertyLog.instance.out);
				} catch(InterruptedException e) {
					// Then we're shutting down
					if(OpenLibertyLog.instance.log.isLoggable(Level.FINE)) {
						OpenLibertyLog.instance.log.fine(Messages.getString("OpenLibertyRuntime.terminatingLogMonitor")); //$NON-NLS-1$
					}
				}
			}));
			
			if(OpenLibertyUtil.IS_WINDOWS) {
				File consoleLogPath = logs.resolve(consoleLog).toFile();
				// Spawn a second thread to nudge the filesystem every so often, since the above polling
				//   doesn't actually work particularly well on Windows
				fileTouchThreads.put(logs, DominoThreadFactory.scheduler.scheduleWithFixedDelay(() -> {
					if(consoleLogPath.exists()) {
						consoleLogPath.length();
					}
				}, 10, 2, TimeUnit.SECONDS));
			}
		}
	}
	
	/** @since 2.0.0 */
	private void stopWatchLogs(Path path, String serverName) {
		Path logs = path.resolve("usr").resolve("servers").resolve(serverName).resolve("logs"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		if(watcherThreads.containsKey(logs)) {
			watcherThreads.remove(logs).cancel(true);
		}
		if(fileTouchThreads.containsKey(logs)) {
			fileTouchThreads.remove(logs).cancel(true);
		}
	}
	
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
		
		List<ExtensionDeployer> extensions = OpenLibertyUtil.findExtensions(ExtensionDeployer.class).collect(Collectors.toList());
		if(extensions != null) {
			for(ExtensionDeployer ext : extensions) {
				try(InputStream is = ext.getEsaData()) {
					try(ZipInputStream zis = new ZipInputStream(is)) {
						ZipEntry entry = zis.getNextEntry();
						while(entry != null) {
							String entryName = entry.getName();
							
							// Deploy .jar entries to the lib folder
							if(entryName.toLowerCase().endsWith(".jar") && !entryName.contains("/")) { //$NON-NLS-1$ //$NON-NLS-2$
								Path dest = lib.resolve(entryName);
								Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
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
								try(OutputStream os = Files.newOutputStream(mfDest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
									mf.write(os);
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
	
	/**
	 * Determines the base URL to use for local requests to the server to pass to the WLP environment
	 * @throws NotesException 
	 */
	private String getServerBase() throws NotesException {
		Session session = NotesFactory.createSession();
		try {
			// HTTP_Port int
			// HTTP_HostName string
			// HTTP_NormalMode string 1=on, 2=off, 3=redirect to SSL
			// HTTP_SSLPort int
			// HTTP_SSLMode string 1=on, 2=off
			
			Database names = session.getDatabase("", "names.nsf"); //$NON-NLS-1$ //$NON-NLS-2$
			View servers = names.getView("$Servers"); //$NON-NLS-1$
			Document serverDoc = servers.getDocumentByKey(session.getUserName(), true);
			
			int port;
			String protocol;
			String host;
			
			// Prefer HTTP for simplicity
			String httpMode = serverDoc.getItemValueString("HTTP_NormalMode"); //$NON-NLS-1$
			if("1".equals(httpMode)) { //$NON-NLS-1$
				port = serverDoc.getItemValueInteger("HTTP_Port"); //$NON-NLS-1$
				protocol = "http"; //$NON-NLS-1$
			} else {
				// Assume SSL is on, since otherwise we don't have a good option
				port = serverDoc.getItemValueInteger("HTTP_SSLPort"); //$NON-NLS-1$
				protocol = "https"; //$NON-NLS-1$
			}
			
			host = serverDoc.getItemValueString("HTTP_HostName"); //$NON-NLS-1$
			if(StringUtil.isEmpty(host)) {
				host = "localhost"; //$NON-NLS-1$
			}
			
			return protocol + "://" + host + ":" + port; //$NON-NLS-1$ //$NON-NLS-2$
		} finally {
			session.recycle();
		}
	}
	
	private Path findWlpRoot(String serverName) {
		AbstractJavaServerConfiguration config = serverConfigurations.get(serverName);
		return wlpRoots.get(config);
	}
	
	private Path findJavaHome(String serverName) {
		AbstractJavaServerConfiguration config = serverConfigurations.get(serverName);
		return this.findJavaHome(config);
	}
	
	private Path findJavaHome(AbstractJavaServerConfiguration config) {
		return this.javaHomes.computeIfAbsent(config.getJavaVersion(), javaIdentifier -> {
			JavaRuntimeProvider javaRuntimeProvider = OpenLibertyUtil.findExtensions(JavaRuntimeProvider.class)
				.filter(p -> p.canProvide(javaIdentifier))
				.sorted(Comparator.comparing(JavaRuntimeProvider::getPriority).reversed())
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(format(Messages.getString("OpenLibertyRuntime.unableToFindJVMFor"), javaIdentifier))); //$NON-NLS-1$
			Path javaHome = javaRuntimeProvider.getJavaHome(javaIdentifier);
			if(log.isLoggable(Level.INFO)) {
				log.info(format(Messages.getString("OpenLibertyRuntime.usingJavaRuntimeAt"), javaHome)); //$NON-NLS-1$
			}
			return javaHome;
		});
	}
	
	private static class StreamRedirector implements Runnable {
		private final InputStream is;
		
		StreamRedirector(InputStream is) {
			this.is = is;
		}
		
		@Override
		public void run() {
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(is));
				String line = null;
				while((line = reader.readLine()) != null) {
					OpenLibertyLog.instance.out.println(line);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}
}
