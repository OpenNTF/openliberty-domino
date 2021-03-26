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
package org.openntf.openliberty.domino.server;

import java.io.PrintStream;
import java.util.Collection;

/**
 * Represented a registered instance of a server
 * 
 * @param <T> the corresponding {@link ServerConfiguration} type
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public interface ServerInstance<T extends ServerConfiguration> extends AutoCloseable {
	String getServerName();
	
	T getConfiguration();
	
	/**
	 * Monitors the server's log and directs its output to the provided destination.
	 * 
	 * @param out a {@link PrintStream} to write the log output to
	 */
	void watchLogs(PrintStream out);
	
	/**
	 * Emits the status of the server in an implementation-specific way.
	 */
	void showStatus();
	
	/**
	 * Deploys the server instance based on its configuration.
	 */
	void deploy();
	
	/**
	 * Starts the server.
	 * 
	 * <p>This should be called only after calling {@link #deploy()}.
	 */
	void start();
	
	/**
	 * Refreshes the server's configuration based on a new config object.
	 * 
	 * @param configuration the new configuration to apply
	 */
	void updateConfiguration(ServerConfiguration configuration);
	
	/**
	 * @return the name of the host this server is bound to, or {@code *} if it is listening on all addresses
	 */
	String getListeningHost();
	
	/**
	 * @return a non-{@code null} {@link Collection} of ports the server is listening on
	 */
	Collection<Integer> getListeningPorts();
}
