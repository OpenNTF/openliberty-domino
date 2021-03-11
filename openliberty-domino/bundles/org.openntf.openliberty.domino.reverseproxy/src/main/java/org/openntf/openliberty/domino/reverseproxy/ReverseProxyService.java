package org.openntf.openliberty.domino.reverseproxy;

import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;

import java.text.MessageFormat;

import org.openntf.openliberty.domino.ext.RuntimeService;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.reverseproxy.ext.ReverseProxyConfig;
import org.openntf.openliberty.domino.reverseproxy.ext.ReverseProxyConfigProvider;
import org.openntf.openliberty.domino.reverseproxy.undertow.UndertowReverseProxy;

public class ReverseProxyService implements RuntimeService {
	@Override
	public void run() {
		try {
			ReverseProxyConfig config = OpenLibertyUtil.findExtension(ReverseProxyConfigProvider.class)
				.map(provider -> provider.createConfiguration(this))
				.orElseThrow(() -> new IllegalStateException(MessageFormat.format("Unable to find provider for {0}", ReverseProxyConfigProvider.class.getName())));
			
			if(!config.isEnabled()) {
				OpenLibertyLog.instance.out.println("Reverse proxy disabled - skipping");
				return;
			}
			
			UndertowReverseProxy proxy = new UndertowReverseProxy();
			proxy.setPrintStream(OpenLibertyLog.instance.out);
			
			proxy.setProxyHostName(config.getProxyHostName());
			proxy.setProxyPort(config.getProxyHttpPort());
			
			proxy.setDominoHostName(config.getDominoHostName());
			proxy.setDominoHttpPort(config.getDominoHttpPort());
			proxy.setDominoHttps(config.isDominoHttps());
			if(config.isUseDominoConnectorHeaders()) {
				proxy.setUseDominoConnectorHeaders(true);
				proxy.setDominoConnectorHeadersSecret(config.getDominoConnectorHeadersSecret());
			}
			proxy.setTargets(config.getTargets());
			
			DominoThreadFactory.executor.submit(proxy);
		} catch(Throwable t) {
			t.printStackTrace();
		}
	}
}
