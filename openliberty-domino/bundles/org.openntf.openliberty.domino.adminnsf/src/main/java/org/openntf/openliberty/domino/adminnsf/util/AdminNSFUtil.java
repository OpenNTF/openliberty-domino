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
package org.openntf.openliberty.domino.adminnsf.util;

import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import org.openntf.openliberty.domino.adminnsf.AdminNSFService;
import org.openntf.openliberty.domino.adminnsf.config.AdminNSFProperties;
import org.openntf.openliberty.domino.util.DominoThreadFactory;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.Name;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.domino.View;

public enum AdminNSFUtil {
	;
	
	public static Database getAdminDatabase(Session session) throws NotesException {
		String adminNsfPath = AdminNSFProperties.instance.getNsfPath();
		String server, filePath;
		int bangIndex = adminNsfPath.indexOf("!!"); //$NON-NLS-1$
		if(bangIndex > -1) {
			server = adminNsfPath.substring(0, bangIndex);
			filePath = adminNsfPath.substring(bangIndex+2);
		} else {
			server = ""; //$NON-NLS-1$
			filePath = adminNsfPath;
		}
		
		return session.getDatabase(server, filePath);
	}
	
	public static Document getConfigurationDocument(Database adminNsf) throws NotesException {
		View configuration = adminNsf.getView(AdminNSFService.VIEW_CONFIGURATION);
		configuration.setAutoUpdate(false);
		try {
			configuration.refresh();
			
			Document doc = configuration.getDocumentByKey(adminNsf.getParent().getUserName(), true);
			if(doc == null) {
				doc = configuration.getDocumentByKey("", true); //$NON-NLS-1$
			}
			
			if(doc != null) {
				return doc;
			} else {
				doc = adminNsf.createDocument();
				doc.replaceItemValue("Form", "Configuration"); //$NON-NLS-1$ //$NON-NLS-2$
				return doc;
			}
		} finally {
			configuration.recycle();
		}
	}
	
	public static boolean isNamesListMatch(Collection<String> currentNamesList, Collection<String> allowedList) {
		Collection<String> serverNames = new HashSet<>(allowedList);
		serverNames.remove(""); //$NON-NLS-1$
		serverNames.remove(null);
		if(!serverNames.isEmpty()) {
			for(String serverName : serverNames) {
				if(currentNamesList.contains(serverName)) {
					return true;
				}
			}
			return false;
		} else {
			return true;
		}
	}
	
	public static Collection<String> getCurrentServerNamesList() {
		try {
			return DominoThreadFactory.getExecutor().submit(() -> {
				return AccessController.doPrivileged((PrivilegedExceptionAction<Collection<String>>)() -> {
					Session session = NotesFactory.createSession();
					try {
						Class<?> dominoServerClass = ClassLoader.getSystemClassLoader().loadClass("lotus.notes.addins.DominoServer"); //$NON-NLS-1$
						Method getNamesList = dominoServerClass.getMethod("getNamesList", String.class); //$NON-NLS-1$
						Object dominoServer = dominoServerClass.getConstructor(new Class<?>[0]).newInstance();
						@SuppressWarnings("unchecked")
						Collection<String> names = (Collection<String>)getNamesList.invoke(dominoServer, session.getUserName());

						Collection<String> namesList = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
						namesList.addAll(names);
						
						// The abbreviated name _shouldn't_ make it into the names field, but just in case
						Name nameObj = session.getUserNameObject();
						try {
							namesList.add(nameObj.getAbbreviated());
						} finally {
							nameObj.recycle();
						}
						
						return namesList;
					} finally {
						session.recycle();
					}
				});
			}).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}
}
