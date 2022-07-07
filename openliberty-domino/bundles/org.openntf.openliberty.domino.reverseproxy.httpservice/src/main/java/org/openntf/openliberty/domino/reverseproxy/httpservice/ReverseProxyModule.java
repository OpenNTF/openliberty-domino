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
package org.openntf.openliberty.domino.reverseproxy.httpservice;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.AbstractExecutionAwareRequest;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.util.EntityUtils;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyTarget;

import com.ibm.designer.runtime.domino.adapter.ComponentModule;
import com.ibm.designer.runtime.domino.adapter.LCDEnvironment;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpServletRequestAdapter;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpServletResponseAdapter;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpSessionAdapter;

/**
 * Represents an individual active reverse-proxy target.
 * 
 * @author Jesse Gallagher
 * @since 3.1.0
 */
public class ReverseProxyModule extends ComponentModule {
	
	private static final String HEADER_SET_COOKIE = "Set-Cookie"; //$NON-NLS-1$
	private static final String HEADER_SET_COOKIE2 = "Set-Cookie2"; //$NON-NLS-1$
	private static final String HEADER_LOCATION = "Location"; //$NON-NLS-1$
	private static final String HEADER_CONTENT_LENGTH = "Content-Length"; //$NON-NLS-1$
	private static final String HEADER_TRANSFER_ENCODING = "Transfer-Encoding"; //$NON-NLS-1$
	private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For"; //$NON-NLS-1$
	private static final String HEADER_X_FORWARDED_PROTO = "X-Forwarded-Proto"; //$NON-NLS-1$

    /** These are the "hop-by-hop" headers that should not be copied.
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html
     */
	private static final Set<String> hopByHopHeaders;
	static {
		hopByHopHeaders = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		hopByHopHeaders.addAll(Arrays.asList(
			"Connection", //$NON-NLS-1$
			"Keep-Alive", //$NON-NLS-1$
			"Proxy-Authenticate", //$NON-NLS-1$
			"Proxy-Authorization", //$NON-NLS-1$
			"TE", //$NON-NLS-1$
			"Trailers", //$NON-NLS-1$
			"Transfer-Encoding", //$NON-NLS-1$
			"Upgrade", //$NON-NLS-1$
			HEADER_X_FORWARDED_FOR,
			HEADER_X_FORWARDED_PROTO
		));
	}
	
	private final ReverseProxyTarget target;
	private HttpClient proxyClient;

	public ReverseProxyModule(LCDEnvironment env, ReverseProxyHttpService service, String moduleName, ReverseProxyTarget target) {
		super(env, service, moduleName, false);
		this.target = target;
	}
	
	@Override
	public ReverseProxyHttpService getHttpService() {
		return (ReverseProxyHttpService)super.getHttpService();
	}

	@Override
	protected void doInitModule() {
		this.proxyClient = createHttpClient();
	}

	@Override
	protected void doDestroyModule() {
		HttpClient client = this.proxyClient;
		if(client instanceof Closeable) {
			try {
				((Closeable)client).close();
			} catch (IOException e) {
				// Ignore
			}
		}
	}
	
