/**
 * Copyright © 2018-2020 Jesse Gallagher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openntf.openliberty.domino.httpservice;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openntf.openliberty.domino.ext.RuntimeService;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.darwino.domino.napi.DominoAPI;
import com.ibm.commons.util.StringUtil;
import com.ibm.commons.xml.DOMUtil;
import com.ibm.commons.xml.XMLException;
import com.ibm.commons.xml.XResult;

public class ServerStatusLineService implements RuntimeService {
	
	private final Map<Path, Long> statusLines = new HashMap<>();
	private final Object deleteSync = new Object();

	@Override
	public void run() {
		// Run until canceled
		try {
			while(true) {
				TimeUnit.DAYS.sleep(1);
			}
		} catch(InterruptedException e) {
			// Good
		} finally {
			synchronized(deleteSync) {
				statusLines.forEach((path, hDesc) -> DominoAPI.get().AddInDeleteStatusLine(hDesc));
			}
		}
	}

	@Override
	public void notifyServerStart(Path wlp, String serverName) {
		synchronized(deleteSync) {
			Path serverXml = wlp.resolve("usr").resolve("servers").resolve(serverName).resolve("server.xml"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			if(Files.isRegularFile(serverXml)) {
				statusLines.computeIfAbsent(serverXml, p -> {
					long result = DominoAPI.get().AddInCreateStatusLine("Open Liberty");
					DominoAPI.get().AddInSetStatusLine(result, MessageFormat.format("{0}: Running", serverName));
					return result;
				});
				
				updateStatusLine(wlp, serverName);
			}
		}
	}
	
	@Override
	public void notifyServerStop(Path wlp, String serverName) {
		synchronized(deleteSync) {
			Path serverXml = wlp.resolve("usr").resolve("servers").resolve(serverName).resolve("server.xml"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			Long hDesc = statusLines.get(serverXml);
			if(hDesc != null) {
				DominoAPI.get().AddInDeleteStatusLine(hDesc);
			}
		}
	}
	
	@Override
	public void notifyServerDeploy(Path wlp, String serverName) {
		synchronized(deleteSync) {
			updateStatusLine(wlp, serverName);
		}
	}
	
	// *******************************************************************************
	// * Internal utility methods
	// *******************************************************************************
	
	private void updateStatusLine(Path wlp, String serverName) {
		Path serverXml = wlp.resolve("usr").resolve("servers").resolve(serverName).resolve("server.xml"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		if(Files.isRegularFile(serverXml)) {
			Long hDesc = statusLines.get(serverXml);
			if(hDesc != null) {
				// Parse the server.xml for port information
				try {
					Document xml;
					try(InputStream is = Files.newInputStream(serverXml)) {
						xml = DOMUtil.createDocument(is);
					}
					XResult res = DOMUtil.evaluateXPath(xml.getDocumentElement(), "/server/httpEndpoint"); //$NON-NLS-1$
					Object[] nodes = res.getNodes();
					if(nodes != null && nodes.length > 0) {
						// Last one wins in WLP
						Element node = (Element)nodes[nodes.length-1];
						String host = node.getAttribute("host"); //$NON-NLS-1$
						if(StringUtil.isEmpty(host)) {
							host = InetAddress.getLocalHost().getHostName();
						}
						String httpPort = node.getAttribute("httpPort"); //$NON-NLS-1$
						if(StringUtil.isEmpty(httpPort)) {
							// This seems to be the default when unspecified
							httpPort = "9080"; //$NON-NLS-1$
						}
						String httpsPort = node.getAttribute("httpsPort"); //$NON-NLS-1$
						String ports = Stream.of(httpPort, httpsPort)
							.filter(StringUtil::isNotEmpty)
							.filter(p -> !"-1".equals(p)) //$NON-NLS-1$
							.collect(Collectors.joining(",")); //$NON-NLS-1$
						if(StringUtil.isNotEmpty(ports)) {
							String status = MessageFormat.format("{0}: Listening on {1}:{2}", serverName, host, ports);
							DominoAPI.get().AddInSetStatusLine(hDesc, status);
						}
					}
				} catch(XMLException | IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
