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
package org.openntf.openliberty.domino.httpapi;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;

public class RestEasyServlet extends HttpServletDispatcher {
	private static final long serialVersionUID = 1L;

	@Override
	protected void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
			throws ServletException, IOException {
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try {
			AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
				Thread.currentThread().setContextClassLoader(RestEasyServlet.class.getClassLoader());

				return null;
			});

			super.service(httpServletRequest, httpServletResponse);
		} catch(Exception e) {
			// Look for a known case of blank XspCmdExceptions
			Throwable t = e;
			while(t != null && t.getCause() != null) {
				t = t.getCause();
			}
			if(t.getClass().getName().equals("com.ibm.domino.xsp.bridge.http.exception.XspCmdException")) { //$NON-NLS-1$
				if("HTTP: Internal error:".equals(String.valueOf(t.getMessage()).trim())) { //$NON-NLS-1$
					// Ignore
					return;
				}
			}

			throw e;
		} finally {
			AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
				Thread.currentThread().setContextClassLoader(cl);
				return null;
			});
		}
	}
}
