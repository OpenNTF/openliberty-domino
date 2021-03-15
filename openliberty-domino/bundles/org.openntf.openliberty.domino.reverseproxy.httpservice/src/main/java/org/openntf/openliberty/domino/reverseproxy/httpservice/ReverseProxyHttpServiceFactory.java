package org.openntf.openliberty.domino.reverseproxy.httpservice;

import com.ibm.designer.runtime.domino.adapter.HttpService;
import com.ibm.designer.runtime.domino.adapter.IServiceFactory;
import com.ibm.designer.runtime.domino.adapter.LCDEnvironment;

public class ReverseProxyHttpServiceFactory implements IServiceFactory {

	@Override
	public HttpService[] getServices(LCDEnvironment env) {
		return new HttpService[] {
			new ReverseProxyHttpService(env)
		};
	}

}
