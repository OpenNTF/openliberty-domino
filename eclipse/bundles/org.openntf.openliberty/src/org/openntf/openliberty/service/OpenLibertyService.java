package org.openntf.openliberty.service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;

import org.openntf.openliberty.util.DominoThreadFactory;

import com.ibm.designer.runtime.domino.adapter.ComponentModule;
import com.ibm.designer.runtime.domino.adapter.HttpService;
import com.ibm.designer.runtime.domino.adapter.LCDEnvironment;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpServletRequestAdapter;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpServletResponseAdapter;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpSessionAdapter;

public class OpenLibertyService extends HttpService {

	public OpenLibertyService(LCDEnvironment env) {
		super(env);
		
		System.out.println("Initializing Liberty");
		DominoThreadFactory.executor.submit(OpenLibertyRuntime.instance);
	}

	@Override
	public void destroyService() {
		super.destroyService();
		
		DominoThreadFactory.executor.shutdownNow();
		try {
			DominoThreadFactory.executor.awaitTermination(1, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			// Ignore
		}
	}



	@Override
	public void refreshSettings() {
		super.refreshSettings();
		
	}



	@Override
	public Object tellCommand(String command) {
		return super.tellCommand(command);
	}



	@Override
	public boolean doService(String contextPath, String path, HttpSessionAdapter httpSession, HttpServletRequestAdapter httpRequest,
			HttpServletResponseAdapter httpResponse) throws ServletException, IOException {
		// NOP
		return false;
	}

	@Override
	public void getModules(List<ComponentModule> modules) {
		// NOP
	}

}
