/*
 * The MIT License
 *
 * Copyright 2020 Sergey Sidorov/000ssg@gmail.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ssg.lib.http;

import ssg.lib.http.di.DIHttpData;
import ssg.lib.http.HttpAuthenticator.HttpSimpleAuth;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.http.base.HttpResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ssg.lib.common.Matcher;
import ssg.lib.common.TaskProvider;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.di.DI;
import ssg.lib.service.DataProcessor;
import ssg.lib.service.Repository;
import ssg.lib.service.Repository.CombinedRepository;
import ssg.lib.service.SERVICE_FLOW_STATE;
import ssg.lib.service.SERVICE_MODE;
import static ssg.lib.service.SERVICE_MODE.request;
import static ssg.lib.service.SERVICE_MODE.response;
import ssg.lib.service.SERVICE_PROCESSING_STATE;
import ssg.lib.service.ServiceProcessor;
import static ssg.lib.service.ServiceProcessor.SPO_NO_OPTIONS;
import ssg.lib.service.ServiceProcessor.ServiceProviderMeta;

/**
 *
 * @author 000ssg
 */
public class HttpService<P extends Channel> implements ServiceProcessor<P> {

    public static final String protocolVersion = "HTTP/1.1";
    public static Collection<String> methods = Collections.unmodifiableSet(new HashSet<String>() {
        {
            add("GET");
            add("HEAD");
            add("POST");
            add("PUT");
            add("DELETE");
            add("CONNECT");
            add("OPTIONS");
            add("TRACE");
            add("PATCH");
        }
    });

    public String defaultConnectionBehaviour = HttpData.HCONN_KEEP_ALIVE;

    public static boolean DEBUG_SERVICE_ERROR = false;
    String name = "Http service";
    private long options = SPO_NO_OPTIONS;
    private Repository<DataProcessor> dataProcessors;
    private Repository<HttpConnectionUpgrade> connectionUpgrades;
    private Repository<HttpApplication> applications = new Repository<HttpApplication>().addOwner(this);
    //HttpMatcher serviceMatcher;
    String root;
    int maxURILength = 1024 * 64 + 10 + protocolVersion.length() + 2;
    String sessionIdCookieHTTP = "0x01";
    String sessionIdCookieHTTPS = "0x02";

    DIHttpData<P> httpData = new DIHttpData<P>() {
        @Override
        public void onCompleted(P provider, ssg.lib.http.base.HttpData data) {
            super.onCompleted(provider, data);
            onHttpDataCompleted(provider, data);
        }
    };
    HttpAuthenticator<P> auth = new HttpSimpleAuth<P>();
    Map<String, HttpSession> sessions = new LinkedHashMap<>();

    public HttpService() {
    }

    public HttpService(HttpAuthenticator<P> auth) {
        if (auth != null) {
            this.auth = auth;
        }
    }

    public <T extends HttpService> T configureDataProcessors(Repository<DataProcessor> dataProcessors) {
        this.dataProcessors = dataProcessors;
        return (T) this;
    }

    public <T extends HttpService> T configureConnectionUpgrades(Repository<HttpConnectionUpgrade> connectionUpgrades) {
        return (T) this;
    }

    public <T extends HttpService> T configureDataProcessor(int order, DataProcessor... dataProcessors) {
        if (this.dataProcessors == null) {
            this.dataProcessors = new Repository<>();
        }
        this.dataProcessors.configure(order, dataProcessors);
        return (T) this;
    }

    public <T extends HttpService> T configureConnectionUpgrade(int order, HttpConnectionUpgrade... connectionUpgrades) {
        if (this.connectionUpgrades == null) {
            this.connectionUpgrades = new Repository<>();
        }
        this.connectionUpgrades.configure(order, connectionUpgrades);
        return (T) this;
    }

    public <T extends HttpService> T configureApplication(int order, HttpApplication... applications) {
        if (this.applications == null) {
            this.applications = new Repository<>();
        }
        this.applications.configure(order, applications);
        return (T) this;
    }

