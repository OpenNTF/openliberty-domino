package org.openntf.openliberty.domino.adminnsf.proxy;

import java.text.MessageFormat;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutionException;

import org.openntf.openliberty.domino.adminnsf.AdminNSFService;
import org.openntf.openliberty.domino.adminnsf.util.AdminNSFUtil;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyService;
import org.openntf.openliberty.domino.reverseproxy.ext.ReverseProxyConfig;
import org.openntf.openliberty.domino.reverseproxy.ext.ReverseProxyConfigProvider;
import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.xml.XMLDocument;
import org.openntf.openliberty.domino.util.xml.XMLNode;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.ViewEntry;
import lotus.domino.ViewNavigator;

public class AdminNSFProxyConfigProvider implements ReverseProxyConfigProvider {

	@Override
	public ReverseProxyConfig createConfiguration(ReverseProxyService service) {
		ReverseProxyConfig result = new ReverseProxyConfig();
		
		
		try {
			// Load the main config
			DominoThreadFactory.executor.submit(() -> {
				try {
					Session session = NotesFactory.createSession();
					try {
						Database adminNsf = AdminNSFUtil.getAdminDatabase(session);
						Document config = AdminNSFUtil.getConfigurationDocument(adminNsf);
						
						boolean enable = "Y".equals(config.getItemValueString("ReverseProxyEnable"));
						result.setEnabled(enable);
						if(!enable) {
							return;
						}
						
						String hostName = config.getItemValueString("ReverseProxyHostName");
						if(hostName == null || hostName.isEmpty()) {
							hostName = "0.0.0.0";
						}
						result.setProxyHostName(hostName);
						int port = config.getItemValueInteger("ReverseProxyPort");
						if(port == 0) {
							port = 8080;
						}
						result.setProxyHttpPort(port);
						
						boolean connectorHeaders = "Y".equals(config.getItemValueString("ReverseProxyConnectorHeaders"));
						if(connectorHeaders) {
							result.setUseDominoConnectorHeaders(connectorHeaders);
							String secret = session.getEnvironmentString("HTTPConnectorHeadersSecret", true);
							result.setDominoConnectorHeadersSecret(secret);
						}
						
						
						// Look for proxy-enabled webapps
						Map<String, XMLDocument> serverXmls = new HashMap<>();
						View apps = adminNsf.getView("ReverseProxyApps");
						apps.setAutoUpdate(false);
						ViewNavigator nav = apps.createViewNav();
						ViewEntry entry = nav.getFirst();
						while(entry != null) {
							Vector<?> columnValues = entry.getColumnValues();
							
							String ref = (String)columnValues.get(0);
							String contextRoot = (String)columnValues.get(1);
							if(contextRoot.startsWith("/")) {
								contextRoot = contextRoot.substring(1);
							}

							// TODO participate in any random-numbering scheme available - could read from the filesystem
							XMLDocument serverXml = serverXmls.computeIfAbsent(ref, unid -> {
								try {
									Document server = adminNsf.getDocumentByUNID(ref);
									String serverXmlString = server.getItemValueString(AdminNSFService.ITEM_SERVERXML);
									XMLDocument resultDoc = new XMLDocument();
									resultDoc.loadString(serverXmlString);
									return resultDoc;
								} catch(Exception e) {
									throw new RuntimeException(e);
								}
							});
							
							XMLNode httpEndpoint = serverXml.selectSingleNode("/server/httpEndpoint");
							if(httpEndpoint != null) {
								String host = httpEndpoint.getAttribute("host");
								if(host == null || host.isEmpty() || "*".equals(host)) {
									host = "localhost";
								}
								String httpPort = httpEndpoint.getAttribute("httpPort");
								boolean https = false;
								if(httpPort == null || httpPort.isEmpty()) {
									httpPort = httpEndpoint.getAttribute("httpsPort");
									https = true;
								}
								URI uri = URI.create(MessageFormat.format("http{0}://{1}:{2}/{3}", https ? "s" : "", host, httpPort, contextRoot));
								result.addTarget(contextRoot, uri);
							}
							
							
							entry.recycle(columnValues);
							ViewEntry tempEntry = entry;
							entry = nav.getNext();
							tempEntry.recycle();
						}
						
					} finally {
						session.recycle();
					}
					return;
				} catch(Throwable e) {
					e.printStackTrace(OpenLibertyLog.instance.out);
					throw new RuntimeException(e);
				}
			});
			
			if(result.isEnabled()) {
				// Determine the local server port from the server doc
				DominoThreadFactory.executor.submit(() -> {
					try {
						Session session = NotesFactory.createSession();
						try {
							String serverName = session.getUserName();
							
							Database names = session.getDatabase("", "names.nsf");
							View servers = names.getView("$Servers");
							Document serverDoc = servers.getDocumentByKey(serverName);
							
							boolean httpEnabled = "1".equals(serverDoc.getItemValueString("HTTP_NormalMode"));
							boolean httpsEnabled = "1".equals(serverDoc.getItemValueString("HTTP_SSLMode"));
							if(!httpEnabled && !httpsEnabled) {
								// Then HTTP is effectively off - end early
								result.setDominoHttpPort(ReverseProxyConfig.PORT_DISABLED);
								return;
							}
							
							if(httpEnabled) {
								result.setDominoHttpPort(serverDoc.getItemValueInteger("HTTP_Port"));
							} else {
								result.setDominoHttpPort(serverDoc.getItemValueInteger("HTTP_SSLPort"));
								result.setDominoHttps(true);
							}
							
							boolean bindToHostName = "1".equals(serverDoc.getItemValueString("HTTP_BindToHostName"));
							if(bindToHostName) {
								String hostName = serverDoc.getItemValueString("HTTP_HostName");
								if(hostName != null && !hostName.isEmpty()) {
									result.setDominoHostName(hostName);
								}
							}
						} finally {
							session.recycle();
						}
					} catch(Exception e) {
						e.printStackTrace(OpenLibertyLog.instance.out);
						throw new RuntimeException(e);
					}
				}).get();
			}
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace(OpenLibertyLog.instance.out);
			throw new RuntimeException(e);
		}
		
		
		
		return result;
	}
}
