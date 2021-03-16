package org.openntf.openliberty.domino.reverseproxy.httpservice;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.AbortableHttpRequest;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.HeaderGroup;
import org.apache.http.util.EntityUtils;
import org.openntf.openliberty.domino.log.OpenLibertyLog;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyConfig;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyConfigProvider;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyService;
import org.openntf.openliberty.domino.reverseproxy.ReverseProxyTarget;
import org.openntf.openliberty.domino.util.OpenLibertyUtil;

import com.ibm.commons.util.StringUtil;
import com.ibm.designer.runtime.domino.adapter.ComponentModule;
import com.ibm.designer.runtime.domino.adapter.HttpService;
import com.ibm.designer.runtime.domino.adapter.LCDEnvironment;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpServletRequestAdapter;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpServletResponseAdapter;
import com.ibm.designer.runtime.domino.bootstrap.adapter.HttpSessionAdapter;

@SuppressWarnings("deprecation")
public class ReverseProxyHttpService extends HttpService implements ReverseProxyService {
	private static final Logger log = OpenLibertyLog.getLog();

	public static final String TYPE = "NHTTP";
	private final boolean enabled;
	private final Map<String, ReverseProxyTarget> targets;
	private HttpClient proxyClient;

	public ReverseProxyHttpService(LCDEnvironment env) {
		super(env);

		try {
			ReverseProxyConfigProvider configProvider = OpenLibertyUtil.findRequiredExtension(ReverseProxyConfigProvider.class);
			ReverseProxyConfig config = configProvider.createConfiguration();
	
			this.enabled = config.isEnabled(this);
			if (!enabled) {
				this.targets = Collections.emptyMap();
			} else {
				if (log.isLoggable(Level.INFO)) {
					log.info("NHTTP reverse proxy enabled");
				}
				this.targets = config.getTargets();
				this.proxyClient = createHttpClient();
			}
		} catch(Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	@Override
	public String getProxyType() {
		return TYPE;
	}

	@Override
	public boolean isXspUrl(String fullPath, boolean arg1) {
		if (!enabled) {
			return false;
		}
		String pathInfo = getChompedPathInfo(fullPath);
		boolean match = this.targets.keySet().stream()
				.anyMatch(contextRoot -> pathInfo.equals(contextRoot) || pathInfo.startsWith(contextRoot + '/'));
		return match;
	}

	@Override
	public boolean doService(String arg0, String fullPath, HttpSessionAdapter httpSessionAdapter,
			HttpServletRequestAdapter servletRequest, HttpServletResponseAdapter servletResponse)
			throws ServletException, IOException {
		if (!enabled) {
			return false;
		}
		String pathInfo = getChompedPathInfo(fullPath);
		if (StringUtil.isEmpty(fullPath)) {
			return false;
		}
		Optional<ReverseProxyTarget> target = this.targets.entrySet()
				.stream()
				.filter(entry -> pathInfo.equals(entry.getKey()) || pathInfo.startsWith(entry.getKey() + '/'))
				.map(Map.Entry::getValue)
				.findFirst();
		if (target.isPresent()) {

			HttpRequest proxyRequest = null;
			HttpResponse proxyResponse = null;
			try {
				String method = servletRequest.getMethod();
				
				// Incoming request will be in the form foo/bar
				// Target will be in the form http://localhost/foo - for now, we can assume there's no substring replacement
				String proxyRequestUri = target.get().getUri().resolve(servletRequest.getPathInfo()).toString();

				// spec: RFC 2616, sec 4.3: either of these two headers signal that there is a
				// message body.
				if (servletRequest.getHeader(HttpHeaders.CONTENT_LENGTH) != null
						|| servletRequest.getHeader(HttpHeaders.TRANSFER_ENCODING) != null) {
					proxyRequest = newProxyRequestWithEntity(method, proxyRequestUri, servletRequest);
				} else {
					proxyRequest = new BasicHttpRequest(method, proxyRequestUri);
				}

				copyRequestHeaders(servletRequest, proxyRequest);

				setForwardingHeaders(target.get(), servletRequest, proxyRequest);
				
				// Execute the request
				proxyResponse = doExecute(target.get().getUri(), servletRequest, servletResponse, proxyRequest);

				// Process the response:

				// Pass the response code. This method with the "reason phrase" is deprecated
				// but it's the
				// only way to pass the reason along too.
				int statusCode = proxyResponse.getStatusLine().getStatusCode();
				// noinspection deprecation
				servletResponse.setStatus(statusCode, proxyResponse.getStatusLine().getReasonPhrase());

				// Copying response headers to make sure SESSIONID or other Cookie which comes
				// from the remote
				// server will be saved in client when the proxied url was redirected to another
				// one.
				// See issue [#51](https://github.com/mitre/HTTP-Proxy-Servlet/issues/51)
				copyResponseHeaders(target.get().getUri(), proxyResponse, servletRequest, servletResponse);

				if (statusCode == HttpServletResponse.SC_NOT_MODIFIED) {
					// 304 needs special handling. See:
					// http://www.ics.uci.edu/pub/ietf/http/rfc1945.html#Code304
					// Don't send body entity/content!
					servletResponse.setIntHeader(HttpHeaders.CONTENT_LENGTH, 0);
				} else {
					// Send the content to the client
					copyResponseEntity(proxyResponse, servletResponse, proxyRequest, servletRequest);
				}

			} catch (Exception e) {
				handleRequestException(proxyRequest, e);
			} finally {
				// make sure the entire entity was consumed, so the connection is released
				if (proxyResponse != null)
					EntityUtils.consumeQuietly(proxyResponse.getEntity());
				// Note: Don't need to close servlet outputStream:
				// http://stackoverflow.com/questions/1159168/should-one-call-close-on-httpservletresponse-getoutputstream-getwriter
			}
			
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void destroyService() {
		super.destroyService();

		// Usually, clients implement Closeable:
		if (proxyClient instanceof Closeable) {
			try {
				((Closeable) proxyClient).close();
			} catch (IOException e) {

			}
		} else {
			// Older releases require we do this:
			if (proxyClient != null)
				proxyClient.getConnectionManager().shutdown();
		}
	}

	@Override
	public void getModules(List<ComponentModule> modules) {
		// NOP
	}

	private String getChompedPathInfo(String fullPath) {
		if (StringUtil.isEmpty(fullPath)) {
			return "";
		} else {
			int qIndex = fullPath.indexOf('?');
			if (qIndex >= 0) {
				return fullPath.substring(1, qIndex);
			} else {
				return fullPath.substring(1);
			}
		}
	}

	private HttpClient createHttpClient() {
		HttpClientBuilder clientBuilder = HttpClientBuilder.create().setDefaultRequestConfig(buildRequestConfig());
		return clientBuilder.build();
	}

	private RequestConfig buildRequestConfig() {
		return RequestConfig.custom().setCookieSpec(CookieSpecs.IGNORE_COOKIES).build();
	}
	
	private void handleRequestException(HttpRequest proxyRequest, Exception e) throws ServletException, IOException {
		e.printStackTrace();
        //abort request, according to best practice with HttpClient
        if (proxyRequest instanceof AbortableHttpRequest) {
            AbortableHttpRequest abortableHttpRequest = (AbortableHttpRequest) proxyRequest;
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

	private HttpResponse doExecute(URI target, HttpServletRequestAdapter servletRequest, HttpServletResponseAdapter servletResponse,
                                   HttpRequest proxyRequest) throws IOException {
        HttpHost host = new HttpHost(target.getHost(), target.getPort(), target.getScheme());
        return proxyClient.execute(host, proxyRequest);
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
        String contentLengthHeader = request.getHeader("Content-Length"); //$NON-NLS-1$
        if (contentLengthHeader != null) {
            return Long.parseLong(contentLengthHeader);
        }
        return -1L;
    }

    /** These are the "hop-by-hop" headers that should not be copied.
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html
     * I use an HttpClient HeaderGroup class instead of Set&lt;String&gt; because this
     * approach does case insensitive lookup faster.
     */
    private static final HeaderGroup hopByHopHeaders;
    static {
        hopByHopHeaders = new HeaderGroup();
        String[] headers = new String[] {
                "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                "TE", "Trailers", "Transfer-Encoding", "Upgrade" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        for (String header : headers) {
            hopByHopHeaders.addHeader(new BasicHeader(header, null));
        }
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
            copyRequestHeader(servletRequest, proxyRequest, headerName);
        }
    }

	/**
	 * Copy a request header from the servlet client to the proxy request. This is
	 * easily overridden to filter out certain headers if desired.
	 */
	private void copyRequestHeader(HttpServletRequestAdapter servletRequest, HttpRequest proxyRequest,
			String headerName) {
		// Instead the content-length is effectively set via InputStreamEntity
		if (headerName.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)) {
			return;
		}
		if (hopByHopHeaders.containsHeader(headerName)) {
			return;
		}
		// Avoid copying any connector headers coming in to Domino
		if (headerName.startsWith("$WS")) {
			return;
		}

		@SuppressWarnings("unchecked")
		Enumeration<String> headers = servletRequest.getHeaders(headerName);
		while (headers.hasMoreElements()) {// sometimes more than one value
			String headerValue = headers.nextElement();
			proxyRequest.addHeader(headerName, headerValue);
		}
	}

	private void setForwardingHeaders(ReverseProxyTarget target, HttpServletRequestAdapter servletRequest, HttpRequest proxyRequest) {
		proxyRequest.setHeader("Host", servletRequest.getServerName()); //$NON-NLS-1$
		
		if(target.isUseXForwardedFor()) {
			String forHeaderName = "X-Forwarded-For"; //$NON-NLS-1$
			String forHeader = servletRequest.getRemoteAddr();
			String existingForHeader = servletRequest.getHeader(forHeaderName);
			if (existingForHeader != null) {
				forHeader = existingForHeader + ", " + forHeader; //$NON-NLS-1$
			}
			proxyRequest.setHeader(forHeaderName, forHeader);
	
			String protoHeaderName = "X-Forwarded-Proto"; //$NON-NLS-1$
			String protoHeader = servletRequest.getScheme();
			proxyRequest.setHeader(protoHeaderName, protoHeader);
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
			proxyRequest.setHeader("$WSRU", servletRequest.getRemoteUser());
		}
	}

    /** Copy proxied response headers back to the servlet client. */
    private void copyResponseHeaders(URI target, HttpResponse proxyResponse, HttpServletRequestAdapter servletRequest,
                                     HttpServletResponseAdapter servletResponse) {
        for (Header header : proxyResponse.getAllHeaders()) {
            copyResponseHeader(target, servletRequest, servletResponse, header);
        }
    }

    /** Copy a proxied response header back to the servlet client.
     * This is easily overwritten to filter out certain headers if desired.
     */
    private void copyResponseHeader(URI target, HttpServletRequestAdapter servletRequest,
                                    HttpServletResponseAdapter servletResponse, Header header) {
        String headerName = header.getName();
        if (hopByHopHeaders.containsHeader(headerName))
            return;
        String headerValue = header.getValue();
        if (headerName.equalsIgnoreCase(org.apache.http.cookie.SM.SET_COOKIE) ||
                headerName.equalsIgnoreCase(org.apache.http.cookie.SM.SET_COOKIE2)) {
            copyProxyCookie(servletRequest, servletResponse, headerValue);
        } else if (headerName.equalsIgnoreCase(HttpHeaders.LOCATION)) {
            // LOCATION Header may have to be rewritten.
            servletResponse.addHeader(headerName, rewriteUrlFromResponse(target, servletRequest, headerValue));
        } else {
            servletResponse.addHeader(headerName, headerValue);
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
}
