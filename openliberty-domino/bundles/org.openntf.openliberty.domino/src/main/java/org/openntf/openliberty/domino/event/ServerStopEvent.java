package org.openntf.openliberty.domino.event;

import java.util.EventObject;

import org.openntf.openliberty.domino.server.ServerInstance;

/**
 * This event signals that a server has been given the stop signal.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public class ServerStopEvent extends EventObject {
	private static final long serialVersionUID = 1L;

	public ServerStopEvent(ServerInstance<?> instance) {
		super(instance);
	}

	@Override
	public ServerInstance<?> getSource() {
		return (ServerInstance<?>)super.getSource();
	}
}
