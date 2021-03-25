package org.openntf.openliberty.domino.event;

import java.util.EventObject;

import org.openntf.openliberty.domino.server.ServerInstance;

/**
 * This event signals that a server has been started, though it may not have
 * yet completed its initialization.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public class ServerStartEvent extends EventObject {
	private static final long serialVersionUID = 1L;

	public ServerStartEvent(ServerInstance<?> instance) {
		super(instance);
	}

	@Override
	public ServerInstance<?> getSource() {
		return (ServerInstance<?>)super.getSource();
	}
}