	@Override
	public void doService(String var1, String fullPath, HttpSessionAdapter httpSessionAdapter, HttpServletRequestAdapter servletRequest,
			HttpServletResponseAdapter servletResponse) throws ServletException, IOException {
		HttpRequest proxyRequest = null;
		HttpResponse proxyResponse = null;
		try {
			String method = servletRequest.getMethod();
			URI targetUri = target.getUri();
			
			// Incoming request will be in the form foo/bar
			// Target will be in the form http://localhost/foo - for now, we can assume there's no substring replacement
			String proxyRequestUri = targetUri.resolve(servletRequest.getPathInfo()).toString();
			String queryString = servletRequest.getQueryString();
			if(queryString != null && !queryString.isEmpty()) {
				proxyRequestUri += '?' + queryString;
			}

			// spec: RFC 2616, sec 4.3: either of these two headers signal that there is a
			// message body.
			if (servletRequest.getHeader(HEADER_CONTENT_LENGTH) != null
					|| servletRequest.getHeader(HEADER_TRANSFER_ENCODING) != null) {
				proxyRequest = newProxyRequestWithEntity(method, proxyRequestUri, servletRequest);
			} else {
				proxyRequest = new BasicHttpRequest(method, proxyRequestUri);
			}

			copyRequestHeaders(servletRequest, proxyRequest);

			setForwardingHeaders(target, servletRequest, proxyRequest);
			
			// Execute the request
			HttpHost host = new HttpHost(targetUri.getHost(), targetUri.getPort(), targetUri.getScheme());
			
			proxyResponse = this.proxyClient.execute(host, proxyRequest);

			// Process the response:

			// Pass the response code. This method with the "reason phrase" is deprecated
			// but it's the only way to pass the reason along too.
			int statusCode = proxyResponse.getStatusLine().getStatusCode();
			// noinspection deprecation
			servletResponse.setStatus(statusCode, proxyResponse.getStatusLine().getReasonPhrase());

			// Copying response headers to make sure SESSIONID or other Cookie which comes
			// from the remote server will be saved in client when the proxied url was redirected
			// to another one.
			// See issue [#51](https://github.com/mitre/HTTP-Proxy-Servlet/issues/51)
			copyResponseHeaders(target.getUri(), proxyResponse, servletRequest, servletResponse);

			if (statusCode == HttpServletResponse.SC_NOT_MODIFIED) {
				// 304 needs special handling. See:
				// http://www.ics.uci.edu/pub/ietf/http/rfc1945.html#Code304
				// Don't send body entity/content!
				servletResponse.setIntHeader(HEADER_CONTENT_LENGTH, 0);
			} else {
				// Send the content to the client
				copyResponseEntity(proxyResponse, servletResponse, proxyRequest, servletRequest);
			}

		} catch (Throwable e) {
			handleRequestException(proxyRequest, e);
		} finally {
			// make sure the entire entity was consumed, so the connection is released
			if (proxyResponse != null) {
				EntityUtils.consumeQuietly(proxyResponse.getEntity());
			}
		}
	}
	
	// *******************************************************************************
	// * Proxy implementation
	// *******************************************************************************
	
	private HttpClient createHttpClient() {
		RequestConfig config = RequestConfig.custom().setCookieSpec(CookieSpecs.IGNORE_COOKIES).build();
		
		return HttpClientBuilder.create()
			.setDefaultRequestConfig(config)
			.setConnectionManager(new PoolingHttpClientConnectionManager())
			.disableRedirectHandling()
			.build();
	}
	
	private void handleRequestException(HttpRequest proxyRequest, Throwable e) throws ServletException, IOException {

		// Special handling for an empty exception when the client terminates the connection.
		Throwable root = e;
		while(root.getCause() != null) {
			root = root.getCause();
		}
		if("com.ibm.domino.xsp.bridge.http.exception.XspCmdException".equals(root.getClass().getName())) { //$NON-NLS-1$
			if("HTTP: Internal error: ".equals(root.getMessage())) { //$NON-NLS-1$
				// No need to log or bubble this
				return;
			}
		}
		
		e.printStackTrace();
        //abort request, according to best practice with HttpClient
        if (proxyRequest instanceof AbstractExecutionAwareRequest) {
        	AbstractExecutionAwareRequest abortableHttpRequest = (AbstractExecutionAwareRequest) proxyRequest;
            abortableHttpRequest.abort();
        }
        
        if (e instanceof RuntimeException)
            throw (RuntimeException)e;
        if (e instanceof ServletException)
            throw (ServletException)e;
        if (e instanceof IOException)
            throw (IOException) e;
        throw new RuntimeException(e);
    }

	private HttpRequest newProxyRequestWithEntity(String method, String proxyRequestUri,
                                                  HttpServletRequestAdapter servletRequest)
            throws IOException {
        HttpEntityEnclosingRequest eProxyRequest =
                new BasicHttpEntityEnclosingRequest(method, proxyRequestUri);
        // Add the input entity (streamed)
        //  note: we don't bother ensuring we close the servletInputStream since the container handles it
        eProxyRequest.setEntity(
                new InputStreamEntity(servletRequest.getInputStream(), getContentLength(servletRequest)));
        return eProxyRequest;
    }

    // Get the header value as a long in order to more correctly proxy very large requests
    private long getContentLength(HttpServletRequestAdapter request) {
        String contentLengthHeader = request.getHeader(HEADER_CONTENT_LENGTH);
        if (contentLengthHeader != null) {
            return Long.parseLong(contentLengthHeader);
        }
        return -1L;
    }

