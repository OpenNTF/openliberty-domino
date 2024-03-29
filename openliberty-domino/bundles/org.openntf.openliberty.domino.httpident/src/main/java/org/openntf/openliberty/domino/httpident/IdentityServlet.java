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
package org.openntf.openliberty.domino.httpident;

import com.ibm.commons.util.io.StreamUtil;
import com.ibm.domino.osgi.core.context.ContextInfo;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.Name;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.notes.addins.DominoServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class IdentityServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	public static final String INI_LOCALONLY = "WLP_IdentityServlet_LocalOnly"; //$NON-NLS-1$
	
	// Set up a poor man's API
	public enum Method {
		Identity, checkPassword, getUsers, getUserDisplayName, getUniqueUserId, getUserSecurityName, getGroups,
		getUniqueGroupIds, isValidGroup, getUsersForGroup
	}
	
	private boolean localOnly = true;
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		
		try {
			Session session = NotesFactory.createSession();
			try {
				String localOnlyConfig = session.getEnvironmentString(INI_LOCALONLY, true);
				if("0".equals(localOnlyConfig)) { //$NON-NLS-1$
					localOnly = false;
				}
			} finally {
				session.recycle();
			}
		} catch(NotesException e) {
			throw new ServletException(e);
		}
	}
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		handle(req, resp);
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		handle(req, resp);
	}
	
	protected void handle(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		if(localOnly && !isLocal(req.getRemoteAddr())) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return;
		}
		
		try(ServletOutputStream os = resp.getOutputStream()) {
			resp.setContentType("text/plain"); //$NON-NLS-1$
			
			Map<String, String> param = getPost(req);
			String methodParam = param.get("method"); //$NON-NLS-1$
			Method method;
			if(methodParam == null || methodParam.isEmpty()) {
				method = Method.Identity;
			} else {
				method = Method.valueOf(methodParam);
			}

			switch(method) {
			case Identity:
				os.print(identity());
				break;
			case checkPassword:
				os.print(checkPassword(param.get("userSecurityName"), param.get("password"))); //$NON-NLS-1$ //$NON-NLS-2$
				break;
			case getUsers:
				os.print(getUsers(param.get("pattern"), Integer.valueOf(param.get("limit")))); //$NON-NLS-1$ //$NON-NLS-2$
				break;
			case getUserDisplayName:
				os.print(getUserDisplayName(param.get("userSecurityName"))); //$NON-NLS-1$
				break;
			case getUniqueUserId:
				os.print(getUniqueUserId(param.get("userSecurityName"))); //$NON-NLS-1$
				break;
			case getUserSecurityName:
				os.print(getUserSecurityName(param.get("uniqueUserId"))); //$NON-NLS-1$
				break;
			case getGroups:
				os.print(getGroups(param.get("pattern"), Integer.valueOf(param.get("limit")))); //$NON-NLS-1$ //$NON-NLS-2$
				break;
			case getUniqueGroupIds:
				os.print(getUniqueGroupIds(param.get("uniqueUserId"))); //$NON-NLS-1$
				break;
			case isValidGroup:
				os.print(isValidGroup(param.get("groupSecurityName"))); //$NON-NLS-1$
				break;
			case getUsersForGroup:
				os.print(getUsersForGroup(param.get("groupSecurityName"), Integer.valueOf(param.get("limit")))); //$NON-NLS-1$ //$NON-NLS-2$
				break;
			default:
				break;
			}
		} catch (NotesException e) {
			throw new ServletException(e);
		}
	}
	
	private String identity() throws IOException, ServletException {
		Session session = ContextInfo.getUserSession();
		try {
			try {
				return session.getEffectiveUserName();
			} catch (NotesException e) {
				throw new ServletException(e);
			}
		} finally {
			try {
				session.recycle();
			} catch (NotesException e) { }
		}
	}
	
	private String checkPassword(String userSecurityName, String password) throws NotesException, IOException {
		Session session = NotesFactory.createSession();
		try {
			Database names = session.getDatabase("", "names.nsf"); //$NON-NLS-1$ //$NON-NLS-2$
			Document tempDoc = names.createDocument();
			tempDoc.replaceItemValue("Username", userSecurityName); //$NON-NLS-1$
			tempDoc.replaceItemValue("Password", password); //$NON-NLS-1$
			session.evaluate(" @SetField('HashPassword'; @NameLookup([NoCache]:[Exhaustive]; Username; 'HTTPPassword')[1]) ", tempDoc); //$NON-NLS-1$
			// TODO look up against other password variants, or find real way to do this
			List<?> result = session.evaluate(" @VerifyPassword(Password; HashPassword) ", tempDoc); //$NON-NLS-1$
			if(!result.isEmpty() && Double.valueOf(1).equals(result.get(0))) {
				// Then it's good! Look up the user's real name
				return (String)session.evaluate(" @NameLookup([NoCache]:[Exhaustive]; Username; 'FullName') ", tempDoc).get(0); //$NON-NLS-1$
			} else {
				return ""; //$NON-NLS-1$
			}
		} catch(Throwable t) {
			t.printStackTrace();
			throw t;
		} finally {
			session.recycle();
		}
	}
	
	@SuppressWarnings("unchecked")
	private String getUsers(String pattern, int limit) throws NotesException {
		Session session = NotesFactory.createSession();
		try {
			// TODO change API to avoid 64k trouble
			Database names = session.getDatabase("", "names.nsf"); //$NON-NLS-1$ //$NON-NLS-2$
			Document tempDoc = names.createDocument();
			List<String> users = session.evaluate(" @Trim(@Sort(@Unique(@NameLookup([NoCache]:[Exhaustive]; ''; 'FullName')))) ", tempDoc); //$NON-NLS-1$
			return String.join("\n", users); //$NON-NLS-1$
		} finally {
			session.recycle();
		}
	}
	
	private String getUserDisplayName(String userSecurityName) throws NotesException {
		Session session = NotesFactory.createSession();
		try {
			Database names = session.getDatabase("", "names.nsf"); //$NON-NLS-1$ //$NON-NLS-2$
			Document tempDoc = names.createDocument();
			tempDoc.replaceItemValue("Username", userSecurityName); //$NON-NLS-1$
			List<?> result = session.evaluate(" @Trim(@NameLookup([NoCache]:[Exhaustive]; Username; 'FullName')) ", tempDoc); //$NON-NLS-1$
			if(!result.isEmpty()) {
				Name name = session.createName((String)result.get(0));
				return name.getCommon();
			} else {
				return ""; //$NON-NLS-1$
			}
		} finally {
			session.recycle();
		}
	}
	
	private String getUniqueUserId(String userSecurityName) throws NotesException {
		Session session = NotesFactory.createSession();
		try {
			Database names = session.getDatabase("", "names.nsf"); //$NON-NLS-1$ //$NON-NLS-2$
			Document tempDoc = names.createDocument();
			tempDoc.replaceItemValue("Username", userSecurityName); //$NON-NLS-1$
			List<?> result = session.evaluate(" @Trim(@NameLookup([NoCache]:[Exhaustive]; Username; 'ShortName')) ", tempDoc); //$NON-NLS-1$
			if(!result.isEmpty()) {
				return (String)result.get(0);
			} else {
				return ""; //$NON-NLS-1$
			}
		} finally {
			session.recycle();
		}
	}
	
	private String getUserSecurityName(String uniqueUserId) throws NotesException {
		Session session = NotesFactory.createSession();
		try {
			Database names = session.getDatabase("", "names.nsf"); //$NON-NLS-1$ //$NON-NLS-2$
			Document tempDoc = names.createDocument();
			tempDoc.replaceItemValue("Username", uniqueUserId); //$NON-NLS-1$
			List<?> result = session.evaluate(" @Trim(@NameLookup([NoCache]:[Exhaustive]; Username; 'FullName')) ", tempDoc); //$NON-NLS-1$
			if(!result.isEmpty()) {
				return (String)result.get(0);
			} else {
				return ""; //$NON-NLS-1$
			}
		} finally {
			session.recycle();
		}
	}
	
	@SuppressWarnings("unchecked")
	public String getGroups(String pattern, int limit) throws NotesException {
		Session session = NotesFactory.createSession();
		try {
			Database names = session.getDatabase("", "names.nsf"); //$NON-NLS-1$ //$NON-NLS-2$
			Document tempDoc = names.createDocument();
			List<String> groups = session.evaluate(" @Trim(@Sort(@Unique(@NameLookup([NoCache]:[Exhaustive]; ''; 'ListName')))) ", tempDoc); //$NON-NLS-1$
			return String.join("\n", groups); //$NON-NLS-1$
		} finally {
			session.recycle();
		}
	}
	
	@SuppressWarnings("unchecked")
	private String getUniqueGroupIds(String uniqueUserId) throws NotesException {
		Session session = NotesFactory.createSession();
		try {
			DominoServer server = new DominoServer(session.getUserName());
			String name = getUserSecurityName(uniqueUserId);
			List<String> names = new ArrayList<>((Collection<String>)server.getNamesList(name));
			int starIndex = names.indexOf("*"); //$NON-NLS-1$
			if(starIndex > -1) {
				// Everything at and after this point should be a group or
				//   pseudo-group (e.g. "*/O=SomeOrg")
				names = names.subList(starIndex, names.size());
			}
			return String.join("\n", names); //$NON-NLS-1$
		} finally {
			session.recycle();
		}
	}
	
	public String isValidGroup(String groupSecurityName) throws NotesException  {
		Session session = NotesFactory.createSession();
		try {
			Database names = session.getDatabase("", "names.nsf"); //$NON-NLS-1$ //$NON-NLS-2$
			Document tempDoc = names.createDocument();
			tempDoc.replaceItemValue("GroupName", groupSecurityName); //$NON-NLS-1$
			List<?> result = session.evaluate(" @Trim(@NameLookup([NoCache]:[Exhaustive]; GroupName; 'ListName')) ", tempDoc); //$NON-NLS-1$
			return String.valueOf(!result.isEmpty());
		} finally {
			session.recycle();
		}
	}
	
	@SuppressWarnings("unchecked")
	public String getUsersForGroup(String groupSecurityName, int limit) throws NotesException {
		Session session = NotesFactory.createSession();
		try {
			// TODO Look up and expand group
			// TODO work with multiple group-allowed directories
			Database names = session.getDatabase("", "names.nsf"); //$NON-NLS-1$ //$NON-NLS-2$
			Document tempDoc = names.createDocument();
			tempDoc.replaceItemValue("GroupName", groupSecurityName); //$NON-NLS-1$
			List<String> members = session.evaluate(" @Text(@Trim(@Unique(@Sort(@DbLookup(''; '':'names.nsf'; '$VIMGroups'; GroupName; 'Members'))))) ", tempDoc); //$NON-NLS-1$
			return String.join("\n", members); //$NON-NLS-1$
		} finally {
			session.recycle();
		}
	}
	
	// *******************************************************************************
	// * Internal utility methods
	// *******************************************************************************
	
	private Map<String, String> getPost(HttpServletRequest req) throws IOException {
		Map<String, String> result = new HashMap<>();
		String content;
		try(InputStream is = req.getInputStream()) {
			if(is == null) {
				return Collections.emptyMap();
			}
			content = StreamUtil.readString(is);
		}
		
		String[] parts = content.split("&"); //$NON-NLS-1$
		for(String part : parts) {
			int eqIndex = part.indexOf('=');
			if(eqIndex < 0) {
				result.put(part, ""); //$NON-NLS-1$
			} else {
				String key = part.substring(0, eqIndex);
				String value = URLDecoder.decode(part.substring(eqIndex+1), StandardCharsets.UTF_8.name());
				result.put(key, value);
			}
		}
		
		return result;
	}
	
	private boolean isLocal(String ipAddress) {
		try {
			InetAddress addr = InetAddress.getByName(ipAddress);
			if(addr.isAnyLocalAddress() || addr.isLoopbackAddress()) {
				return true;
			}
			try {
				return NetworkInterface.getByInetAddress(addr) != null;
			} catch(SocketException e) {
				return false;
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
