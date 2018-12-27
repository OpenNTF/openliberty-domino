package org.openntf.openliberty.service;

import com.ibm.designer.runtime.domino.adapter.HttpService;
import com.ibm.designer.runtime.domino.adapter.IServiceFactory;
import com.ibm.designer.runtime.domino.adapter.LCDEnvironment;

public class OpenLibertyServiceFactory implements IServiceFactory {

	@Override
	public HttpService[] getServices(LCDEnvironment env) {
		return new HttpService[] {
			new OpenLibertyService(env)
		};
	}

}
