/*
 * Copyright © 2018-2021 Jesse Gallagher
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
package org.openntf.openliberty.domino.adminnsf.proxy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.MessageFormat;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ExecutionException;

import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.openntf.openliberty.domino.adminnsf.AdminNSFService;
import org.openntf.openliberty.domino.adminnsf.util.AdminNSFUtil;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyConfig;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyConfigProvider;
import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.xml.XMLDocument;
import org.openntf.openliberty.domino.util.xml.XMLNode;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.ViewEntry;
import lotus.domino.ViewNavigator;

public class AdminNSFProxyConfigProvider implements ReverseProxyConfigProvider {
	public static final String ITEM_REVERSEPROXYENABLE = "ReverseProxyEnable";
	public static final String ITEM_REVERSEPROXYTYPES = "ReverseProxyTypes";
	public static final String ITEM_REVERSEPROXYHOST = "ReverseProxyHostName";
	public static final String ITEM_REVERSEPROXYCONNECTORHEADERS = "ReverseProxyConnectorHeaders";
	
	public static final String ITEM_REVERSEPROXYHTTP = "ReverseProxyHTTP";
	public static final String ITEM_REVERSEPROXYHTTPPORT = "ReverseProxyHTTPPort";
	public static final String ITEM_REVERSEPROXYHTTPS = "ReverseProxyHTTPS";
	public static final String ITEM_REVERSEPROXYHTTPSPORT = "ReverseProxyHTTPSPort";
	public static final String ITEM_REVERSEPROXYHTTPSKEY = "ReverseProxyHTTPSKey";
	public static final String ITEM_REVERSEPROXYHTTPSCERT = "ReverseProxyHTTPSChain";
	
	public static final String VIEW_REVERSEPROXYAPPS = "ReverseProxyApps";

	@Override
	public ReverseProxyConfig createConfiguration() {
		ReverseProxyConfig result = new ReverseProxyConfig();
		
		
		try {
			// Load the main config
			DominoThreadFactory.executor.submit(() -> {
				try {
					Session session = NotesFactory.createSession();
					try {
						Database adminNsf = AdminNSFUtil.getAdminDatabase(session);
						Document config = AdminNSFUtil.getConfigurationDocument(adminNsf);
						
						boolean enable = "Y".equals(config.getItemValueString(ITEM_REVERSEPROXYENABLE));
						result.setGlobalEnabled(enable);
						if(!enable) {
							return;
						}
						
						@SuppressWarnings("unchecked")
						List<String> enabledTypes = config.getItemValue(ITEM_REVERSEPROXYTYPES);
						enabledTypes.forEach(result::addEnabledType);
						
						String hostName = config.getItemValueString(ITEM_REVERSEPROXYHOST);
						if(hostName == null || hostName.isEmpty()) {
							hostName = "0.0.0.0";
						}
						result.proxyHostName = hostName;
						boolean connectorHeaders = "Y".equals(config.getItemValueString(ITEM_REVERSEPROXYCONNECTORHEADERS));
						if(connectorHeaders) {
							result.useDominoConnectorHeaders = connectorHeaders;
							String secret = session.getEnvironmentString("HTTPConnectorHeadersSecret", true);
							result.dominoConnectorHeadersSecret = secret;
						}
						
						// Check for HTTP
						boolean enableHttp = "Y".equals(config.getItemValueString(ITEM_REVERSEPROXYHTTP));
						if(enableHttp) {
							if(enableHttp) {
								int port = config.getItemValueInteger(ITEM_REVERSEPROXYHTTPPORT);
								result.proxyHttpPort = port;
							}
						}
						
						// Check for HTTPS
						boolean enableHttps = "Y".equals(config.getItemValueString(ITEM_REVERSEPROXYHTTPS));
						if(enableHttps) {
							int port = config.getItemValueInteger(ITEM_REVERSEPROXYHTTPSPORT);
							result.proxyHttpsPort = port;
							
							String privateKeyPem = config.getItemValueString(ITEM_REVERSEPROXYHTTPSKEY);
							char[] password = Long.toString(System.currentTimeMillis()).toCharArray();
							RSAPrivateKey privateKey = readPrivateKey(privateKeyPem);
							
							String certsPem = config.getItemValueString(ITEM_REVERSEPROXYHTTPSCERT);
							CertificateFactory fac = CertificateFactory.getInstance("X.509");
							Collection<? extends Certificate> certs;
							try(ByteArrayInputStream bais = new ByteArrayInputStream(certsPem.getBytes())) {
								certs = fac.generateCertificates(bais);
							}
							
							KeyStore keystore = KeyStore.getInstance("PKCS12");
							keystore.load(null, password);
							keystore.setKeyEntry("default", privateKey, password, certs.toArray(new Certificate[certs.size()]));
							TrustManager[] trustManagers = buildTrustManagers(keystore);
							KeyManager[] keyManagers = buildKeyManagers(keystore, password);
							
							SSLContext sslContext = SSLContext.getInstance("TLS");
							sslContext.init(keyManagers, trustManagers, null);
							result.proxyHttpsContext = sslContext;
						}
						
						// Look for proxy-enabled webapps
						Map<String, XMLDocument> serverXmls = new HashMap<>();
						View apps = adminNsf.getView(VIEW_REVERSEPROXYAPPS);
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

							// TODO participate in any random-numbering scheme available - could read from the filesystem.
							//   Alternatively, this could read from the fields in the server doc, if not using randomized ports
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
			}).get();
			
			if(result.isGlobalEnabled()) {
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
								result.dominoHttpPort = ReverseProxyConfig.PORT_DISABLED;
								return;
							}
							
							if(httpEnabled) {
								result.dominoHttpPort = serverDoc.getItemValueInteger("HTTP_Port");
							} else {
								result.dominoHttpPort = serverDoc.getItemValueInteger("HTTP_SSLPort");
								result.dominoHttps = true;
							}
							
							boolean bindToHostName = "1".equals(serverDoc.getItemValueString("HTTP_BindToHostName"));
							if(bindToHostName) {
								String hostName = serverDoc.getItemValueString("HTTP_HostName");
								if(hostName != null && !hostName.isEmpty()) {
									result.dominoHostName = hostName;
								}
							}
							
							// Mirror Domino's maximum entity size
							long maxEntitySize = serverDoc.getItemValueInteger("HTTP_MaxContentLength");
							if(maxEntitySize == 0) {
								maxEntitySize = Long.MAX_VALUE;
							}
							result.maxEntitySize = maxEntitySize;
							
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
	
	private RSAPrivateKey readPrivateKey(String key) throws InvalidKeySpecException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IOException {
		String privateKeyPEM = key
	      .replace("-----BEGIN PRIVATE KEY-----", "")
	      .replaceAll(System.lineSeparator(), "")
	      .replace("-----END PRIVATE KEY-----", "");

	    byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);

	    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
	    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
	    return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
	}

    
	private static TrustManager[] buildTrustManagers(final KeyStore trustStore) throws IOException {
        TrustManager[] trustManagers = null;
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory
                .getInstance(KeyManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            trustManagers = trustManagerFactory.getTrustManagers();
        }
        catch (NoSuchAlgorithmException | KeyStoreException exc) {
            throw new IOException("Unable to initialise TrustManager[]", exc);
        }
        return trustManagers;
    }
	private static KeyManager[] buildKeyManagers(final KeyStore keyStore, char[] storePassword) throws IOException {
	    KeyManager[] keyManagers;
	    try {
	        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory
	            .getDefaultAlgorithm());
	        keyManagerFactory.init(keyStore, storePassword);
	        keyManagers = keyManagerFactory.getKeyManagers();
	    }
	    catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException exc) {
	        throw new IOException("Unable to initialise KeyManager[]", exc);
	    }
	    return keyManagers;
	}
}
