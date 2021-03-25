package org.openntf.openliberty.domino.reverseproxy.event;

import java.util.EventObject;

import org.openntf.openliberty.domino.reverseproxy.ReverseProxyConfig;

public class ReverseProxyConfigChangedEvent extends EventObject {
	private static final long serialVersionUID = 1L;

	public ReverseProxyConfigChangedEvent(ReverseProxyConfig source) {
		super(source);
	}
	
	@Override
	public ReverseProxyConfig getSource() {
		return (ReverseProxyConfig)super.getSource();
	}
}
