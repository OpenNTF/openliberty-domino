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
package org.openntf.openliberty.wlp.userregistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.component.annotations.Component;

import com.ibm.websphere.security.WebTrustAssociationException;
import com.ibm.websphere.security.WebTrustAssociationFailedException;
import com.ibm.wsspi.security.tai.TAIResult;
import com.ibm.wsspi.security.tai.TrustAssociationInterceptor;

/**
 * This class provides one-way single-sign-on based on an active Domino session with
 * the backing server.
 * 
 * @author Jesse Gallagher
 * @since 1.18004.0
 */
@Component(
	service=TrustAssociationInterceptor.class,
	configurationPid=DominoTAI.CONFIG_PID,
	property={
		"invokeBeforeSSO:Boolean=true",
		"id=" + DominoTAI.CONFIG_PID
	}
)
public class DominoTAI implements TrustAssociationInterceptor {
	private static final Logger log = Logger.getLogger(DominoTAI.class.getPackage().getName());
	static {
		log.setLevel(Level.FINER);
	}
	
	public static final String CONFIG_PID = "org.openntf.openliberty.wlp.userregistry.DominoTAI"; //$NON-NLS-1$
	private static final String ENV_PROXY = System.getenv("Domino_HTTP"); //$NON-NLS-1$
	private static final boolean enabled = ENV_PROXY != null && !ENV_PROXY.isEmpty();
	
	// TODO make this customizable
	private static final Collection<String> COOKIES = Arrays.asList("DomAuthSessId", "LtpaToken", "LtpaToken2"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	
	public DominoTAI() {
	}

	@Override
	public int initialize(Properties props) throws WebTrustAssociationFailedException {
		if(log.isLoggable(Level.FINER)) {
			log.finer(MessageFormat.format(Messages.getString("DominoTAI.TAIEnabled"), getClass().getSimpleName(), enabled)); //$NON-NLS-1$
		}
		return 0;
	}
	
	@Override
	public void cleanup() {
		// NOP
	}

	@Override
	public String getType() {
		return CONFIG_PID;
	}

	@Override
	public String getVersion() {
		return "1.0"; //$NON-NLS-1$
	}

	@Override
	public boolean isTargetInterceptor(HttpServletRequest req) throws WebTrustAssociationException {
		if(!enabled) {
			return false;
		}
		
		Cookie[] cookies = req.getCookies();
		if(cookies == null || cookies.length == 0) {
			return false;
		}
		
		if(Arrays.stream(cookies).map(Cookie::getName).anyMatch(COOKIES::contains)) {
			if(log.isLoggable(Level.FINE)) {
				log.fine(MessageFormat.format(Messages.getString("DominoTAI.foundMatchingRequest"), getClass().getSimpleName())); //$NON-NLS-1$
			}
			return true;
		}
		
		if(log.isLoggable(Level.FINER)) {
			log.finer(MessageFormat.format(Messages.getString("DominoTAI.skippedNonMatchingRequest"), getClass().getSimpleName())); //$NON-NLS-1$
		}
		return false;
	}

	@Override
	public TAIResult negotiateValidateandEstablishTrust(HttpServletRequest req, HttpServletResponse resp)
			throws WebTrustAssociationFailedException {
		// We must have a match - check against the Domino server
		try {
			URL url = new URL(ENV_PROXY);
			url = new URL(url, "/org.openntf.openliberty.domino/whoami"); //$NON-NLS-1$
			HttpURLConnection conn = (HttpURLConnection)url.openConnection();
			try {
				conn.setRequestMethod("GET"); //$NON-NLS-1$
				conn.setRequestProperty("Host", url.getHost()); //$NON-NLS-1$
				
				if(req.getHeader("Cookie") != null) { //$NON-NLS-1$
					conn.setRequestProperty("Cookie", req.getHeader("Cookie")); //$NON-NLS-1$ //$NON-NLS-2$
				}
				if(req.getHeader("Authorization") != null) { //$NON-NLS-1$
					conn.setRequestProperty("Authorization", req.getHeader("Authorization")); //$NON-NLS-1$ //$NON-NLS-2$
				}
				conn.connect();
				String name;
				try(InputStream is = conn.getInputStream()) {
					try(BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
						name = r.readLine();
					}
				}
				if(log.isLoggable(Level.FINE)) {
					log.fine(MessageFormat.format(Messages.getString("DominoTAI.resolvedToUserName"), getClass().getSimpleName(), name)); //$NON-NLS-1$
				}
				if("Anonymous".equals(name)) { //$NON-NLS-1$
					name = "anonymous"; //$NON-NLS-1$
				}
				return TAIResult.create(HttpServletResponse.SC_OK, name);
			} finally {
				conn.disconnect();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
