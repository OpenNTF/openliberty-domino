package org.openntf.openliberty.domino.httpapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.security.Principal;
import java.text.MessageFormat;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openntf.openliberty.domino.config.RuntimeAccessProvider;
import org.openntf.openliberty.domino.runtime.AppDeploymentProvider;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;

public class AppDeploymentServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	public static String HEADER_SERVERNAME = "X-ServerName"; //$NON-NLS-1$
	public static String HEADER_APPNAME = "X-AppName"; //$NON-NLS-1$
	public static String HEADER_CONTEXTPATH = "X-ContextPath"; //$NON-NLS-1$
	public static String HEADER_FILENAME = "X-FileName"; //$NON-NLS-1$
	public static String HEADER_REVERSEPROXY = "X-IncludeInReverseProxy"; //$NON-NLS-1$

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp.setContentType("text/plain"); //$NON-NLS-1$
		
		ServletOutputStream os = resp.getOutputStream();
		try {
			Principal user = req.getUserPrincipal();
	
			
			RuntimeAccessProvider access = OpenLibertyUtil.findRequiredExtension(RuntimeAccessProvider.class);
			if(user == null || !access.canDeployApps(user.getName())) {
				resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				os.println(MessageFormat.format("User \"{0}\" does not have access to deploy apps", user == null ? "Anonymous" : user.getName())); //$NON-NLS-2$
				return;
			}
			
			String serverName = req.getHeader(HEADER_SERVERNAME);
			if(StringUtil.isEmpty(serverName)) {
				throw new IllegalArgumentException(MessageFormat.format("{0} header cannot be empty", HEADER_SERVERNAME));
			}
			String appName = req.getHeader(HEADER_APPNAME);
			if(StringUtil.isEmpty(appName)) {
				throw new IllegalArgumentException(MessageFormat.format("{0} header cannot be empty", HEADER_APPNAME));
			}
			String fileName = req.getHeader(HEADER_FILENAME);
			if(StringUtil.isEmpty(appName)) {
				throw new IllegalArgumentException(MessageFormat.format("{0} header cannot be empty", HEADER_APPNAME));
			}
			if(fileName.contains("/") || fileName.contains("\\")) { //$NON-NLS-1$ //$NON-NLS-2$
				throw new IllegalArgumentException("File name cannot contain path components");
			}
			String contextPath = req.getHeader(HEADER_CONTEXTPATH);
			if(StringUtil.isEmpty(contextPath)) {
				throw new IllegalArgumentException(MessageFormat.format("{0} header cannot be empty", HEADER_CONTEXTPATH));
			}
			boolean reverseProxy = false;
			String reverseProxyHeader = req.getHeader(HEADER_REVERSEPROXY);
			if(StringUtil.isNotEmpty(reverseProxyHeader)) {
				reverseProxy = Boolean.parseBoolean(reverseProxyHeader);
			}
			
			AppDeploymentProvider deploymentProvider = OpenLibertyUtil.findRequiredExtension(AppDeploymentProvider.class);
			try(InputStream is = req.getInputStream()) {
				deploymentProvider.deployApp(serverName, appName, contextPath, fileName, reverseProxy, is);
			}
			
			resp.setStatus(HttpServletResponse.SC_OK);
			os.println("Deployment successful");
		} catch(IllegalArgumentException e) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			os.println(e.getLocalizedMessage());
		} catch(Throwable t) {
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			try(PrintStream out = new PrintStream(os)) {
				t.printStackTrace(out);
			}
		}
	}
}
