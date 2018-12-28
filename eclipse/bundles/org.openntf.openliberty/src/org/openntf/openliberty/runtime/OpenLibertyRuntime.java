/**
 * Copyright © 2018 Jesse Gallagher
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
package org.openntf.openliberty.runtime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.openntf.openliberty.config.RuntimeProperties;
import org.openntf.openliberty.log.OpenLibertyLog;
import org.openntf.openliberty.util.DominoThreadFactory;

import com.ibm.commons.util.StringUtil;
import com.ibm.commons.util.io.StreamUtil;
import com.ibm.domino.napi.c.Os;

import static com.ibm.commons.util.StringUtil.format;

public enum OpenLibertyRuntime implements Runnable {
	instance;
	
	private static final Logger log = OpenLibertyLog.LIBERTY_LOG;
	private static final String serverFile;
	static {
		String os = System.getProperty("os.name");
		if(os.toLowerCase().contains("windows")) {
			serverFile = "server.bat";
		} else {
			serverFile = "server";
		}
	}
	private static final String serverName = "defaultServer";
	
	private final BlockingQueue<String> commandQueue = new LinkedBlockingDeque<String>();

	@Override
	public void run() {
		String version = RuntimeProperties.instance.getVersion();
		
		if(log.isLoggable(Level.INFO)) {
			log.info(format("Startup version {0}", version));
		}
		
		Path wlp = null;
		try {
			wlp = deployRuntime(version);
			if(log.isLoggable(Level.INFO)) {
				log.info(format("Using runtime deployed to {0}", wlp));
			}
			
			sendCommand(wlp, "start");
			watchLog(wlp);
			
			while(!Thread.interrupted()) {
				String command = commandQueue.take();
				if(StringUtil.isNotEmpty(command)) {
					if(log.isLoggable(Level.INFO)) {
						log.info(format("Received command: {0}", command));
					}
					sendCommand(wlp, command);
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
				try {
					sendCommand(wlp, "stop").waitFor();
				} catch (IOException e) {
					// Not much to do here
				} catch (InterruptedException e) {
					// Neither here
				}
			}
			
			if(log.isLoggable(Level.INFO)) {
				log.info("Shutdown");
			}
		}
	}
	
	public void sendCommand(String command) {
		commandQueue.add(command);
	}
	
	private Path deployRuntime(String version) throws IOException {
		if(log.isLoggable(Level.INFO)) {
			log.info("Checking runtime deployment");
		}
		
		String execDir = Os.OSGetExecutableDirectory();
		Path wlp = Paths.get(execDir, format("wlp-{0}", RuntimeProperties.instance.getVersion()));
		
		if(!Files.isDirectory(wlp)) {
			// If it doesn't yet exist, deploy from the embedded runtime
			if(log.isLoggable(Level.INFO)) {
				log.info("Deploying new runtime");
			}
			
			Files.createDirectories(wlp);
			
			try(InputStream is = getClass().getResourceAsStream(format("/runtime/openliberty-runtime-{0}.zip", version))) {
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
	}

	private Process sendCommand(Path path, String command) throws IOException {
		Path serverScript = path.resolve("bin").resolve(serverFile);
		
		ProcessBuilder pb = new ProcessBuilder()
			.command(Arrays.asList(serverScript.toString(), command));
		
		Map<String, String> env = pb.environment();
		env.put("JAVA_HOME", System.getProperty("java.home"));
		
		Process process = pb.start();
		
		DominoThreadFactory.executor.submit(new StreamRedirector(process.getInputStream()));
		DominoThreadFactory.executor.submit(new StreamRedirector(process.getErrorStream()));
		
		return process;
	}
	
	private void watchLog(Path path) {
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
