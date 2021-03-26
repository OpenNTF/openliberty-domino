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
package org.openntf.openliberty.domino.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.runtime.Messages;
import org.openntf.openliberty.domino.util.commons.ibm.StreamUtil;

/**
 * This class monitors a given log file and directs new data append to it to a given {@link PrintStream}.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public class LogFileWatcher implements AutoCloseable {
	private static final Logger log = OpenLibertyLog.getLog();
	
	private final Path logFile;
	private final PrintStream out;

	private Future<?> watcherThread;
	private ScheduledFuture<?> fileTouchThread;
	
	public LogFileWatcher(Path logFile, PrintStream out) {
		this.logFile = logFile;
		this.out = out;
	}
	
	public void start() {
		Path logs = logFile.getParent();
		if(!Files.exists(logs)) {
			try {
				Files.createDirectories(logs);
			} catch (IOException e) {
				e.printStackTrace(out);
			}
		}
		String consoleLog = logFile.getFileName().toString();
		watcherThread = DominoThreadFactory.executor.submit(() -> {
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
				e.printStackTrace(out);
			} catch(InterruptedException e) {
				// Then we're shutting down
				if(log.isLoggable(Level.FINE)) {
					log.fine(Messages.getString("OpenLibertyRuntime.terminatingLogMonitor")); //$NON-NLS-1$
				}
			}
		});
		
		if(OpenLibertyUtil.IS_WINDOWS) {
			File consoleLogPath = logs.resolve(consoleLog).toFile();
			// Spawn a second thread to nudge the filesystem every so often, since the above polling
			//   doesn't actually work particularly well on Windows
			fileTouchThread = DominoThreadFactory.scheduler.scheduleWithFixedDelay(() -> {
				if(consoleLogPath.exists()) {
					consoleLogPath.length();
				}
			}, 10, 2, TimeUnit.SECONDS);
		}
	}
	

	@Override
	public void close() throws Exception {
		if(this.watcherThread != null) {
			this.watcherThread.cancel(true);
		}
		if(this.fileTouchThread != null) {
			this.fileTouchThread.cancel(true);
		}
	}

}
