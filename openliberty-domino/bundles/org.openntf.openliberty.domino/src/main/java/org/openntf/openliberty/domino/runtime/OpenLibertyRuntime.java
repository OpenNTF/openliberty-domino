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

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.openntf.openliberty.domino.ext.RuntimeService;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.server.ServerConfiguration;
import org.openntf.openliberty.domino.server.ServerInstance;
import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;

public enum OpenLibertyRuntime implements Runnable {
	instance;
	
	private final BlockingQueue<RuntimeTask> taskQueue = new LinkedBlockingDeque<RuntimeTask>();
	private final List<RuntimeService> runtimeServices = OpenLibertyUtil.findExtensions(RuntimeService.class).collect(Collectors.toList());
	
	private Set<String> startedServers = Collections.synchronizedSet(new HashSet<>());
	private Set<Process> subprocesses = Collections.synchronizedSet(new HashSet<>());
	
	/**
	 * Maps server names to their configurations.
	 * @since 3.0.0
	 */
	private Map<String, ServerInstance<?>> serverInstances = new HashMap<>();
	
	private Logger log;

	@Override
	public void run() {
		log = OpenLibertyLog.instance.log;
		
		if(log.isLoggable(Level.INFO)) {
			log.info(format(Messages.getString("OpenLibertyRuntime.0"))); //$NON-NLS-1$
		}
		
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
						String serverName = (String)command.args[0];
						ServerInstance<?> serverInstance = this.serverInstances.get(serverName);
						serverInstance.start();
						serverInstance.watchLogs(OpenLibertyLog.instance.out);
						runtimeServices.forEach(service -> {
							DominoThreadFactory.executor.submit(() -> service.notifyServerStart(serverInstance));
						});
						break;
					}
					case STOP: {
						String serverName = (String)command.args[0];
						ServerInstance<?> serverInstance = this.serverInstances.remove(serverName);
						serverInstance.close();
						
						runtimeServices.forEach(service -> {
							DominoThreadFactory.executor.submit(() -> service.notifyServerStop(serverInstance));
						});
						break;
					}
					case CREATE_SERVER: {
						String serverName = (String)command.args[0];
						ServerInstance<?> serverInstance = this.serverInstances.get(serverName);

						serverInstance.deploy();
						runtimeServices.forEach(service -> {
							DominoThreadFactory.executor.submit(() -> service.notifyServerDeploy(serverInstance));
						});
						break;
					}
					case UPDATE_CONFIGURATION: {
						String serverName = (String)command.args[0];
						ServerConfiguration newConfig = (ServerConfiguration)command.args[1];
						ServerInstance<?> serverInstance = this.serverInstances.get(serverName);
						
						serverInstance.updateConfiguration(newConfig);
						break;
					}
					case STATUS: {
						for(String serverName : startedServers) {
							this.serverInstances.get(serverName).showStatus();
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
			for(String serverName : startedServers) {
				try {
					if(log.isLoggable(Level.INFO)) {
						log.info(format(Messages.getString("OpenLibertyRuntime.shuttingDownServer"), serverName)); //$NON-NLS-1$
					}
					this.serverInstances.get(serverName).close();
				} catch (Exception e) {
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
	public void registerServer(String serverName, ServerConfiguration config) {
		this.serverInstances.put(serverName, config.createInstance(serverName));
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
	
	public void updateConfiguration(String serverName, ServerConfiguration config) {
		taskQueue.add(new RuntimeTask(RuntimeTask.Type.UPDATE_CONFIGURATION, serverName, config));
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
			START, STOP, CREATE_SERVER, STATUS, UPDATE_CONFIGURATION
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
}