    public <T extends HttpService> T configureServiceOptions(long options) {
        this.setOptions(options);
        return (T) this;
    }

    public <T extends HttpService> T configureRoot(String root) throws IOException {
        if (this.root == null && root != null) {
            this.root = root;
        } else {
            throw new IOException("Cannot modify root: '" + this.root + "' to '" + root + "'.");
        }
        this.setOptions(options);
        return (T) this;
    }

    public <T extends HttpService> T configureAuthentication(HttpAuthenticator<P> auth) throws IOException {
        this.auth = auth;
        return (T) this;
    }

    ////////////////////////////////////////////////////////////////////////////
    public HttpService root(String root) {
        if (root != null && !root.startsWith("/")) {
            root = "/" + root;
        }
        this.root = root;
        return this;
    }

    public HttpAuthenticator<P> getAuthenticator() {
        return auth;
    }

    public Repository<HttpApplication> getApplications() {
        return applications;
    }

    @Override
    public void onAssigned(P p, DI<?, P> di) {
        // no default actions on assign/deassign
    }

    @Override
    public void onDeassigned(P p, DI<?, P> di) {
        // no default actions on assign/deassign
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean hasOptions(long options) {
        return (this.getOptions() & options) == options;
    }

    @Override
    public boolean hasOption(long options) {
        return (this.getOptions() & options) != 0;
    }

    @Override
    public SERVICE_MODE probe(ServiceProviderMeta<P> meta, Collection<ByteBuffer> data) {
        if (!isAllowedProvider(meta.getProvider())) {
            return SERVICE_MODE.failed;
        }

//        try {
//            System.out.println("HTTP PROBE: " + meta.getProvider() + "\n  " + BufferTools.toText("ISO-8859-1", data).replace("\n", "\n  "));
//        } catch (Throwable th) {
//            System.out.println("HTTP PROBE: " + meta.getProvider() + "\n  " + th.toString().replace("\n", "\n  "));
//        }
        byte[] buf = new byte[10 + ((root != null) ? root.length() + 2 : 0)];
        ByteBuffer probe = BufferTools.probe(ByteBuffer.wrap(buf), data);
        if (probe.hasRemaining()) {
            int maxIdx = probe.remaining();
            int spIdx = -1;
            int spIdx2 = -1;
            int spIdx3 = -1;
            for (int i = 0; i < maxIdx; i++) {
                if (buf[i] == ' ') {
                    if (spIdx == -1) {
                        spIdx = i;
                        if (root == null) {
                            break;
                        }
                    } else {
                        if (spIdx2 == -1) {
                            spIdx2 = i;
                            break;
                        }
                    }
                }
            }
            if (spIdx > 0) {
                String method = new String(buf, 0, spIdx);
                if (root != null) {
                    if (spIdx2 == -1) {
                        spIdx2 = maxIdx - 1;
                    }
                    String path = new String(buf, spIdx + 1, spIdx2);
                    if (!path.startsWith(root)) {
                        return SERVICE_MODE.failed;
                    }
                }
                if (methods.contains(method)) {
                    return SERVICE_MODE.request;
                } else if (protocolVersion.equals(method)) {
                    return SERVICE_MODE.response;
                }
            }
        }
        return SERVICE_MODE.failed;
    }

    @Override
    public DI<ByteBuffer, P> initPD(ServiceProviderMeta<P> meta, SERVICE_MODE initialState, Collection<ByteBuffer>... data) throws IOException {
        try {
            switch (initialState) {
                case request:

                    HttpRequest req = new HttpRequest(data);
                    req.secure(meta.isSecure());
                    req.getProperties().put("connection", "" + meta.getProvider());

                    if (meta.getStatistics() != null) {
                        meta.getStatistics().updateCounter(req.getQuery(), null, 0, 0, 0);
                    }

                    //System.out.println("HTTP: " + meta.getProvider() + ": " + req.getQuery() + "\n  " + req.toString().replace("\n", "\n    "));
                    HttpSession sess = null;
                    synchronized (sessions) {

                        String sessionIdCookie = req.isSecure() ? sessionIdCookieHTTPS : sessionIdCookieHTTP;
                        Map<String, HttpCookie> clientCookies = HttpSession.getCookies(req);
                        if (sessionIdCookie != null && clientCookies.containsKey(sessionIdCookie)) {
                            HttpCookie cookie = clientCookies.get(sessionIdCookie);
                            if (cookie.isValid()) {
                                sess = sessions.get(cookie.value);
                            }
                        }

                        if (sess != null && !sess.isValid()) {
                            sessions.remove("" + sess.getId());
                            sess = null;
                        }

                        if (sess == null) {
                            String path = ((root != null) ? root : "/");
                            final String query = req.getQuery();
                            List<HttpApplication> apps = getApplications().find(new Matcher<HttpApplication>() {
                                @Override
                                public float match(HttpApplication t) {
                                    if (query.startsWith(t.getRoot())) {
                                        return t.getRoot().length() * 1f / query.length();
                                    }
                                    return 0;
                                }

                                @Override
                                public float weight() {
                                    return 1;
                                }
                            }, null);

                            sess = new HttpSession(req.getHostURL() + path);
                            if (req.isSecure()) {
                                sess.setSecure(true);
                            }
                            if (apps != null && !apps.isEmpty()) {
                                sess.application = apps.get(0);
                                String appRoot = sess.application.getRoot();
                                path += (path.endsWith("/") || appRoot.startsWith("/"))
                                        ? (path.endsWith("/") && appRoot.startsWith("/"))
                                        ? appRoot.substring(1)
                                        : appRoot
                                        : "/" + appRoot;
                                sess.setLocale(Locale.forLanguageTag(sess.application.chooseLocale(req)));
                            } else if (req.getHead().getHeader1(HttpData.HH_ACCEPT_LANGUAGE) != null) {
                                String[] ll = req.getHead().getHeader1(HttpData.HH_ACCEPT_LANGUAGE).split(";");
                                String l = ll[0];
                                sess.setLocale(Locale.forLanguageTag(l.contains(",") ? l.split(",")[0] : l));
                            }
                            sess.expiresAt = System.currentTimeMillis() + 1000 * 60 * 15;
                            sessions.put("" + sess.getId(), sess);

                            HttpCookie cookie = new HttpCookie(
                                    sessionIdCookie,
                                    "" + sess.id,
                                    null,
                                    path,
                                    sess.expiresAt,
                                    HttpCookie.HttpOnly | (sess.isSecure() ? HttpCookie.Secure : 0)
                            );
                            sess.getCookies().put("" + sess.id, cookie);
                            req.getResponse().getHead().addHeader(HttpData.HH_SET_COOKIE, cookie.toSetString());
                        } else {
                            // check if need to extend session cookie life
                            sess.touch(1000 * 60 * 15);
                            if (sessionIdCookie != null && clientCookies.containsKey(sessionIdCookie)) {
                                HttpCookie cookie = clientCookies.get(sessionIdCookie);
                                if (!cookie.isValid() || cookie.expires != null && (cookie.expires - 1000 * 60) < sess.expiresAt) {
                                    cookie.expires = sess.expiresAt;
                                    req.getResponse().getHead().addHeader(HttpData.HH_SET_COOKIE, cookie.toSetString());
                                }
                            }
                        }
                    }

                    synchronized (sess) {
                        req.setContext(sess);

                        if (meta.getCertificates() != null) {
                            for (Certificate cert : meta.getCertificates()) {
                                HttpUser user = this.auth.authenticate(meta.getProvider(), cert);
                                if (user != null) {
                                    sess.setUser(user);
                                }
                            }
                        } else if (sess.getUser() == null && sess.getRevalidateUser() == null) {
                            if (sess.getApplication() == null || !sess.getApplication().isNoAuth(req))
                        try {
                                // try user authentication
                                HttpUser user = this.auth.authenticate(meta.getProvider(), req);
                                if (user != null) {
                                    // ignore Basic authentication if restricted in application
                                    if (!HttpUser.AUTH_TYPE.basic.equals(user.getProperties().get(HttpUser.P_AUTH_TYPE))
                                            || sess.getApplication() == null
                                            || sess.getApplication().isBasicAuthEnabled()) {
                                        sess.setUser(user);
                                    }
                                }
                            } catch (Throwable th) {
                                th.printStackTrace();
                                int a = 0;
                            }
                        }
                        if (req.getHead().isConnectionUpgrade()) {
                            //HttpData ureq=this.upgradeHttp(meta.getProvider(), req);
                            int a = 0;
                        }
                        initHttpResponseHeaders(req);
                        httpData.registerHttp(meta.getProvider(), req);
                        test(meta.getProvider(), httpData);
                        onHttpDataCreated(meta.getProvider(), req);
                    }
                    return httpData;
                case response:
                    HttpData http = httpData.http(meta.getProvider());
                    HttpResponse resp = new HttpResponse((HttpRequest) http);
                    resp.secure(meta.isSecure());
                    resp.add(data);
                    onHttpDataCreated(meta.getProvider(), resp);
                    return httpData;
            }
            return null;
        } catch (Throwable th) {
            if (th instanceof IOException) {
                throw (IOException) th;
            } else {
                throw new IOException(getClass().getName() + ".initPD unrecoverable error: " + th, th);
            }
        }
    }

    /**
     * Override to prepare default set of response headers like Connection,
     * Server etc.
     *
     * @param req
     * @throws IOException
     */
    public void initHttpResponseHeaders(HttpRequest req) throws IOException {
        if (name != null) {
            req.getResponse().setHeader(HttpData.HH_SERVER, name);
        }

        String sessionIdCookie = req.isSecure() ? sessionIdCookieHTTPS : sessionIdCookieHTTP;

        if (sessionIdCookie != null) {
            HttpCookie cookie = req.getHttpSession().getCookies().get(sessionIdCookie);
            if (cookie != null) {
                Map<String, HttpCookie> ccs = HttpSession.getSetCookies(req.getResponse());
                if (!ccs.containsKey(sessionIdCookie)) {
                    req.getResponse().setHeader(HttpData.HH_COOKIE, cookie.toGetString());
                }
            }
        }

        //System.out.println("REQ: "+req.getQuery()+"\n  "+req.toString().replace("\n", "\n    "));
        req.getResponse().setHeader(HttpData.HH_CACHE_CONTROL, HttpData.HCC_NO_CACHE + ", " + HttpData.HCC_NO_STORE);
        String accepts = req.getHead().getHeader1(HttpData.HH_ACCEPT_ENCODING);
        if (accepts != null && accepts.contains(HttpData.HTE_GZIP)) {
            req.getResponse().setHeader(HttpData.HH_CONTENT_ENCODING, HttpData.HTE_GZIP);
        }
    }

    /**
     *
     * @param http
     * @return
     * @throws IOException
     */
    public HttpData upgradeHttp(P provider, final HttpData http) throws IOException {
        if (http.getHead().isConnectionUpgrade()) {
            Repository<HttpConnectionUpgrade> cur = this.getConnectionUpgrades();
            if (cur != null) {
                String uRoot = root;
                if (uRoot == null) {
                    uRoot = "";
                }
                if (http instanceof HttpRequest && ((HttpRequest) http).getHttpSession().getApplication() != null) {
                    String aRoot = ((HttpRequest) http).getHttpSession().getApplication().getRoot();
                    uRoot += aRoot;
                }
                final String mRoot = uRoot;
                List<HttpConnectionUpgrade> cus = cur.find(new Matcher<HttpConnectionUpgrade>() {
                    @Override
                    public float match(HttpConnectionUpgrade t) {
                        if (t != null && t.testUpgrade(mRoot, http.getHead())) {
                            return 1;
                        } else {
                            return 0;
                        }
                    }

                    @Override
                    public float weight() {
                        return 1;
                    }
                }, null);
                if (cus != null || cus.isEmpty()) {
                    for (HttpConnectionUpgrade cu : cus) {
                        HttpData httpu = cu.doUpgrade(provider, http);
                        if (httpu != null) {
                            return httpu;
                        }
                    }
                }
            }
        }
        throw new IOException("Failed to upgrade connection for: " + http.getHead());
    }

    @Override
    public SERVICE_FLOW_STATE test(P provider, DI<ByteBuffer, P> pd) throws IOException {
        if (pd instanceof DIHttpData) {
            HttpData http = ((DIHttpData) pd).http(provider);
            if (http == null) {
                return SERVICE_FLOW_STATE.failed;
            } else if (http.getHead().isHeadCompleted() && http.getHead().isConnectionUpgrade()) {
                HttpData httpu = upgradeHttp(provider, http);
                if (httpu != null && httpu != http) {
                    ((DIHttpData) pd).replaceHttp(provider, httpu);
                }
                return SERVICE_FLOW_STATE.in;
            } else if (http.isSent()) {
                return SERVICE_FLOW_STATE.completed;
            } else if (http.isCompleted()) {
                return SERVICE_FLOW_STATE.out;
            } else if (!http.isCompleted()) {
                return SERVICE_FLOW_STATE.in;
            }
        }
        return SERVICE_FLOW_STATE.failed;
    }

    @Override
    public SERVICE_PROCESSING_STATE testProcessing(P provider, DI<ByteBuffer, P> pd) throws IOException {
        HttpData http = (pd instanceof DIHttpData) ? ((DIHttpData) pd).http(provider) : null;
        if (http != null && http.hasFlags(HttpData.HF_SWITCHED)) {
            return (http.isCompleted())
                    ? SERVICE_PROCESSING_STATE.OK
                    : SERVICE_PROCESSING_STATE.processing;
        }
        return SERVICE_PROCESSING_STATE.failed;
    }

    public void onHttpDataCreated(P provider, HttpData http) {
    }

    public void onHttpDataCompleted(P provider, HttpData http) {
        //System.out.println("" + provider + ": HttpDataCompleted: " + http.toString().replace("\n", "\n    "));
    }

    @Override
    public void onServiceError(P provider, DI<ByteBuffer, P> pd, Throwable error) throws IOException {
        HttpData http = (pd instanceof DIHttpData) ? ((DIHttpData) pd).http(provider) : null;
        if (DEBUG_SERVICE_ERROR) {
            System.out.println(getClass().getSimpleName() + ".onServiceError:"
                    + "\n  provider: " + provider
                    + "\n  error   : " + error
                    + ((http != null)
                            ? "\n  request : " + ((http instanceof HttpRequest) ? http.toString().replace("\n", "\n    ") : "<none>")
                            + "\n  response: " + ((http instanceof HttpRequest) ? ("" + ((HttpRequest) http).getResponse()).replace("\n", "\n    ") : "<none>")
                            : "")
            );
        }
        if (http != null) {
            if (http instanceof HttpRequest) {
                HttpRequest req = (HttpRequest) http;
                if (req.getResponse() != null) {
                    HttpResponse resp = req.getResponse();
                    if (!resp.getHead().isSent() || !resp.isSent() && resp.getHead().getProtocolInfo() != null && "200".equals(resp.getHead().getProtocolInfo()[1])) {
                        resp.setResponseCode(500, "Service error: " + ("" + error.toString()).replace("\r", "\\r").replace("\n", "\\n").replace(" ", "_"));
                        resp.setHeader(HttpData.HH_CONTENT_TYPE, "text/plain; encoding=utf-8");
                        byte[] data = ("Query: " + req.getQuery() + "\nError: " + BufferTools.dummpErrorMessages(error) + "").getBytes("UTF-8");
                        resp.setHeader(HttpData.HH_CONTENT_LENGTH, "" + data.length);
                        resp.onHeaderLoaded();
                        resp.add(Collections.singletonList(ByteBuffer.wrap(data)));
                        //resp.getBody().add(data);
                        //resp.onLoaded();
                    } else {
                        resp.cancel();
                    }
                }
            }
        }
    }

    @Override
    public Repository<DataProcessor> getDataProcessors(P provider, DI<ByteBuffer, P> pd) {
        if (provider != null && pd != null) {
            HttpData http = ((DIHttpData) pd).http(provider);
            if (http != null && http instanceof HttpRequest && ((HttpRequest) http).getHttpSession() != null) {
                HttpSession sess = ((HttpRequest) http).getHttpSession();
                if (sess.getApplication() != null && sess.getApplication().getDataProcessors() != null) {
                    if (dataProcessors != null) {
                        return new CombinedRepository<DataProcessor>(sess.getApplication().getDataProcessors(), dataProcessors);
                    } else {
                        return sess.getApplication().getDataProcessors();
                    }
                }
            }
        }
        return dataProcessors;
    }

    /**
     * @param dataProcessors the dataProcessors to set
     */
    public void setDataProcessors(Repository<DataProcessor> dataProcessors) {
        if (this.dataProcessors != null) {
            this.dataProcessors.removeOwner(this);
        }
        this.dataProcessors = dataProcessors;
        if (dataProcessors != null) {
            dataProcessors.addOwner(this);
        }
    }

    public boolean isAllowedProvider(P provider) {
        return provider != null && provider.isOpen();
    }

    /**
     * @return the options
     */
    public long getOptions() {
        return options;
    }

    /**
     * @param options the options to set
     */
    public void setOptions(long options) {
        this.options = options;
    }

    /**
     * @return the connectionUpgrades
     */
    public Repository<HttpConnectionUpgrade> getConnectionUpgrades() {
        return connectionUpgrades;
    }

    /**
     * @param connectionUpgrades the connectionUpgrades to set
     */
    public void setConnectionUpgrades(Repository<HttpConnectionUpgrade> connectionUpgrades) {
        if (this.connectionUpgrades != null) {
            this.connectionUpgrades.removeOwner(this);
        }
        this.connectionUpgrades = connectionUpgrades;
        if (connectionUpgrades != null) {
            connectionUpgrades.addOwner(this);
        }
    }

    @Override
    public List<Task> getTasks(TaskPhase... phases) {
        if (phases == null || phases.length == 0) {
            return Collections.emptyList();
        }
        List<Task> r = new ArrayList<>();

        if (dataProcessors != null) {
            for (DataProcessor dp : dataProcessors.items()) {
                if (dp == null) {
                    continue;
                }
                List<Task> dpr = dp.getTasks(phases);
                if (dpr != null) {
                    r.addAll(dpr);
                }
            }
        }

        for (HttpApplication dp : this.applications.items()) {
            List<Task> dpr = dp.getTasks(phases);
            if (dpr != null) {
                r.addAll(dpr);
            }
        }

        Collections.sort(r, TaskProvider.getTaskComparator(true));
        return r;
    }

    @Override
    public String toString() {
        return "HttpService{"
                + "\n  defaultConnectionBehaviour=" + defaultConnectionBehaviour
                + "\n  name=" + name
                + "\n  options=" + options
                + "\n  dataProcessors=" + dataProcessors
                + "\n  connectionUpgrades=" + connectionUpgrades
                + "\n  applications=" + applications
                + "\n  root=" + root
                + "\n  maxURILength=" + maxURILength
                + "\n  sessionIdCookieHTTP=" + sessionIdCookieHTTP
                + "\n  sessionIdCookieHTTPS=" + sessionIdCookieHTTPS
                + "\n  httpData=" + httpData
                + "\n  auth=" + auth
                + "\n  sessions=" + sessions
                + '\n'
                + '}';
    }

}
