package org.openntf.openliberty.domino.reverseproxy;

/**
 * Represents a reverse proxy service able to handle proxying to running application servers.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public interface ReverseProxyService {
	/**
	 * @return an implementation-specific type identifier for the proxy
	 */
	String getProxyType();
}
