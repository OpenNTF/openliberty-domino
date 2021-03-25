package org.openntf.openliberty.domino.httpapi.resources;

import java.io.InputStream;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.openntf.openliberty.domino.runtime.AppDeploymentProvider;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;
import org.openntf.openliberty.domino.util.commons.ibm.StringUtil;

@Path("apps")
public class AppsResource {
	@POST
	@Path("{serverName}/{appName}")
	@Produces(MediaType.TEXT_PLAIN)
	public String deployApp(
		@PathParam("serverName") String serverName,
		@PathParam("appName") String appName,
		@QueryParam("fileName") String fileName,
		@QueryParam("contextPath") String contextPath,
		@QueryParam("includeInReverseProxy") String includeInReverseProxy,
		InputStream fileData
	) {
		AppDeploymentProvider deploymentProvider = OpenLibertyUtil.findRequiredExtension(AppDeploymentProvider.class);
		Boolean reverseProxy = StringUtil.isEmpty(includeInReverseProxy) ? null : Boolean.valueOf(includeInReverseProxy);
		deploymentProvider.deployApp(serverName, appName, contextPath, fileName, reverseProxy, fileData);
		
		return "Deployment successful";
	}
}
