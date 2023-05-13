package org.openntf.openliberty.domino.event;

import java.util.EventObject;

/**
 * Represents a command to refresh the deployment config, such as re-checking
 * the configuration database and re-deploying any changed servers.
 * 
 * @author Jesse Gallagher
 * @since 4.0.0
 */
public class RefreshDeploymentConfigEvent extends EventObject {

	public RefreshDeploymentConfigEvent(Object source) {
		super(source);
	}

}
