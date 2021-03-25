package org.openntf.openliberty.domino.event;

import java.util.EventListener;
import java.util.EventObject;

/**
 * Represents an object that can register itself as a recipient of broadcast
 * events.
 * 
 * @author Jesse Gallagher
 * @since 3.0.0
 */
public interface EventRecipient extends EventListener {
	void notifyMessage(EventObject event);
}
