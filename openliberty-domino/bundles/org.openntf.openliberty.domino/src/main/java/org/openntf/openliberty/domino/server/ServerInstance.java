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
