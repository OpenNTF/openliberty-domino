package org.openntf.openliberty.domino.runtime;

import java.io.InputStream;

/**
 * This service interface represents an object capable of deploying new and updated
 * apps to their designated servers.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public interface AppDeploymentProvider {
	void deployApp(String serverName, String appName, String contextPath, String fileName, boolean includeInReverseProxy, InputStream appData);
}
