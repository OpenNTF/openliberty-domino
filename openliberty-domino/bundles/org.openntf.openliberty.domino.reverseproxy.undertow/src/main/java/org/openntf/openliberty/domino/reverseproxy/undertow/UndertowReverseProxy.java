package org.openntf.openliberty.domino.reverseproxy.undertow;

import java.net.URI;

import io.undertow.Undertow;
import io.undertow.server.handlers.proxy.LoadBalancingProxyClient;
import io.undertow.server.handlers.proxy.ProxyHandler;

public class UndertowReverseProxy implements Runnable {

	@Override
	public void run() {
		System.out.println("hello there, friend - ask me for 21345");
		try {
			LoadBalancingProxyClient proxy = new LoadBalancingProxyClient()
				.addHost(URI.create("http://localhost"));
			
			Undertow server = Undertow.builder()
	            .addHttpListener(21345, "0.0.0.0")
	            .setHandler(
	            	ProxyHandler.builder()
	            		.setProxyClient(proxy)
	            		.build()
	            ).build();
	        server.start();
		} catch(Throwable t) {
			t.printStackTrace();
		}
	}

}
