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
package org.openntf.openliberty.domino.runtime;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.PosixFilePermission;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
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

import org.openntf.openliberty.domino.ext.ExtensionDeployer;
import org.openntf.openliberty.domino.ext.RuntimeService;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;

import com.ibm.commons.extension.ExtensionManager;
import com.ibm.commons.util.StringUtil;
import com.ibm.commons.util.io.StreamUtil;
import com.ibm.domino.napi.c.Os;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.domino.View;

import static com.ibm.commons.util.StringUtil.format;

public enum OpenLibertyRuntime implements Runnable {
	instance;
	
	private static final Logger log = OpenLibertyLog.LIBERTY_LOG;
	private static final String serverFile;
	static {
		if(OpenLibertyUtil.IS_WINDOWS) {
			serverFile = "server.bat"; //$NON-NLS-1$
		} else {
			serverFile = "server"; //$NON-NLS-1$
		}
	}
	
	private final BlockingQueue<RuntimeTask> taskQueue = new LinkedBlockingDeque<RuntimeTask>();
	private List<RuntimeService> runtimeServices = ExtensionManager.findServices(null, getClass().getClassLoader(), RuntimeService.SERVICE_ID, RuntimeService.class);
	
	private Set<String> startedServers = Collections.synchronizedSet(new HashSet<>());
	
	private Path javaHome;

