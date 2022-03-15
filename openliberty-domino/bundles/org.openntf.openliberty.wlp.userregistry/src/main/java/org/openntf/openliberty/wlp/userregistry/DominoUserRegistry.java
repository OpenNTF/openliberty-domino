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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.service.component.annotations.Component;

import com.ibm.websphere.security.CertificateMapFailedException;
import com.ibm.websphere.security.CertificateMapNotSupportedException;
import com.ibm.websphere.security.CustomRegistryException;
import com.ibm.websphere.security.EntryNotFoundException;
import com.ibm.websphere.security.NotImplementedException;
import com.ibm.websphere.security.PasswordCheckFailedException;
import com.ibm.websphere.security.Result;
import com.ibm.websphere.security.UserRegistry;
import com.ibm.websphere.security.cred.WSCredential;

/**
 * This class provides a Liberty {@link UserRegistry} based on the effective directory
 * of the backing Domino server.
 * 
 * @author Jesse Gallagher
 * @since 1.18004.0
 * @see <a href="https://www.ibm.com/support/knowledgecenter/SSAW57_9.0.0/com.ibm.websphere.nd.multiplatform.doc/ae/tsec_users.html?view=kc">Developing the UserRegistry interface for using custom registries</a>
 */
@Component(service=UserRegistry.class, configurationPid=DominoUserRegistry.CONFIG_PID)
public class DominoUserRegistry implements UserRegistry {
	private static final Logger log = Logger.getLogger(DominoUserRegistry.class.getPackage().getName());
	static {
		log.setLevel(Level.FINER);
	}
	public static final String CONFIG_PID = "dominoUserRegistry"; //$NON-NLS-1$

	public DominoUserRegistry() {
		if(log.isLoggable(Level.FINER)) {
			log.finer(MessageFormat.format(Messages.getString("DominoUserRegistry.construct"), getClass().getSimpleName())); //$NON-NLS-1$
		}
	}

	@Override
	public void initialize(Properties props) throws CustomRegistryException, RemoteException {
		// Nothing to do here
		if(log.isLoggable(Level.FINER)) {
			log.finer(MessageFormat.format(Messages.getString("DominoUserRegistry.initializeWithProps"), getClass().getSimpleName(), props)); //$NON-NLS-1$
		}
	}
	
