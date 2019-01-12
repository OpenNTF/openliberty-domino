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
package org.openntf.openliberty.domino.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.openntf.openliberty.domino.ext.ExtensionDeployer;
import org.openntf.openliberty.domino.ext.RuntimeService;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;

import com.ibm.commons.extension.ExtensionManager;
import com.ibm.commons.util.StringUtil;
import com.ibm.commons.util.io.StreamUtil;
import com.ibm.domino.napi.c.Os;

import static com.ibm.commons.util.StringUtil.format;

public enum OpenLibertyRuntime implements Runnable {
	instance;
	
	private static final Logger log = OpenLibertyLog.LIBERTY_LOG;
	private static final String serverFile;
	static {
		if(OpenLibertyUtil.IS_WINDOWS) {
			serverFile = "server.bat";
		} else {
			serverFile = "server";
		}
	}
	
	private final BlockingQueue<RuntimeTask> taskQueue = new LinkedBlockingDeque<RuntimeTask>();
	
	private Set<String> startedServers = Collections.synchronizedSet(new HashSet<>());

	@Override
	public void run() {
		if(log.isLoggable(Level.INFO)) {
			log.info(format("Startup"));
		}
		
		Path wlp = null;
		try {
			wlp = deployRuntime();
			if(log.isLoggable(Level.INFO)) {
				log.info(format("Using runtime deployed to {0}", wlp));
			}
			createNotesApi(wlp);
			deployExtensions(wlp);
			
			List<RuntimeService> runtimeServices = ExtensionManager.findServices(null, getClass().getClassLoader(), RuntimeService.SERVICE_ID, RuntimeService.class);
			if(runtimeServices != null) {
				for(RuntimeService service : runtimeServices) {
					DominoThreadFactory.executor.submit(service);
				}
			}
			
			while(!Thread.interrupted()) {
				RuntimeTask command = taskQueue.take();
				if(command != null) {
					if(log.isLoggable(Level.FINER)) {
						log.finer(format("Received command: {0}", command));
					}
					switch(command.type) {
					case START:
						sendCommand(wlp, "start", command.args).waitFor();
						watchLog(wlp, (String)command.args[0]);
						break;
					case STOP:
						sendCommand(wlp, "stop", command.args);
						break;
					case CREATE_SERVER: {
						String serverName = (String)command.args[0];
						String serverXml = (String)command.args[1];
						String serverEnv = (String)command.args[2];
						
						if(!serverExists(wlp, serverName)) {
							sendCommand(wlp, "create", serverName).waitFor();
						}
						if(StringUtil.isNotEmpty(serverXml)) {
							deployServerXml(wlp, serverName, serverXml);
						}
						if(StringUtil.isNotEmpty(serverEnv)) {
							deployServerEnv(wlp, serverName, serverEnv);
						}
						break;
					}
					case DEPLOY_DROPIN: {
						String serverName = (String)command.args[0];
						String warName = (String)command.args[1];
						Path warFile = (Path)command.args[2];
						boolean deleteAfterDeploy = (Boolean)command.args[3];
						
						if(Files.isRegularFile(warFile)) {
							deployWar(wlp, serverName, warName, warFile);
						}
						if(deleteAfterDeploy) {
							Files.deleteIfExists(warFile);
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
				log.log(Level.SEVERE, "Encountered unexpected exception", t);
				t.printStackTrace(OpenLibertyLog.out);
			}
		} finally {
			if(wlp != null) {
				for(String serverName : startedServers) {
					try {
						sendCommand(wlp, "stop", serverName);
					} catch (IOException e) {
						// Nothing to do here
					}
				}
			}
			
			if(log.isLoggable(Level.INFO)) {
				log.info("Shutdown");
			}
		}
	}
	
	public void startServer(String serverName) {
		taskQueue.add(new RuntimeTask(RuntimeTask.Type.START, serverName));
		startedServers.add(serverName);
	}
	
	public void stopServer(String serverName) {
		taskQueue.add(new RuntimeTask(RuntimeTask.Type.STOP, serverName));
		startedServers.remove(serverName);
	}
	
	public void createServer(String serverName, String serverXml, String serverEnv) {
		taskQueue.add(new RuntimeTask(RuntimeTask.Type.CREATE_SERVER, serverName, serverXml, serverEnv));
	}
	
	public void deployDropin(String serverName, String warName, Path warFile, boolean deleteAfterDeploy) {
		taskQueue.add(new RuntimeTask(RuntimeTask.Type.DEPLOY_DROPIN, serverName, warName, warFile, deleteAfterDeploy));
	}
	
	// *******************************************************************************
	// * Internal utility methods
	// *******************************************************************************
	
	private static class RuntimeTask {
		enum Type {
			START, STOP, CREATE_SERVER, DEPLOY_DROPIN
		}
		private final Type type;
		private final Object[] args;
		
		RuntimeTask(Type type, Object... args) {
			this.type = type;
			this.args = args;
		}

		@Override
		public String toString() {
			return "RuntimeTask [type=" + type + ", args=" + Arrays.toString(args) + "]";
		}
	}
	
	private Path deployRuntime() throws IOException {
		List<RuntimeDeploymentTask> deploymentServices = ExtensionManager.findServices(null, getClass().getClassLoader(), RuntimeDeploymentTask.SERVICE_ID, RuntimeDeploymentTask.class);
		if(deploymentServices.isEmpty()) {
			throw new IllegalStateException("Unable to find any services providing " + RuntimeDeploymentTask.SERVICE_ID);
		}
		RuntimeDeploymentTask task = deploymentServices.get(0);
		if(deploymentServices.size() > 1) {
			if(log.isLoggable(Level.WARNING)) {
				log.warning(format("Found multiple services providing " + RuntimeDeploymentTask.SERVICE_ID + "; using the first, " + task));
			}
		}
		return task.call();
	}
	
	private void deployServerXml(Path path, String serverName, String serverXml) throws IOException {
		Path xmlFile = path.resolve("usr").resolve("servers").resolve(serverName).resolve("server.xml");
		try(OutputStream os = Files.newOutputStream(xmlFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			try(PrintStream ps = new PrintStream(os)) {
				ps.print(serverXml);
			}
		}
	}
	private void deployServerEnv(Path path, String serverName, String serverXml) throws IOException {
		Path xmlFile = path.resolve("usr").resolve("servers").resolve(serverName).resolve("server.env");
		try(OutputStream os = Files.newOutputStream(xmlFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			try(PrintStream ps = new PrintStream(os)) {
				ps.print(serverXml);
			}
		}
	}

	private Process sendCommand(Path path, String command, Object... args) throws IOException {
		Path serverScript = path.resolve("bin").resolve(serverFile);
		
		List<String> commands = new ArrayList<>();
		commands.add(serverScript.toString());
		commands.add(command);
		for(Object arg : args) {
			commands.add(StringUtil.toString(arg));
		}
		
		ProcessBuilder pb = new ProcessBuilder().command(commands);
		
		Map<String, String> env = pb.environment();
		env.put("JAVA_HOME", System.getProperty("java.home"));
		
		Process process = pb.start();
		
		DominoThreadFactory.executor.submit(new StreamRedirector(process.getInputStream()));
		DominoThreadFactory.executor.submit(new StreamRedirector(process.getErrorStream()));
		
		return process;
	}
	
	private void watchLog(Path path, String serverName) {
		Path logs = path.resolve("usr").resolve("servers").resolve(serverName).resolve("logs");
		String consoleLog = "console.log";
		DominoThreadFactory.executor.submit(() -> {
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
									if(is.skip(pos) < pos) {
										// It must have been truncated
										is.reset();
										pos = 0;
									}
								}
								
								String newContent = StreamUtil.readString(is);
								pos += newContent.length();
								
								OpenLibertyLog.out.println(newContent);
							}
						}
						
						if(!key.reset()) {
							break;
						}
					}
				}
			} catch (IOException e) {
				e.printStackTrace(OpenLibertyLog.out);
			} catch(InterruptedException e) {
				// Then we're shutting down
				if(log.isLoggable(Level.FINE)) {
					log.fine("Terminating log monitor");
				}
			}
		});
	}
	
