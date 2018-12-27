package org.openntf.openliberty.service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.openntf.openliberty.log.OpenLibertyLog;
import org.openntf.openliberty.util.DominoThreadFactory;

import com.ibm.commons.util.StringUtil;
import com.ibm.designer.runtime.domino.adapter.ComponentModule;
import com.ibm.designer.runtime.domino.adapter.HttpService;
import com.ibm.designer.runtime.domino.adapter.LCDEnvironment;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpServletRequestAdapter;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpServletResponseAdapter;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpSessionAdapter;

public class OpenLibertyService extends HttpService {
	private static final Logger log = OpenLibertyLog.LIBERTY_LOG;

	public OpenLibertyService(LCDEnvironment env) {
		super(env);
		
		DominoThreadFactory.executor.submit(OpenLibertyRuntime.instance);
	}

	@Override
	public void destroyService() {
		super.destroyService();
		
		if(log.isLoggable(Level.INFO)) {
			log.info("Shutting down OpenLiberty server");
		}
		
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
		if(StringUtil.startsWithIgnoreCase(command, "wlp ")) {
			String cmd = command.substring(4);
			OpenLibertyRuntime.instance.sendCommand(cmd);
			return "Sent command to OpenLiberty server";
		}
		
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