	@Override
	public String checkPassword(String userSecurityName, String password)
			throws PasswordCheckFailedException, CustomRegistryException, RemoteException {
		if(log.isLoggable(Level.FINE)) {
			log.fine(MessageFormat.format(Messages.getString("DominoUserRegistry.checkingPasswordForUser"), getClass().getSimpleName(), userSecurityName)); //$NON-NLS-1$
		}
		
		try {
			List<String> result = call("checkPassword", toMap("userSecurityName", userSecurityName, "password", password)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			if(isEmpty(result)) {
				return null;
			} else {
				return result.get(0);
			}
		} catch (IOException e) {
			throw new CustomRegistryException(e);
		}
	}

	@Override
	public String mapCertificate(X509Certificate[] cert) throws CertificateMapNotSupportedException,
			CertificateMapFailedException, CustomRegistryException, RemoteException {
		// Not yet implemented
		
		// return user DN
		return null;
	}

	@Override
	public String getRealm() throws CustomRegistryException, RemoteException {
		return "defaultRealm"; //$NON-NLS-1$
	}

	@Override
	public Result getUsers(String pattern, int limit) throws CustomRegistryException, RemoteException {
		try {
			List<String> users = call("getUsers", toMap("pattern", pattern, "limit", String.valueOf(limit))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			Result result = new Result();
			if(limit > users.size()) {
				users = users.subList(0, limit);
				result.setHasMore();
			}
			result.setList(users);
			return result;
		} catch (IOException e) {
			throw new CustomRegistryException(e);
		}
	}

	@Override
	public String getUserDisplayName(String userSecurityName)
			throws EntryNotFoundException, CustomRegistryException, RemoteException {
		if(log.isLoggable(Level.FINE)) {
			log.fine(MessageFormat.format(Messages.getString("DominoUserRegistry.gettingDisplayNameForUser"), getClass().getSimpleName(), userSecurityName)); //$NON-NLS-1$
		}
		try {
			List<String> result = call("getUserDisplayName", toMap("userSecurityName", userSecurityName)); //$NON-NLS-1$ //$NON-NLS-2$
			if(result == null || result.isEmpty()) {
				return null;
			} else {
				return result.get(0);
			}
		} catch (IOException e) {
			throw new CustomRegistryException(e);
		}
	}

	@Override
	public String getUniqueUserId(String userSecurityName) throws EntryNotFoundException, CustomRegistryException, RemoteException {
		try {
			List<String> result = call("getUniqueUserId", toMap("userSecurityName", userSecurityName)); //$NON-NLS-1$ //$NON-NLS-2$
			if(result == null || result.isEmpty()) {
				return null;
			} else {
				return result.get(0);
			}
		} catch (IOException e) {
			throw new CustomRegistryException(e);
		}
	}

	@Override
	public String getUserSecurityName(String uniqueUserId)
			throws EntryNotFoundException, CustomRegistryException, RemoteException {
		try {
			List<String> result = call("getUserSecurityName", toMap("uniqueUserId", uniqueUserId)); //$NON-NLS-1$ //$NON-NLS-2$
			if(result == null || result.isEmpty()) {
				return null;
			} else {
				return result.get(0);
			}
		} catch (IOException e) {
			throw new CustomRegistryException(e);
		}
		
	}

	@Override
	public boolean isValidUser(String userSecurityName) throws CustomRegistryException, RemoteException {
		String cn;
		try {
			cn = getUserSecurityName(userSecurityName);
		} catch (EntryNotFoundException e) {
			throw new CustomRegistryException(e);
		}
		return cn != null && !cn.isEmpty();
	}

	@Override
	public Result getGroups(String pattern, int limit) throws CustomRegistryException, RemoteException {
		try {
			List<String> users = call("getGroups", toMap("pattern", pattern, "limit", String.valueOf(limit))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			Result result = new Result();
			if(limit > users.size()) {
				users = users.subList(0, limit);
				result.setHasMore();
			}
			result.setList(users);
			return result;
		} catch (IOException e) {
			throw new CustomRegistryException(e);
		}
	}

	@Override
	public String getGroupDisplayName(String groupSecurityName)
			throws EntryNotFoundException, CustomRegistryException, RemoteException {
		// No such distinction
		return groupSecurityName;
	}

	@Override
	public String getUniqueGroupId(String groupSecurityName)
			throws EntryNotFoundException, CustomRegistryException, RemoteException {
		// No such distinction
		return groupSecurityName;
	}

	@Override
	public List<String> getUniqueGroupIds(String uniqueUserId)
			throws EntryNotFoundException, CustomRegistryException, RemoteException {
		try {
			return call("getUniqueGroupIds", toMap("uniqueUserId", uniqueUserId)); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (IOException e) {
			throw new CustomRegistryException(e);
		}
	}

	@Override
	public String getGroupSecurityName(String uniqueGroupId)
			throws EntryNotFoundException, CustomRegistryException, RemoteException {
		// No such distinction
		return uniqueGroupId;
	}

	@Override
	public boolean isValidGroup(String groupSecurityName) throws CustomRegistryException, RemoteException {
		try {
			List<String> result = call("isValidGroup", toMap("groupSecurityName", groupSecurityName)); //$NON-NLS-1$ //$NON-NLS-2$
			if(isEmpty(result)) {
				return false;
			} else {
				return Boolean.valueOf(result.get(0));
			}
		} catch (IOException e) {
			throw new CustomRegistryException(e);
		}
	}

	@Override
	public List<String> getGroupsForUser(String userSecurityName)
			throws EntryNotFoundException, CustomRegistryException, RemoteException {
		return getUniqueGroupIds(userSecurityName);
	}

	@Override
	public Result getUsersForGroup(String groupSecurityName, int limit)
			throws NotImplementedException, EntryNotFoundException, CustomRegistryException, RemoteException {
		try {
			List<String> users = call("getUsersForGroup", toMap("groupSecurityName", groupSecurityName, "limit", String.valueOf(limit))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			Result result = new Result();
			if(limit > users.size()) {
				users = users.subList(0, limit);
				result.setHasMore();
			}
			result.setList(users);
			return result;
		} catch (IOException e) {
			throw new CustomRegistryException(e);
		}
	}

	@Override
	public WSCredential createCredential(String userSecurityName)
			throws NotImplementedException, EntryNotFoundException, CustomRegistryException, RemoteException {
		// This is not yet implemented on the WLP side
		return null;
	}
	
	// *******************************************************************************
	// * HTTP method
	// *******************************************************************************
	
	private List<String> call(String methodName, Map<String, String> param) throws IOException {
		param.put("method", methodName); //$NON-NLS-1$
		String base = System.getenv("Domino_HTTP"); //$NON-NLS-1$
		if(base == null || base.isEmpty()) {
			return null;
		}
		if(!base.endsWith("/")) { //$NON-NLS-1$
			base += "/"; //$NON-NLS-1$
		}
		URL url = new URL(base);
		url = new URL(url, "/org.openntf.openliberty.domino/whoami"); //$NON-NLS-1$
		
		StringBuilder payload = new StringBuilder();
		for(Map.Entry<String, String> entry : param.entrySet()) {
			if(payload.length() > 0) {
				payload.append('&');
			}
			payload.append(entry.getKey());
			payload.append('=');
			payload.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
		}
		
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); //$NON-NLS-1$ //$NON-NLS-2$
		conn.setRequestProperty("Accept", "*/*"); //$NON-NLS-1$ //$NON-NLS-2$
		conn.setDoOutput(true);
		try(OutputStream os = conn.getOutputStream()) {
			os.write(payload.toString().getBytes());
		}
		
		List<String> result = new ArrayList<>();
		try(InputStream is = conn.getInputStream()) {
			BufferedReader r = new BufferedReader(new InputStreamReader(is));
			String line;
			while((line = r.readLine()) != null) {
				if(!line.isEmpty()) {
					result.add(line);
				}
			}
		}
		return result;
	}
	
	private Map<String, String> toMap(String... components) {
		Map<String, String> result = new LinkedHashMap<>();
		for(int i = 0; i < components.length; i += 2) { 
			result.put(components[i], components[i+1]);
		}
		return result;
	}
	
	private boolean isEmpty(List<String> result) {
		return result == null || result.isEmpty() || (result.size() == 1 && "".equals(result.get(0))); //$NON-NLS-1$
	}

}