	private boolean serverExists(Path path, String serverName) {
		// TODO change to ask Liberty for a list of servers
		Path server = path.resolve("usr").resolve("servers").resolve(serverName);
		return Files.isDirectory(server);
	}
	
	private void deployWar(Path wlp, String serverName, String warName, Path warFile) {
		Path dropins = wlp.resolve("usr").resolve("servers").resolve(serverName).resolve("dropins");
		
		String name;
		if(StringUtil.isNotEmpty(warName)) {
			name = warName;
		} else {
			name = warFile.getFileName().toString();
		}
		
		Path dest = dropins.resolve(name);
		try {
			Files.copy(warFile, dest, StandardCopyOption.REPLACE_EXISTING);
		} catch(IOException e) {
			if(log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, "Encountered exception when deploying dropin: " + e, e);
			}
		}
	}
	
	private void createNotesApi(Path wlp) throws IOException {
		Path lib = wlp.resolve("usr").resolve("extension").resolve("lib");
		Files.createDirectories(lib);
		
		// Find Notes.jar
		String execDir = Os.OSGetExecutableDirectory();
		Path notesJar = Paths.get(execDir, "jvm", "lib", "ext", "Notes.jar");
		Path dest = lib.resolve("org.openntf.notes.java.api_10.0.1.jar");
		try(InputStream is = Files.newInputStream(notesJar)) {
			try(ZipInputStream zis = new ZipInputStream(is)) {
				try(OutputStream os = Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
					try(ZipOutputStream zos = new ZipOutputStream(os)) {
						ZipEntry entry = zis.getNextEntry();
						Set<String> packages = new LinkedHashSet<String>();
						while(entry != null) {
							if(!entry.getName().startsWith("META-INF")) {
								if(!entry.isDirectory()) {
									zos.putNextEntry(entry);
									StreamUtil.copyStream(zis, zos);
									zos.closeEntry();
									
									int slashIndex = entry.getName().lastIndexOf('/');
									if(slashIndex > 0) {
										packages.add(entry.getName().substring(0, slashIndex).replace('/', '.'));
									}
								}
							}
							
							zis.closeEntry();
							entry = zis.getNextEntry();
						}
						
						ZipEntry manifestEntry = new ZipEntry("META-INF/MANIFEST.MF");
						zos.putNextEntry(manifestEntry);
						Manifest mf = new Manifest();
						mf.getMainAttributes().putValue("Manifest-Version", "1.0");
						mf.getMainAttributes().putValue("Bundle-SymbolicName", "org.openntf.notes.java.api");
						mf.getMainAttributes().putValue("Export-Package", String.join(",", packages));
						mf.getMainAttributes().putValue("Bundle-Name", "Notes Java API");
						mf.getMainAttributes().putValue("Bundle-Version", "10.0.1");
						mf.getMainAttributes().putValue("Bundle-ManifestVersion", "2");
						mf.getMainAttributes().putValue("DynamicImport-Package", "org.omg.CORBA");
						mf.write(zos);
					}
				}
			}
		}
	}
	
	private void deployExtensions(Path wlp) throws IOException {
		Path lib = wlp.resolve("usr").resolve("extension").resolve("lib");
		Files.createDirectories(lib);
		Path features = lib.resolve("features");
		Files.createDirectories(features);
		
		List<ExtensionDeployer> extensions = ExtensionManager.findServices(null, getClass().getClassLoader(), ExtensionDeployer.SERVICE_ID, ExtensionDeployer.class);
		if(extensions != null) {
			for(ExtensionDeployer ext : extensions) {
				List<String> fileNames = ext.getBundleFileNames();
				List<InputStream> fileData = ext.getBundleData();
				try {
					for(int i = 0; i < fileData.size(); i++) {
						InputStream is = fileData.get(i);
						String name = fileNames.get(i);
						
						Path dest = lib.resolve(name);
						try(OutputStream os = Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
							StreamUtil.copyStream(is, os);
						}
					}
					
					// Build the feature manifest
					Manifest mf = new Manifest();
					mf.getMainAttributes().putValue("Manifest-Version", "1.0");
					mf.getMainAttributes().putValue("IBM-Feature-Version", "2");
					mf.getMainAttributes().putValue("Subsystem-Type", "osgi.subsystem.feature");
					mf.getMainAttributes().putValue("Subsystem-Version", ext.getSubsystemVersion());
					mf.getMainAttributes().putValue("Subsystem-ManifestVersion", "1.0");
					mf.getMainAttributes().putValue("Subsystem-SymbolicName", ext.getFeatureId() + ";visibility:=public");
					mf.getMainAttributes().putValue("Subsystem-Content", ext.getSubsystemContent());
					mf.getMainAttributes().putValue("IBM-ShortName", ext.getFeatureId());
					Path mfDest = features.resolve(ext.getFeatureId() + ".mf");
					try(OutputStream os = Files.newOutputStream(mfDest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
						mf.write(os);
					}
					
				} finally {
					for(InputStream is : fileData) {
						StreamUtil.close(is);
					}
				}
			}
		}
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
					OpenLibertyLog.out.println(line);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}
}
