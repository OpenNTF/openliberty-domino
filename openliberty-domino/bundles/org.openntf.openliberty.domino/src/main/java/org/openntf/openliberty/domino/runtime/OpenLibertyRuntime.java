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
package org.openntf.openliberty.domino.runtime;

import static java.text.MessageFormat.format;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.openntf.openliberty.domino.event.EventRecipient;
import org.openntf.openliberty.domino.event.RefreshDeploymentConfigEvent;
import org.openntf.openliberty.domino.event.ServerDeployEvent;
import org.openntf.openliberty.domino.event.ServerStartEvent;
import org.openntf.openliberty.domino.event.ServerStopEvent;
import org.openntf.openliberty.domino.ext.RuntimeService;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.server.ServerConfiguration;
import org.openntf.openliberty.domino.server.ServerInstance;
import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;

public enum OpenLibertyRuntime implements Runnable {
	instance;
	
	private final BlockingQueue<RuntimeTask> taskQueue = new LinkedBlockingDeque<>();
	private final List<RuntimeService> runtimeServices = new ArrayList<>();
	private final Collection<EventRecipient> messageRecipients = Collections.synchronizedList(new ArrayList<>());
	
	private Set<String> startedServers = Collections.synchronizedSet(new HashSet<>());
	
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
			OpenLibertyUtil.findExtensions(RuntimeService.class).forEach(runtimeServices::add);
			
			runtimeServices.forEach(DominoThreadFactory.getExecutor()::submit);
			messageRecipients.addAll(runtimeServices);
			
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
						
						broadcastMessage(new ServerStartEvent(serverInstance));
						break;
					}
					case STOP: {
						String serverName = (String)command.args[0];
						ServerInstance<?> serverInstance = this.serverInstances.get(serverName);
						serverInstance.close();
						
						broadcastMessage(new ServerStopEvent(serverInstance));
						break;
					}
					case CREATE_SERVER: {
						String serverName = (String)command.args[0];
						ServerInstance<?> serverInstance = this.serverInstances.get(serverName);
						serverInstance.deploy();
						
						broadcastMessage(new ServerDeployEvent(serverInstance));
						break;
					}
					case UPDATE_DEPLOYMENT: {
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
					case REFRESH: {
						broadcastMessage(new RefreshDeploymentConfigEvent(this));
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
			stop();
			
			if(log.isLoggable(Level.INFO)) {
				log.info(Messages.getString("OpenLibertyRuntime.shutdown")); //$NON-NLS-1$
			}
		}
	}
	
	public synchronized void stop() {
		for(String serverName : startedServers) {
			try {
				if(log.isLoggable(Level.INFO)) {
					log.info(format(Messages.getString("OpenLibertyRuntime.shuttingDownServer"), serverName)); //$NON-NLS-1$
				}
				this.serverInstances.get(serverName).close();
			} catch(RejectedExecutionException | InterruptedException e) {
				// Ignore
			} catch(Throwable t) {
				log.log(Level.SEVERE, "Exception while terminating server " + serverName, t);
			}
		}
		this.serverInstances.clear();
		this.startedServers.clear();
		
		for(RuntimeService svc : this.runtimeServices) {
			try {
				svc.close();
			} catch(Throwable t) {
				log.log(Level.SEVERE, "Exception while terminating service " + svc, t);
			}
		}
		this.runtimeServices.clear();
		this.messageRecipients.clear();
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
		if(this.serverInstances.containsKey(serverName)) {
			taskQueue.add(new RuntimeTask(RuntimeTask.Type.UPDATE_DEPLOYMENT, serverName, config));
		} else {
			this.serverInstances.put(serverName, config.createInstance(serverName));
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
	
	public void createServer(String serverName) {
		taskQueue.add(new RuntimeTask(RuntimeTask.Type.CREATE_SERVER, serverName));
	}
	
	public void updateConfiguration(String serverName, ServerConfiguration config) {
		taskQueue.add(new RuntimeTask(RuntimeTask.Type.UPDATE_DEPLOYMENT, serverName, config));
	}
	
	/**
	 * Outputs the server status to the Domino console.
	 * @since 1.2.0
	 */
	public void showStatus() {
		taskQueue.add(new RuntimeTask(RuntimeTask.Type.STATUS));
	}
	
	/**
	 * Registers the provided recipient object in the list of broadcast-message targets.
	 * 
	 * @param target the recipient to register
	 * @since 3.0.0
	 */
	public void registerMessageRecipient(EventRecipient target) {
		this.messageRecipients.add(target);
	}
	
	/**
	 * Notifies all registered message listeners of the provided event.
	 * 
	 * <p>The {@link EventRecipient#notifyMessage(EventObject)} method on each recipient is
	 * submitted to an {@link ExecutorService}.</p>
	 * 
	 * @param event the event to broadcast
	 * @return a {@link List} of void-returning {@link Future} objects representing the asynchronous
	 * 		completions of each broadcast
	 */
	public synchronized List<Future<?>> broadcastMessage(EventObject event) {
		return this.messageRecipients
			.stream()
			.map(r -> DominoThreadFactory.getExecutor().submit(() -> r.notifyMessage(event)))
			.collect(Collectors.toList());
	}
	
	/**
	 * Issues a command to refresh the app deployment configuration.
	 * 
	 * @since 4.0.0
	 */
	public void refreshDeploymentConfiguration() {
		taskQueue.add(new RuntimeTask(RuntimeTask.Type.REFRESH));
	}
	
	// *******************************************************************************
	// * Internal utility methods
	// *******************************************************************************
	
	private static class RuntimeTask {
		enum Type {
			START, STOP, CREATE_SERVER, STATUS, UPDATE_DEPLOYMENT, REFRESH
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
