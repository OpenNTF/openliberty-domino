package org.openntf.openliberty.domino.reverseproxy;

import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;

import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openntf.openliberty.domino.ext.RuntimeService;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.reverseproxy.ext.ReverseProxyConfig;
import org.openntf.openliberty.domino.reverseproxy.ext.ReverseProxyConfigProvider;
import org.openntf.openliberty.domino.reverseproxy.undertow.UndertowReverseProxy;

public class ReverseProxyService implements RuntimeService {
	private static final Logger log = OpenLibertyLog.getLog();
	
	@Override
	public void run() {
		try {
			ReverseProxyConfig config = OpenLibertyUtil.findExtension(ReverseProxyConfigProvider.class)
				.map(provider -> provider.createConfiguration(this))
				.orElseThrow(() -> new IllegalStateException(MessageFormat.format("Unable to find provider for {0}", ReverseProxyConfigProvider.class.getName())));
			
			if(!config.enabled) {
				if(log.isLoggable(Level.INFO)) {
					OpenLibertyLog.instance.out.println("Reverse proxy disabled");
				}
				return;
			}
			
			UndertowReverseProxy proxy = new UndertowReverseProxy();
			proxy.setLogger(log);
			
			proxy.setProxyHostName(config.proxyHostName);
			proxy.setProxyHttpPort(config.proxyHttpPort);
			proxy.setProxyHttpsPort(config.proxyHttpsPort);
			proxy.setProxyHttpsContext(config.proxyHttpsContext);
			proxy.setMaxEntitySize(config.maxEntitySize);
			
			proxy.setDominoHostName(config.dominoHostName);
			proxy.setDominoHttpPort(config.dominoHttpPort);
			proxy.setDominoHttps(config.dominoHttps);
			if(config.useDominoConnectorHeaders) {
				proxy.setUseDominoConnectorHeaders(true);
				proxy.setDominoConnectorHeadersSecret(config.dominoConnectorHeadersSecret);
			}
			proxy.setTargets(config.getTargets());
			
			DominoThreadFactory.executor.submit(proxy);
		} catch(Throwable t) {
			t.printStackTrace();
		}
	}
}
