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