    /**
     * Copy request headers from the servlet client to the proxy request.
     * This is easily overridden to add your own.
     */
    private void copyRequestHeaders(HttpServletRequestAdapter servletRequest, HttpRequest proxyRequest) {
        // Get an Enumeration of all of the header names sent by the client
        @SuppressWarnings("unchecked")
		Enumeration<String> enumerationOfHeaderNames = servletRequest.getHeaderNames();
        while (enumerationOfHeaderNames.hasMoreElements()) {
            String headerName = enumerationOfHeaderNames.nextElement();
            
            // Instead the content-length is effectively set via InputStreamEntity
    		if (headerName.equalsIgnoreCase(HEADER_CONTENT_LENGTH)) {
    			continue;
    		}
    		if (hopByHopHeaders.contains(headerName)) {
    			continue;
    		}
    		// Avoid copying any connector headers coming in to Domino
    		if (headerName.toLowerCase().startsWith("$ws")) { //$NON-NLS-1$
    			continue;
    		}

    		@SuppressWarnings("unchecked")
    		Enumeration<String> headers = servletRequest.getHeaders(headerName);
    		while (headers.hasMoreElements()) {// sometimes more than one value
    			String headerValue = headers.nextElement();
    			proxyRequest.addHeader(headerName, headerValue);
    		}
        }
    }

