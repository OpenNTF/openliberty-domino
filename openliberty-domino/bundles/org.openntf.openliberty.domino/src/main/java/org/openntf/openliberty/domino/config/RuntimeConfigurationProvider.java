package org.openntf.openliberty.domino.config;

import java.nio.file.Path;

/**
 * This extension interface specifies a service that can provide global configuration
 * options.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public interface RuntimeConfigurationProvider {
	Path getBaseDirectory();
	
	/**
	 * @return the host name to use when connecting to Domino via HTTP
	 */
	String getDominoHostName();
	/**
	 * @return the port to use when connect to Domino via HTTP, or {@code -1}
	 * 		if HTTP is disabled
	 */
	int getDominoPort();
	/**
	 * @return whether to use TLS when connecting to Domino via HTTP
	 */
	boolean isDominoHttps();
	/**
	 * @return whether Domino is configured to use HTTP connector headers
	 */
	boolean isUseDominoConnectorHeaders();
}
