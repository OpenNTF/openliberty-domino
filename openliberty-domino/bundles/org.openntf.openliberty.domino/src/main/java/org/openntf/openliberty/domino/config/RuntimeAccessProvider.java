package org.openntf.openliberty.domino.config;

/**
 * Defines a service that allows a component to check if a given user
 * has access to perform runtime actions.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public interface RuntimeAccessProvider {
	boolean canDeployApps(String userName);
}
