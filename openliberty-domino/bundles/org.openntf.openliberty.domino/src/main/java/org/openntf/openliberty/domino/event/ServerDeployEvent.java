package org.openntf.openliberty.domino.event;

import java.util.EventObject;

import org.openntf.openliberty.domino.server.ServerInstance;

/**
 * This event signals that a server configuration is deployed to the file system.
 * The server is not guaranteed to be running when this is called.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public class ServerDeployEvent extends EventObject {
	private static final long serialVersionUID = 1L;

	public ServerDeployEvent(ServerInstance<?> instance) {
		super(instance);
	}

	@Override
	public ServerInstance<?> getSource() {
		return (ServerInstance<?>)super.getSource();
	}
}
