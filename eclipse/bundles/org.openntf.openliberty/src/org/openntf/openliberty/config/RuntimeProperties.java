package org.openntf.openliberty.config;

import java.util.ResourceBundle;

public enum RuntimeProperties {
	instance;
	
	private final ResourceBundle bundle;
	
	private RuntimeProperties() {
		this.bundle = ResourceBundle.getBundle("runtime");
	}
	
	public String getVersion() {
		return bundle.getString("version");
	}
}
