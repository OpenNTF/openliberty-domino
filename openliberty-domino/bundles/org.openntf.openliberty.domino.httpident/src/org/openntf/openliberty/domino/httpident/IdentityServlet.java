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
package org.openntf.openliberty.domino.httpident;

import com.ibm.domino.osgi.core.context.ContextInfo;

import lotus.domino.NotesException;
import lotus.domino.Session;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class IdentityServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try(ServletOutputStream os = resp.getOutputStream()) {
			Session session = ContextInfo.getUserSession();
			try {
				try {
					resp.setContentType("text/plain");
					os.print(session.getEffectiveUserName());
				} catch (NotesException e) {
					throw new ServletException(e);
				}
			} finally {
				try {
					session.recycle();
				} catch (NotesException e) { }
			}
		}
	}
}