	private void setForwardingHeaders(ReverseProxyTarget target, HttpServletRequestAdapter servletRequest, HttpRequest proxyRequest) {
		proxyRequest.setHeader("Host", servletRequest.getServerName()); //$NON-NLS-1$
		
		if(target.isUseXForwardedFor()) {
			String forHeader = servletRequest.getRemoteAddr();
			String existingForHeader = servletRequest.getHeader(HEADER_X_FORWARDED_FOR);
			if (existingForHeader != null) {
				forHeader = existingForHeader + ", " + forHeader; //$NON-NLS-1$
			}
			proxyRequest.setHeader(HEADER_X_FORWARDED_FOR, forHeader);
	
			String protoHeader = servletRequest.getScheme();
			proxyRequest.setHeader(HEADER_X_FORWARDED_PROTO, protoHeader);
		}

		if(target.isUseWsHeaders()) {
			// Add WS Connector Headers -
			// https://developer.ibm.com/wasdev/docs/nginx-websphere-application-server/
			proxyRequest.setHeader("$WSRA", servletRequest.getRemoteAddr()); //$NON-NLS-1$
			proxyRequest.setHeader("$WSRH", servletRequest.getRemoteHost()); //$NON-NLS-1$
			proxyRequest.setHeader("$WSSN", servletRequest.getServerName()); //$NON-NLS-1$
	
			proxyRequest.setHeader("$WSSC", servletRequest.getScheme()); //$NON-NLS-1$
			proxyRequest.setHeader("$WSIS", servletRequest.isSecure() ? "True" : "False"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			proxyRequest.setHeader("$WSSP", String.valueOf(servletRequest.getServerPort())); //$NON-NLS-1$
			proxyRequest.setHeader("$WSRU", servletRequest.getRemoteUser()); //$NON-NLS-1$
		}
	}

    /** Copy proxied response headers back to the servlet client. */
    private void copyResponseHeaders(URI target, HttpResponse proxyResponse, HttpServletRequestAdapter servletRequest,
                                     HttpServletResponseAdapter servletResponse) {
        for (Header header : proxyResponse.getAllHeaders()) {
        	String headerName = header.getName();
            if (hopByHopHeaders.contains(headerName))
                continue;
            String headerValue = header.getValue();
            if (headerName.equalsIgnoreCase(HEADER_SET_COOKIE) ||
                    headerName.equalsIgnoreCase(HEADER_SET_COOKIE2)) {
                copyProxyCookie(servletRequest, servletResponse, headerValue);
            } else if (headerName.equalsIgnoreCase(HEADER_LOCATION)) {
                // LOCATION Header may have to be rewritten.
                servletResponse.addHeader(headerName, rewriteUrlFromResponse(target, servletRequest, headerValue));
            } else {
                servletResponse.addHeader(headerName, headerValue);
            }
        }
    }

    /**
     * Copy cookie from the proxy to the servlet client.
     * Replaces cookie path to local path and renames cookie to avoid collisions.
     */
    private void copyProxyCookie(HttpServletRequestAdapter servletRequest,
                                 HttpServletResponseAdapter servletResponse, String headerValue) {
        //build path for resulting cookie
        String path = servletRequest.getContextPath(); // path starts with / or is empty string
        path += servletRequest.getServletPath(); // servlet path starts with / or is empty string
        if(path.isEmpty()){
            path = "/"; //$NON-NLS-1$
        }

        for (HttpCookie cookie : HttpCookie.parse(headerValue)) {
            //set cookie name prefixed w/ a proxy value so it won't collide w/ other cookies
            String proxyCookieName = cookie.getName();
            Cookie servletCookie = new Cookie(proxyCookieName, cookie.getValue());
            servletCookie.setComment(cookie.getComment());
            servletCookie.setMaxAge((int) cookie.getMaxAge());
            servletCookie.setPath(path); //set to the path of the proxy servlet
            // don't set cookie domain
            servletCookie.setSecure(cookie.getSecure());
            servletCookie.setVersion(cookie.getVersion());
            //servletCookie.setHttpOnly(cookie.isHttpOnly());
            servletResponse.addCookie(servletCookie);
        }
    }



    /** Copy response body data (the entity) from the proxy to the servlet client. */
    private void copyResponseEntity(HttpResponse proxyResponse, HttpServletResponseAdapter servletResponse,
                                    HttpRequest proxyRequest, HttpServletRequestAdapter servletRequest)
            throws IOException {
        HttpEntity entity = proxyResponse.getEntity();
        if (entity != null) {
            OutputStream servletOutputStream = servletResponse.getOutputStream();
            entity.writeTo(servletOutputStream);
        }
    }

    /**
     * For a redirect response from the target server, this translates {@code theUrl} to redirect to
     * and translates it to one the original client can use.
     */
    private String rewriteUrlFromResponse(URI target, HttpServletRequestAdapter servletRequest, String theUrl) {
        //TODO document example paths
        final String targetUri = target.resolve(servletRequest.getPathInfo()).toString();
        if (theUrl.startsWith(targetUri)) {
            /*-
             * The URL points back to the back-end server.
             * Instead of returning it verbatim we replace the target path with our
             * source path in a way that should instruct the original client to
             * request the URL pointed through this Proxy.
             * We do this by taking the current request and rewriting the path part
             * using this servlet's absolute path and the path from the returned URL
             * after the base target URL.
             */
            StringBuffer curUrl = servletRequest.getRequestURL();//no query
            int pos;
            // Skip the protocol part
            if ((pos = curUrl.indexOf("://"))>=0) { //$NON-NLS-1$
                // Skip the authority part
                // + 3 to skip the separator between protocol and authority
                if ((pos = curUrl.indexOf("/", pos + 3)) >=0) { //$NON-NLS-1$
                    // Trim everything after the authority part.
                    curUrl.setLength(pos);
                }
            }
            int len = curUrl.length();
            // Context path starts with a / if it is not blank
            curUrl.append(servletRequest.getContextPath());
            // Servlet path starts with a / if it is not blank
            curUrl.append(servletRequest.getServletPath());
            if(curUrl.length() == len) {
            	// Then both the context and servlet paths were empty - add a "/" to create legal URLs
            	curUrl.append('/');
            }
            curUrl.append(theUrl, targetUri.length(), theUrl.length());
            return curUrl.toString();
        }
        return theUrl;
    }
	
	// *******************************************************************************
	// * NOP ComponentModule methods
	// *******************************************************************************

	@Override
	public boolean exists() {
		return true;
	}

	@Override
	public ClassLoader getModuleClassLoader() {
		return null;
	}

	@Override
	public URL getResource(String path) throws MalformedURLException {
		return null;
	}

	@Override
	public InputStream getResourceAsStream(String path) {
		return null;
	}

	@Override
	public boolean getResourceAsStream(OutputStream os, String path) {
		return false;
	}

	@Override
	public Set<String> getResourcePaths(String path) {
		return null;
	}

	@Override
	public boolean refresh() {
		// NOP
		return false;
	}

	@Override
	public boolean shouldRefresh() {
		return false;
	}

}
