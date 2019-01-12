/**
 * Copyright © 2018-2019 Jesse Gallagher
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

import org.openntf.openliberty.domino.adminnsf.config.AdminNSFProperties;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.Session;
import lotus.domino.View;

public enum AdminNSFUtil {
	;
	
	public static Database getAdminDatabase(Session session) throws NotesException {
		String adminNsfPath = AdminNSFProperties.instance.getNsfPath();
		String server, filePath;
		int bangIndex = adminNsfPath.indexOf("!!");
		if(bangIndex > -1) {
			server = adminNsfPath.substring(0, bangIndex);
			filePath = adminNsfPath.substring(bangIndex+2);
		} else {
			server = "";
			filePath = adminNsfPath;
		}
		
		return session.getDatabase(server, filePath);
	}
	
	public static Document getConfigurationDocument(Database adminNsf) throws NotesException {
		View configuration = adminNsf.getView("Configuration");
		try {
			configuration.refresh();
			Document doc = configuration.getFirstDocument();
			if(doc != null) {
				return doc;
			} else {
				doc = adminNsf.createDocument();
				doc.replaceItemValue("Form", "Configuration");
				return doc;
			}
		} finally {
			configuration.recycle();
		}
	}
}