	@Override
	public void run() {
		if(log.isLoggable(Level.INFO)) {
			log.info(format("Startup"));
		}
		
		List<JavaRuntimeProvider> javaRuntimeProviders = ExtensionManager.findServices(null, getClass().getClassLoader(), JavaRuntimeProvider.SERVICE_ID, JavaRuntimeProvider.class);
		if(javaRuntimeProviders == null || javaRuntimeProviders.isEmpty()) {
			throw new IllegalStateException(format("Unable to find service providing {0}", JavaRuntimeProvider.SERVICE_ID));
		}
		javaHome = javaRuntimeProviders.get(0).getJavaHome();
		if(log.isLoggable(Level.INFO)) {
			log.info(format("Using Java runtime located at  {0}", javaHome));
		}
		
		Path wlp = null;
		try {
			wlp = deployRuntime();
			if(log.isLoggable(Level.INFO)) {
				log.info(format("Using runtime deployed to {0}", wlp));
			}
			verifyRuntime(wlp);
			deployExtensions(wlp);
			
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
						sendCommand(wlp, "start", command.args).waitFor(); //$NON-NLS-1$
						watchLog(wlp, (String)command.args[0]);
						break;
					case STOP:
						sendCommand(wlp, "stop", command.args); //$NON-NLS-1$
						break;
					case CREATE_SERVER: {
						String serverName = (String)command.args[0];
						String serverXml = (String)command.args[1];
						String serverEnv = (String)command.args[2];
						@SuppressWarnings("unchecked")
						List<Path> additionalZips = (List<Path>)command.args[3];
						
						if(!serverExists(wlp, serverName)) {
							sendCommand(wlp, "create", serverName).waitFor(); //$NON-NLS-1$
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
					case STATUS: {
						for(String serverName : startedServers) {
							sendCommand(wlp, "status", serverName); //$NON-NLS-1$
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
						sendCommand(wlp, "stop", serverName); //$NON-NLS-1$
					} catch (IOException | NotesException e) {
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
	
	public void createServer(String serverName, String serverXml, String serverEnv, List<Path> additionalZips) {
		taskQueue.add(new RuntimeTask(RuntimeTask.Type.CREATE_SERVER, serverName, serverXml, serverEnv, additionalZips));
	}
	
	public void deployDropin(String serverName, String warName, Path warFile, boolean deleteAfterDeploy) {
		taskQueue.add(new RuntimeTask(RuntimeTask.Type.DEPLOY_DROPIN, serverName, warName, warFile, deleteAfterDeploy));
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
			START, STOP, CREATE_SERVER, DEPLOY_DROPIN, STATUS
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
	
	private Path deployRuntime() throws IOException {
		List<RuntimeDeploymentTask> deploymentServices = ExtensionManager.findServices(null, getClass().getClassLoader(), RuntimeDeploymentTask.SERVICE_ID, RuntimeDeploymentTask.class);
		if(deploymentServices.isEmpty()) {
			throw new IllegalStateException(format("Unable to find any services providing {0}", RuntimeDeploymentTask.SERVICE_ID));
		}
		RuntimeDeploymentTask task = deploymentServices.get(0);
		if(deploymentServices.size() > 1) {
			if(log.isLoggable(Level.WARNING)) {
				log.warning(format("Found multiple services providing {0}; using the first, {1}", RuntimeDeploymentTask.SERVICE_ID, task));
			}
		}
		return task.call();
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
	
	private void deployServerXml(Path path, String serverName, String serverXml) throws IOException {
		Path xmlFile = path.resolve("usr").resolve("servers").resolve(serverName).resolve("server.xml"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		try(OutputStream os = Files.newOutputStream(xmlFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			try(PrintStream ps = new PrintStream(os)) {
				ps.print(serverXml);
			}
		}
	}
	private void deployServerEnv(Path path, String serverName, String serverXml) throws IOException {
		Path xmlFile = path.resolve("usr").resolve("servers").resolve(serverName).resolve("server.env"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		try(OutputStream os = Files.newOutputStream(xmlFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			try(PrintStream ps = new PrintStream(os)) {
				ps.print(serverXml);
			}
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
						if(log.isLoggable(Level.FINE)) {
							log.fine(format("Deploying file {0}", name));
						}
						
						Path outputPath = serverBase.resolve(name);
						if(entry.isDirectory()) {
							Files.createDirectories(outputPath);
						} else {
							try(OutputStream os = Files.newOutputStream(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
								StreamUtil.copyStream(zis, os);
							}
						}
					}
					
					zis.closeEntry();
					entry = zis.getNextEntry();
				}
			}
		}
		Files.deleteIfExists(zip);
	}

	private Process sendCommand(Path path, String command, Object... args) throws IOException, NotesException {
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
		sysPath += File.pathSeparator + Os.OSGetExecutableDirectory();
		env.put("PATH", sysPath); //$NON-NLS-1$
		env.put("Domino_HTTP", getServerBase()); //$NON-NLS-1$
		
		Process process = pb.start();
		
		DominoThreadFactory.executor.submit(new StreamRedirector(process.getInputStream()));
		DominoThreadFactory.executor.submit(new StreamRedirector(process.getErrorStream()));
		
		return process;
	}
	
	private void watchLog(Path path, String serverName) {
		Path logs = path.resolve("usr").resolve("servers").resolve(serverName).resolve("logs"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		String consoleLog = "console.log"; //$NON-NLS-1$
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
		Path server = path.resolve("usr").resolve("servers").resolve(serverName); //$NON-NLS-1$ //$NON-NLS-2$
		return Files.isDirectory(server);
	}
	
	private void deployWar(Path wlp, String serverName, String warName, Path warFile) {
		Path dropins = wlp.resolve("usr").resolve("servers").resolve(serverName).resolve("dropins"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		
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
				log.log(Level.SEVERE, format("Encountered exception when deploying dropin: {0}", e), e);
			}
		}
	}
	
	private void deployExtensions(Path wlp) throws IOException {
		Path lib = wlp.resolve("usr").resolve("extension").resolve("lib"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		Files.createDirectories(lib);
		Path features = lib.resolve("features"); //$NON-NLS-1$
		Files.createDirectories(features);
		
		List<ExtensionDeployer> extensions = ExtensionManager.findServices(null, getClass().getClassLoader(), ExtensionDeployer.SERVICE_ID, ExtensionDeployer.class);
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
								try(OutputStream os = Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
									StreamUtil.copyStream(zis, os);
								}
							}
							
							// Look for SUBSYSTEM.MF, parse its info, and deploy to the features directory
							if("OSGI-INF/SUBSYSTEM.MF".equalsIgnoreCase(entryName)) { //$NON-NLS-1$
								Manifest mf = new Manifest(zis);
								String shortName = mf.getMainAttributes().getValue("IBM-ShortName"); //$NON-NLS-1$
								if(StringUtil.isEmpty(shortName)) {
									throw new IllegalArgumentException(format(
											"ESA subsystem manifest provided by {0} doesn''t contain an IBM-ShortName",
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
