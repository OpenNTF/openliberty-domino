package org.openntf.openliberty.domino.httpapi;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.openntf.openliberty.domino.httpapi.resources.AppsResource;

public class AdminApplication extends Application {
	@Override
	public Set<Class<?>> getClasses() {
		return new HashSet<>(Arrays.asList(
			AppsResource.class,
			
			IllegalArgumentExceptionHandler.class
		));
	}
}
