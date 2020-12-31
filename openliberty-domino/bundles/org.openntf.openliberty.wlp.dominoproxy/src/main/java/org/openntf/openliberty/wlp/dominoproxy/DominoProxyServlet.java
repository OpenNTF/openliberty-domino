/*
 * Copyright © 2018-2020 Jesse Gallagher
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
/*
 * Derived from a work with the following original copyright, written by David Smiley,
 * dsmiley@apache.org
 *
 * Copyright MITRE
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

package org.openntf.openliberty.wlp.dominoproxy;

import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.AbortableHttpRequest;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.HeaderGroup;
import org.apache.http.util.EntityUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static java.text.MessageFormat.format;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.util.BitSet;
import java.util.Enumeration;
import java.util.Formatter;

@SuppressWarnings("deprecation")
@WebServlet(urlPatterns = "/*")
public class DominoProxyServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
    /* INIT PARAMETER NAME CONSTANTS */

	/** A boolean parameter name to enable logging of input and target URLs to the servlet log. */
    private static final String P_LOG = "log"; //$NON-NLS-1$

    /** A boolean parameter name to enable forwarding of the client IP  */
    private static final String P_FORWARDEDFOR = "forwardip"; //$NON-NLS-1$

    /** A boolean parameter name to keep HOST parameter as-is  */
    private static final String P_PRESERVEHOST = "preserveHost"; //$NON-NLS-1$

    /** A boolean parameter name to keep COOKIES as-is  */
    private static final String P_PRESERVECOOKIES = "preserveCookies"; //$NON-NLS-1$

    /** A boolean parameter name to have auto-handle redirects */
    private static final String P_HANDLEREDIRECTS = "http.protocol.handle-redirects"; // ClientPNames.HANDLE_REDIRECTS //$NON-NLS-1$

    /** A integer parameter name to set the socket connection timeout (millis) */
    private static final String P_CONNECTTIMEOUT = "http.socket.timeout"; // CoreConnectionPNames.SO_TIMEOUT //$NON-NLS-1$

    /** A integer parameter name to set the socket read timeout (millis) */
    private static final String P_READTIMEOUT = "http.read.timeout"; //$NON-NLS-1$

    /** A boolean parameter whether to use JVM-defined system properties to configure various networking aspects. */
    private static final String P_USESYSTEMPROPERTIES = "useSystemProperties"; //$NON-NLS-1$

    /** The parameter name for the target (destination) URI to proxy to. */
    private static final String P_TARGET_URI = "targetUri"; //$NON-NLS-1$
    private static final String ATTR_TARGET_URI =
            DominoProxyServlet.class.getSimpleName() + ".targetUri"; //$NON-NLS-1$
    private static final String ATTR_TARGET_HOST =
            DominoProxyServlet.class.getSimpleName() + ".targetHost"; //$NON-NLS-1$
    private static final String ATTR_SEND_WSIS = DominoProxyServlet.class.getSimpleName() + ".sendWsis"; //$NON-NLS-1$
    private static final String ENV_DOMINOHTTP = "Domino_HTTP"; //$NON-NLS-1$

    /* MISC */

    private boolean doLog = false;
    private boolean doForwardIP = true;
    /** User agents shouldn't send the url fragment but what if it does? */
    private boolean doSendUrlFragment = true;
    private boolean doPreserveHost = false;
    private boolean doPreserveCookies = false;
    private boolean doHandleRedirects = false;
    private boolean useSystemProperties = true;
    private boolean sendWsis = true;
    private int connectTimeout = -1;
    private int readTimeout = -1;

    //These next 3 are cached here, and should only be referred to in initialization logic. See the
    // ATTR_* parameters.
    /** From the configured parameter "targetUri". */
    private String targetUri;
    private URI targetUriObj;//new URI(targetUri)
    private HttpHost targetHost;//URIUtils.extractHost(targetUriObj);

    private HttpClient proxyClient;

    @Override
    public String getServletInfo() {
        return Messages.getString("DominoProxyServlet.servletInfo"); //$NON-NLS-1$
    }


    private String getTargetUri(HttpServletRequest servletRequest) {
        return (String) servletRequest.getAttribute(ATTR_TARGET_URI);
    }

    private HttpHost getTargetHost(HttpServletRequest servletRequest) {
        return (HttpHost) servletRequest.getAttribute(ATTR_TARGET_HOST);
    }

    /**
     * Reads a configuration parameter. By default it reads servlet init parameters but
     * it can be overridden.
     */
    private String getConfigParam(String key) {
        String env = System.getenv(key);
        if(env != null && !env.isEmpty()) {
            return env;
        } else {
            env = getServletConfig().getInitParameter(key);
            if(env != null && !env.isEmpty()) {
            	return env;
            } else {
            	return System.getProperty(key);
            }
        }
    }

    @Override
    public void init() throws ServletException {
        String doLogStr = getConfigParam(P_LOG);
        if (doLogStr != null) {
            this.doLog = Boolean.parseBoolean(doLogStr);
        }

        String doForwardIPString = getConfigParam(P_FORWARDEDFOR);
        if (doForwardIPString != null) {
            this.doForwardIP = Boolean.parseBoolean(doForwardIPString);
        }

        String preserveHostString = getConfigParam(P_PRESERVEHOST);
        if (preserveHostString != null) {
            this.doPreserveHost = Boolean.parseBoolean(preserveHostString);
        }

        String preserveCookiesString = getConfigParam(P_PRESERVECOOKIES);
        if (preserveCookiesString != null) {
            this.doPreserveCookies = Boolean.parseBoolean(preserveCookiesString);
        }

        String handleRedirectsString = getConfigParam(P_HANDLEREDIRECTS);
        if (handleRedirectsString != null) {
            this.doHandleRedirects = Boolean.parseBoolean(handleRedirectsString);
        }

        String connectTimeoutString = getConfigParam(P_CONNECTTIMEOUT);
        if (connectTimeoutString != null) {
            this.connectTimeout = Integer.parseInt(connectTimeoutString);
        }

        String readTimeoutString = getConfigParam(P_READTIMEOUT);
        if (readTimeoutString != null) {
            this.readTimeout = Integer.parseInt(readTimeoutString);
        }

        String useSystemPropertiesString = getConfigParam(P_USESYSTEMPROPERTIES);
        if (useSystemPropertiesString != null) {
            this.useSystemProperties = Boolean.parseBoolean(useSystemPropertiesString);
        }

        String sendWsisString = getConfigParam(ATTR_SEND_WSIS);
        if(sendWsisString != null) {
            this.sendWsis = Boolean.parseBoolean(sendWsisString);
        }

        initTarget();//sets target*

        proxyClient = createHttpClient();
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    /**
     * Sub-classes can override specific behaviour of {@link RequestConfig}.
     */
    private RequestConfig buildRequestConfig() {
        return RequestConfig.custom()
                .setRedirectsEnabled(doHandleRedirects)
                .setCookieSpec(CookieSpecs.IGNORE_COOKIES) // we handle them in the servlet instead
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(readTimeout)
                .build();
    }

    /**
     * Sub-classes can override specific behaviour of {@link SocketConfig}.
     */
    private SocketConfig buildSocketConfig() {

        if (readTimeout < 1) {
            return null;
        }

        return SocketConfig.custom()
                .setSoTimeout(readTimeout)
                .build();
    }

	private void initTarget() throws ServletException {
        targetUri = getConfigParam(P_TARGET_URI);
        if(targetUri == null || targetUri.isEmpty()) {
            targetUri = getConfigParam(ATTR_TARGET_URI);
        }
        if(targetUri == null || targetUri.isEmpty()) {
        	targetUri = getConfigParam(ENV_DOMINOHTTP);
        }
        if (targetUri == null || targetUri.isEmpty())
            throw new ServletException(format(Messages.getString("DominoProxyServlet.configPropertyRequired"), P_TARGET_URI)); //$NON-NLS-1$
        //test it's valid
        try {
            targetUriObj = new URI(targetUri);
        } catch (Exception e) {
            throw new ServletException(format(Messages.getString("DominoProxyServlet.processingTargetUri"), e),e); //$NON-NLS-1$
        }
        targetHost = URIUtils.extractHost(targetUriObj);
    }

    /**
     * Called from {@link #init(ServletConfig)}.
     * HttpClient offers many opportunities for customization.
     * In any case, it should be thread-safe.
     */
    private HttpClient createHttpClient() {
        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .setDefaultRequestConfig(buildRequestConfig())
                .setDefaultSocketConfig(buildSocketConfig());
        if (useSystemProperties) {
            clientBuilder.useSystemProperties();
        }
        return clientBuilder.build();
    }

	@Override
    public void destroy() {
        //Usually, clients implement Closeable:
        if (proxyClient instanceof Closeable) {
            try {
                ((Closeable) proxyClient).close();
            } catch (IOException e) {
                log(format(Messages.getString("DominoProxyServlet.exceptionDestroyingHttpClient"), e), e); //$NON-NLS-1$
            }
        } else {
            //Older releases require we do this:
            if (proxyClient != null)
                proxyClient.getConnectionManager().shutdown();
        }
        super.destroy();
    }

    @Override
    protected void service(HttpServletRequest servletRequest, HttpServletResponse servletResponse)
            throws ServletException, IOException {
        //initialize request attributes from caches if unset by a subclass by this point
        if (servletRequest.getAttribute(ATTR_TARGET_URI) == null) {
            servletRequest.setAttribute(ATTR_TARGET_URI, targetUri);
        }
        if (servletRequest.getAttribute(ATTR_TARGET_HOST) == null) {
            servletRequest.setAttribute(ATTR_TARGET_HOST, targetHost);
        }

        // Make the Request
        //note: we won't transfer the protocol version because I'm not sure it would truly be compatible
        String method = servletRequest.getMethod();
        String proxyRequestUri = rewriteUrlFromRequest(servletRequest);
        HttpRequest proxyRequest;
        //spec: RFC 2616, sec 4.3: either of these two headers signal that there is a message body.
        if (servletRequest.getHeader(HttpHeaders.CONTENT_LENGTH) != null ||
                servletRequest.getHeader(HttpHeaders.TRANSFER_ENCODING) != null) {
            proxyRequest = newProxyRequestWithEntity(method, proxyRequestUri, servletRequest);
        } else {
            proxyRequest = new BasicHttpRequest(method, proxyRequestUri);
        }

        copyRequestHeaders(servletRequest, proxyRequest);

        setForwardingHeaders(servletRequest, proxyRequest);

        HttpResponse proxyResponse = null;
        try {
            // Execute the request
            proxyResponse = doExecute(servletRequest, servletResponse, proxyRequest);

            // Process the response:

            // Pass the response code. This method with the "reason phrase" is deprecated but it's the
            //   only way to pass the reason along too.
            int statusCode = proxyResponse.getStatusLine().getStatusCode();
            //noinspection deprecation
            servletResponse.setStatus(statusCode, proxyResponse.getStatusLine().getReasonPhrase());

            // Copying response headers to make sure SESSIONID or other Cookie which comes from the remote
            // server will be saved in client when the proxied url was redirected to another one.
            // See issue [#51](https://github.com/mitre/HTTP-Proxy-Servlet/issues/51)
            copyResponseHeaders(proxyResponse, servletRequest, servletResponse);

            if (statusCode == HttpServletResponse.SC_NOT_MODIFIED) {
                // 304 needs special handling.  See:
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
            //Note: Don't need to close servlet outputStream:
            // http://stackoverflow.com/questions/1159168/should-one-call-close-on-httpservletresponse-getoutputstream-getwriter
        }
    }

    private void handleRequestException(HttpRequest proxyRequest, Exception e) throws ServletException, IOException {
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

	private HttpResponse doExecute(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
                                   HttpRequest proxyRequest) throws IOException {
        if (doLog) {
            log(format(Messages.getString("DominoProxyServlet.proxyConfigUri"), servletRequest.getMethod(), servletRequest.getRequestURI(), proxyRequest.getRequestLine().getUri())); //$NON-NLS-1$
        }
        return proxyClient.execute(getTargetHost(servletRequest), proxyRequest);
    }

    private HttpRequest newProxyRequestWithEntity(String method, String proxyRequestUri,
                                                  HttpServletRequest servletRequest)
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
    private long getContentLength(HttpServletRequest request) {
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
    private void copyRequestHeaders(HttpServletRequest servletRequest, HttpRequest proxyRequest) {
        // Get an Enumeration of all of the header names sent by the client
        Enumeration<String> enumerationOfHeaderNames = servletRequest.getHeaderNames();
        while (enumerationOfHeaderNames.hasMoreElements()) {
            String headerName = enumerationOfHeaderNames.nextElement();
            copyRequestHeader(servletRequest, proxyRequest, headerName);
        }
    }

    /**
     * Copy a request header from the servlet client to the proxy request.
     * This is easily overridden to filter out certain headers if desired.
     */
    private void copyRequestHeader(HttpServletRequest servletRequest, HttpRequest proxyRequest,
                                   String headerName) {
        //Instead the content-length is effectively set via InputStreamEntity
        if (headerName.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH))
            return;
        if (hopByHopHeaders.containsHeader(headerName))
            return;

        Enumeration<String> headers = servletRequest.getHeaders(headerName);
        while (headers.hasMoreElements()) {//sometimes more than one value
            String headerValue = headers.nextElement();
            // In case the proxy host is running multiple virtual servers,
            // rewrite the Host header to ensure that we get content from
            // the correct virtual server
            if (!doPreserveHost && headerName.equalsIgnoreCase(HttpHeaders.HOST)) {
                HttpHost host = getTargetHost(servletRequest);
                headerValue = host.getHostName();
                if (host.getPort() != -1)
                    headerValue += ":"+host.getPort(); //$NON-NLS-1$
            } else if (!doPreserveCookies && headerName.equalsIgnoreCase(org.apache.http.cookie.SM.COOKIE)) {
                headerValue = getRealCookie(headerValue);
            }
            proxyRequest.addHeader(headerName, headerValue);
        }
    }

    private void setForwardingHeaders(HttpServletRequest servletRequest,
                                      HttpRequest proxyRequest) {
        if (doForwardIP) {
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

        // Add WS Connector Headers - https://developer.ibm.com/wasdev/docs/nginx-websphere-application-server/
        // Skip $WSPR, since Domino wouldn't like HTTP/2.0
        // TODO $WSRU for the user?
        proxyRequest.setHeader("Host", servletRequest.getServerName()); //$NON-NLS-1$
        proxyRequest.setHeader("$WSRA", servletRequest.getRemoteAddr()); //$NON-NLS-1$
        proxyRequest.setHeader("$WSRH", servletRequest.getRemoteHost()); //$NON-NLS-1$
        proxyRequest.setHeader("$WSSN", servletRequest.getServerName()); //$NON-NLS-1$
        if(sendWsis) {
            proxyRequest.setHeader("$WSSC", servletRequest.getScheme()); //$NON-NLS-1$
            proxyRequest.setHeader("$WSIS", servletRequest.isSecure() ? "True" : "False"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            proxyRequest.setHeader("$WSSP", String.valueOf(servletRequest.getServerPort())); //$NON-NLS-1$
        }
    }

    /** Copy proxied response headers back to the servlet client. */
    private void copyResponseHeaders(HttpResponse proxyResponse, HttpServletRequest servletRequest,
                                     HttpServletResponse servletResponse) {
        for (Header header : proxyResponse.getAllHeaders()) {
            copyResponseHeader(servletRequest, servletResponse, header);
        }
    }

    /** Copy a proxied response header back to the servlet client.
     * This is easily overwritten to filter out certain headers if desired.
     */
    private void copyResponseHeader(HttpServletRequest servletRequest,
                                    HttpServletResponse servletResponse, Header header) {
        String headerName = header.getName();
        if (hopByHopHeaders.containsHeader(headerName))
            return;
        String headerValue = header.getValue();
        if (headerName.equalsIgnoreCase(org.apache.http.cookie.SM.SET_COOKIE) ||
                headerName.equalsIgnoreCase(org.apache.http.cookie.SM.SET_COOKIE2)) {
            copyProxyCookie(servletRequest, servletResponse, headerValue);
        } else if (headerName.equalsIgnoreCase(HttpHeaders.LOCATION)) {
            // LOCATION Header may have to be rewritten.
            servletResponse.addHeader(headerName, rewriteUrlFromResponse(servletRequest, headerValue));
        } else {
            servletResponse.addHeader(headerName, headerValue);
        }
    }

    /**
     * Copy cookie from the proxy to the servlet client.
     * Replaces cookie path to local path and renames cookie to avoid collisions.
     */
    private void copyProxyCookie(HttpServletRequest servletRequest,
                                 HttpServletResponse servletResponse, String headerValue) {
        //build path for resulting cookie
        String path = servletRequest.getContextPath(); // path starts with / or is empty string
        path += servletRequest.getServletPath(); // servlet path starts with / or is empty string
        if(path.isEmpty()){
            path = "/"; //$NON-NLS-1$
        }

        for (HttpCookie cookie : HttpCookie.parse(headerValue)) {
            //set cookie name prefixed w/ a proxy value so it won't collide w/ other cookies
            String proxyCookieName = doPreserveCookies ? cookie.getName() : getCookieNamePrefix(cookie.getName()) + cookie.getName();
            Cookie servletCookie = new Cookie(proxyCookieName, cookie.getValue());
            servletCookie.setComment(cookie.getComment());
            servletCookie.setMaxAge((int) cookie.getMaxAge());
            servletCookie.setPath(path); //set to the path of the proxy servlet
            // don't set cookie domain
            servletCookie.setSecure(cookie.getSecure());
            servletCookie.setVersion(cookie.getVersion());
            servletCookie.setHttpOnly(cookie.isHttpOnly());
            servletResponse.addCookie(servletCookie);
        }
    }

    /**
     * Take any client cookies that were originally from the proxy and prepare them to send to the
     * proxy.  This relies on cookie headers being set correctly according to RFC 6265 Sec 5.4.
     * This also blocks any local cookies from being sent to the proxy.
     */
    private String getRealCookie(String cookieValue) {
        StringBuilder escapedCookie = new StringBuilder();
        String[] cookies = cookieValue.split("[;,]"); //$NON-NLS-1$
        for (String cookie : cookies) {
            String[] cookieSplit = cookie.split("="); //$NON-NLS-1$
            if (cookieSplit.length == 2) {
                String cookieName = cookieSplit[0].trim();
                if (cookieName.startsWith(getCookieNamePrefix(cookieName))) {
                    cookieName = cookieName.substring(getCookieNamePrefix(cookieName).length());
                    if (escapedCookie.length() > 0) {
                        escapedCookie.append("; "); //$NON-NLS-1$
                    }
                    escapedCookie.append(cookieName).append("=").append(cookieSplit[1].trim()); //$NON-NLS-1$
                }
            }
        }
        return escapedCookie.toString();
    }

    /** The string prefixing rewritten cookies. */
    private String getCookieNamePrefix(String name) {
        return "!Proxy!" + getServletConfig().getServletName(); //$NON-NLS-1$
    }

    /** Copy response body data (the entity) from the proxy to the servlet client. */
    private void copyResponseEntity(HttpResponse proxyResponse, HttpServletResponse servletResponse,
                                    HttpRequest proxyRequest, HttpServletRequest servletRequest)
            throws IOException {
        HttpEntity entity = proxyResponse.getEntity();
        if (entity != null) {
            OutputStream servletOutputStream = servletResponse.getOutputStream();
            entity.writeTo(servletOutputStream);
        }
    }

    private String rewriteQueryStringFromRequest(HttpServletRequest servletRequest, String queryString) {
        return queryString;
    }

    /**
     * For a redirect response from the target server, this translates {@code theUrl} to redirect to
     * and translates it to one the original client can use.
     */
    private String rewriteUrlFromResponse(HttpServletRequest servletRequest, String theUrl) {
        //TODO document example paths
        final String targetUri = getTargetUri(servletRequest);
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
            // Context path starts with a / if it is not blank
            curUrl.append(servletRequest.getContextPath());
            // Servlet path starts with a / if it is not blank
            curUrl.append(servletRequest.getServletPath());
            curUrl.append(theUrl, targetUri.length(), theUrl.length());
            return curUrl.toString();
        }
        return theUrl;
    }

    /** The target URI as configured. Not null. */
    public String getTargetUri() { return targetUri; }

    /**
     * Encodes characters in the query or fragment part of the URI.
     *
     * <p>Unfortunately, an incoming URI sometimes has characters disallowed by the spec.  HttpClient
     * insists that the outgoing proxied request has a valid URI because it uses Java's {@link URI}.
     * To be more forgiving, we must escape the problematic characters.  See the URI class for the
     * spec.
     *
     * @param in example: name=value&amp;foo=bar#fragment
     * @param encodePercent determine whether percent characters need to be encoded
     */
    private static CharSequence encodeUriQuery(CharSequence in, boolean encodePercent) {
        //Note that I can't simply use URI.java to encode because it will escape pre-existing escaped things.
        StringBuilder outBuf = null;
        Formatter formatter = null;
        for(int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            boolean escape = true;
            if (c < 128) {
                if (asciiQueryChars.get((int)c) && !(encodePercent && c == '%')) {
                    escape = false;
                }
            } else if (!Character.isISOControl(c) && !Character.isSpaceChar(c)) {//not-ascii
                escape = false;
            }
            if (!escape) {
                if (outBuf != null)
                    outBuf.append(c);
            } else {
                //escape
                if (outBuf == null) {
                    outBuf = new StringBuilder(in.length() + 5*3);
                    outBuf.append(in,0,i);
                    formatter = new Formatter(outBuf);
                }
                //leading %, 0 padded, width 2, capital hex
                formatter.format("%%%02X",(int)c);//TODO //$NON-NLS-1$
            }
        }
        return outBuf != null ? outBuf : in;
    }

    private static final BitSet asciiQueryChars;
    static {
        char[] c_unreserved = "_-!.~'()*".toCharArray();//plus alphanum //$NON-NLS-1$
        char[] c_punct = ",;:$&+=".toCharArray(); //$NON-NLS-1$
        char[] c_reserved = "?/[]@".toCharArray();//plus punct //$NON-NLS-1$

        asciiQueryChars = new BitSet(128);
        for(char c = 'a'; c <= 'z'; c++) asciiQueryChars.set((int)c);
        for(char c = 'A'; c <= 'Z'; c++) asciiQueryChars.set((int)c);
        for(char c = '0'; c <= '9'; c++) asciiQueryChars.set((int)c);
        for(char c : c_unreserved) asciiQueryChars.set((int)c);
        for(char c : c_punct) asciiQueryChars.set((int)c);
        for(char c : c_reserved) asciiQueryChars.set((int)c);

        asciiQueryChars.set((int)'%');//leave existing percent escapes in place
    }

    /**
     * Reads the request URI from {@code servletRequest} and rewrites it, considering targetUri.
     * It's used to make the new request.
     */
    private String rewriteUrlFromRequest(HttpServletRequest servletRequest) {
        String uriS = getTargetUri(servletRequest);
        // Handle the path given to the servlet
        String pathInfo = rewritePathInfoFromRequest(servletRequest);
        if (pathInfo != null) {//ex: /my/path.html
            // getPathInfo() returns decoded string, so we need encodeUriQuery to encode "%" characters
            uriS = concat(uriS, encodeUriQuery(pathInfo, true).toString(), '/');
        }
        StringBuilder uri = new StringBuilder(uriS);
        // Handle the query string & fragment
        String queryString = servletRequest.getQueryString();//ex:(following '?'): name=value&foo=bar#fragment
        String fragment = null;
        //split off fragment from queryString, updating queryString if found
        if (queryString != null) {
            int fragIdx = queryString.indexOf('#');
            if (fragIdx >= 0) {
                fragment = queryString.substring(fragIdx + 1);
                queryString = queryString.substring(0,fragIdx);
            }
        }

        queryString = rewriteQueryStringFromRequest(servletRequest, queryString);
        if (queryString != null && queryString.length() > 0) {
            uri.append('?');
            // queryString is not decoded, so we need encodeUriQuery not to encode "%" characters, to avoid double-encoding
            uri.append(encodeUriQuery(queryString, false));
        }

        if (doSendUrlFragment && fragment != null) {
            uri.append('#');
            // fragment is not decoded, so we need encodeUriQuery not to encode "%" characters, to avoid double-encoding
            uri.append(encodeUriQuery(fragment, false));
        }
        
        return uri.toString();
    }

    /**
     * Allow overrides of {@link HttpServletRequest#getPathInfo()}.
     * Useful when url-pattern of servlet-mapping (web.xml) requires manipulation.
     */
    private String rewritePathInfoFromRequest(HttpServletRequest servletRequest) {
        return servletRequest.getPathInfo();
    }

    private static String concat(String path1, String path2, char sep) {
        if(path1 == null || path1.isEmpty()) {
            return path2 == null ? "" : path2; //$NON-NLS-1$
        }
        if(path2 == null || path2.isEmpty()) {
            return path1;
        }
        StringBuilder b = new StringBuilder();
        if(path1.charAt(path1.length()-1)==sep) {
            b.append(path1,0,path1.length()-1);
        } else {
            b.append(path1);
        }
        b.append(sep);
        if(path2.charAt(0)==sep) {
            b.append(path2,1,path2.length());
        } else {
            b.append(path2);
        }
        return b.toString();
    }
}
