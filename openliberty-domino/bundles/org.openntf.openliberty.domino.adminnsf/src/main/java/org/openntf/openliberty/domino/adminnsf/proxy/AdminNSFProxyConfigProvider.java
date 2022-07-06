/*
 * Copyright © 2018-2022 Jesse Gallagher
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
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutionException;

import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.openntf.openliberty.domino.adminnsf.util.AdminNSFUtil;
import org.openntf.openliberty.domino.config.RuntimeConfigurationProvider;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyConfig;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyConfigProvider;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyTarget;
import org.openntf.openliberty.domino.util.DominoThreadFactory;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.domino.View;
import lotus.domino.ViewEntry;
import lotus.domino.ViewNavigator;

public class AdminNSFProxyConfigProvider implements ReverseProxyConfigProvider {
	public static final String ITEM_REVERSEPROXYENABLE = "ReverseProxyEnable"; //$NON-NLS-1$
	public static final String ITEM_REVERSEPROXYTYPES = "ReverseProxyTypes"; //$NON-NLS-1$
	public static final String ITEM_REVERSEPROXYHOST = "ReverseProxyHostName"; //$NON-NLS-1$
	
	public static final String ITEM_REVERSEPROXYHTTP = "ReverseProxyHTTP"; //$NON-NLS-1$
	public static final String ITEM_REVERSEPROXYHTTPPORT = "ReverseProxyHTTPPort"; //$NON-NLS-1$
	public static final String ITEM_REVERSEPROXYHTTPS = "ReverseProxyHTTPS"; //$NON-NLS-1$
	public static final String ITEM_REVERSEPROXYHTTPSPORT = "ReverseProxyHTTPSPort"; //$NON-NLS-1$
	public static final String ITEM_REVERSEPROXYHTTPSKEY = "ReverseProxyHTTPSKey"; //$NON-NLS-1$
	public static final String ITEM_REVERSEPROXYHTTPSCERT = "ReverseProxyHTTPSChain"; //$NON-NLS-1$
	
	public static final String VIEW_REVERSEPROXYTARGETS = "ReverseProxyTargets"; //$NON-NLS-1$

	@SuppressWarnings("unchecked")
	@Override
	public ReverseProxyConfig createConfiguration() {
		ReverseProxyConfig result = new ReverseProxyConfig();
		
		RuntimeConfigurationProvider runtimeConfig = OpenLibertyUtil.findRequiredExtension(RuntimeConfigurationProvider.class);
		result.dominoHostName = runtimeConfig.getDominoHostName();
		result.dominoHttpPort = runtimeConfig.getDominoPort();
		result.dominoHttps = runtimeConfig.isDominoHttps();
		
		try {
			DominoThreadFactory.getExecutor().submit(() -> {
				try {
					Session session = NotesFactory.createSession();
					try {
						Database adminNsf = AdminNSFUtil.getAdminDatabase(session);
						Document config = AdminNSFUtil.getConfigurationDocument(adminNsf);

						// Load the main config
						boolean connectorHeaders = runtimeConfig.isUseDominoConnectorHeaders();
						readConfigurationDocument(result, config, connectorHeaders);
						if(!result.isGlobalEnabled()) {
							return;
						}
						
						Collection<String> namesList = AdminNSFUtil.getCurrentServerNamesList();
						
						// Look for proxy-enabled webapps
						View targetsView = adminNsf.getView(VIEW_REVERSEPROXYTARGETS);
						targetsView.setAutoUpdate(false);
						targetsView.refresh();
						ViewNavigator nav = targetsView.createViewNav();
						nav.setEntryOptions(ViewNavigator.VN_ENTRYOPT_NOCOUNTDATA);
						ViewEntry entry = nav.getFirst();
						
						while(entry != null) {
							Vector<?> columnValues = entry.getColumnValues();
							List<String> dominoServers;
							Object dominoServersObj = columnValues.get(4);
							if(dominoServersObj instanceof String) {
								dominoServers = Arrays.asList((String)dominoServersObj);
							} else {
								dominoServers = (List<String>)dominoServersObj;
							}
							boolean shouldRun = AdminNSFUtil.isNamesListMatch(namesList, dominoServers);
							if(shouldRun) {
								// Format: http://localhost:80
								String baseUri = (String)columnValues.get(0);
								String contextPath = (String)columnValues.get(1);
								boolean useXForwardedFor = "Y".equals(columnValues.get(2)); //$NON-NLS-1$
								boolean useWsHeaders = "Y".equals(columnValues.get(3)); //$NON-NLS-1$
								
								URI uri = URI.create(baseUri + "/" + contextPath); //$NON-NLS-1$
								ReverseProxyTarget target = new ReverseProxyTarget(uri, useXForwardedFor, useWsHeaders);
								result.addTarget(contextPath, target);
							}
							
							entry.recycle(columnValues);
							ViewEntry tempEntry = entry;
							entry = nav.getNextSibling(entry);
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
				DominoThreadFactory.getExecutor().submit(() -> {
					try {
						Session session = NotesFactory.createSession();
						try {
							String serverName = session.getUserName();
							
							Database names = session.getDatabase("", "names.nsf"); //$NON-NLS-1$ //$NON-NLS-2$
							View servers = names.getView("$Servers"); //$NON-NLS-1$
							Document serverDoc = servers.getDocumentByKey(serverName);
							
							// Mirror Domino's maximum entity size
							long maxEntitySize = serverDoc.getItemValueInteger("HTTP_MaxContentLength"); //$NON-NLS-1$
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
	
	public static void readConfigurationDocument(ReverseProxyConfig result, Document config, boolean useDominoConnectorHeaders) {
		try {
			boolean enable = "Y".equals(config.getItemValueString(ITEM_REVERSEPROXYENABLE)); //$NON-NLS-1$
			result.setGlobalEnabled(enable);
			if(!enable) {
				return;
			}
			
			@SuppressWarnings("unchecked")
			List<String> enabledTypes = config.getItemValue(ITEM_REVERSEPROXYTYPES);
			enabledTypes.forEach(result::addEnabledType);
			
			String hostName = config.getItemValueString(ITEM_REVERSEPROXYHOST);
			if(hostName == null || hostName.isEmpty()) {
				hostName = "0.0.0.0"; //$NON-NLS-1$
			}
			result.proxyHostName = hostName;
			if(useDominoConnectorHeaders) {
				result.useDominoConnectorHeaders = true;
				String secret = config.getParentDatabase().getParent().getEnvironmentString("HTTPConnectorHeadersSecret", true); //$NON-NLS-1$
				result.dominoConnectorHeadersSecret = secret;
			}
			
			// Check for HTTP
			String httpVal = config.getItemValueString(ITEM_REVERSEPROXYHTTP);
			boolean enableHttp = "Y".equals(httpVal) || "Redirect".equals(httpVal); //$NON-NLS-1$ //$NON-NLS-2$
			if(enableHttp) {
				int port = config.getItemValueInteger(ITEM_REVERSEPROXYHTTPPORT);
				result.proxyHttpPort = port;
			}
			if("Redirect".equals(httpVal)) { //$NON-NLS-1$
				result.redirectHttpToHttps = true;
			}
			
			// Check for HTTPS
			boolean enableHttps = "Y".equals(config.getItemValueString(ITEM_REVERSEPROXYHTTPS)); //$NON-NLS-1$
			if(enableHttps) {
				int port = config.getItemValueInteger(ITEM_REVERSEPROXYHTTPSPORT);
				result.proxyHttpsPort = port;
				
				String privateKeyPem = config.getItemValueString(ITEM_REVERSEPROXYHTTPSKEY);
				char[] password = Long.toString(System.currentTimeMillis()).toCharArray();
				RSAPrivateKey privateKey = readPrivateKey(privateKeyPem);
				
				String certsPem = config.getItemValueString(ITEM_REVERSEPROXYHTTPSCERT);
				CertificateFactory fac = CertificateFactory.getInstance("X.509"); //$NON-NLS-1$
				Collection<? extends Certificate> certs;
				try(ByteArrayInputStream bais = new ByteArrayInputStream(certsPem.getBytes())) {
					certs = fac.generateCertificates(bais);
				}
				
				KeyStore keystore = KeyStore.getInstance("PKCS12"); //$NON-NLS-1$
				keystore.load(null, password);
				keystore.setKeyEntry("default", privateKey, password, certs.toArray(new Certificate[certs.size()])); //$NON-NLS-1$
				TrustManager[] trustManagers = buildTrustManagers(keystore);
				KeyManager[] keyManagers = buildKeyManagers(keystore, password);
				
				SSLContext sslContext = SSLContext.getInstance("TLS"); //$NON-NLS-1$
				sslContext.init(keyManagers, trustManagers, null);
				result.proxyHttpsContext = sslContext;
			}
		} catch(NotesException | CertificateException | NoSuchAlgorithmException | KeyManagementException | KeyStoreException | IOException | InvalidKeyException | InvalidKeySpecException | NoSuchPaddingException | InvalidAlgorithmParameterException e) {
			throw new RuntimeException(e);
		}
	}
	
	// *******************************************************************************
	// * Internal implementation methods
	// *******************************************************************************
	
	private static RSAPrivateKey readPrivateKey(String key) throws InvalidKeySpecException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IOException {
		String privateKeyPEM = key
	      .replace("-----BEGIN PRIVATE KEY-----", "") //$NON-NLS-1$ //$NON-NLS-2$
	      .replaceAll(System.lineSeparator(), "") //$NON-NLS-1$
	      .replace("-----END PRIVATE KEY-----", ""); //$NON-NLS-1$ //$NON-NLS-2$

	    byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);

	    KeyFactory keyFactory = KeyFactory.getInstance("RSA"); //$NON-NLS-1$
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
