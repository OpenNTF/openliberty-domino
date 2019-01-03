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
