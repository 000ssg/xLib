/*
 * The MIT License
 *
 * Copyright 2021 000ssg.
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
package ssg.lib.http_cs;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.common.Config;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.HttpAuthenticator;
import ssg.lib.http.HttpAuthenticator.Domain;
import ssg.lib.http.HttpAuthenticator.HttpSimpleAuth;
import ssg.lib.http.HttpAuthenticator.UserVerifier;
import ssg.lib.http.HttpDataProcessor;
import ssg.lib.http.HttpMatcher;
import ssg.lib.http.HttpService;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.dp.HttpDataProcessorFavIcon;
import ssg.lib.http.dp.HttpResourceBytes;
import ssg.lib.http.dp.HttpStaticDataProcessor;
import ssg.lib.http.rest.MethodsProvider;
import ssg.lib.http.rest.RESTHttpDataProcessor;
import ssg.lib.http.rest.StubRESTHttpContext;
import ssg.lib.http.rest.StubVirtualData;
import ssg.lib.http_cs.AuthAdapter.AuthAdapterConf;
import ssg.lib.http_cs.RESTAdapter.RESTAdapterConf;
import ssg.lib.net.CS;
import ssg.lib.net.TCPHandler;
import ssg.lib.service.Repository;

/**
 * Common base for deployable runnable. Provides non-reentrable start/stop to
 * allow CS embedding.
 *
 * @author 000ssg
 */
public class HttpRunner extends CS {

    public static final String CFG_HTTP_PORT = "httpPort";
    public static final String CFG_REST_PATH = "restPath";
    public static final String CFG_AUTH = "auth";

    // builders/holders
    Http http;
    Integer httpPort;

    // http
    HttpApplication app;
    RESTHttpDataProcessor rest;
    URI restURI;
    List<StubVirtualData<?>> stubs;
    HttpStaticDataProcessor stubDP;
    //
    AuthAdapter authAdapter = new AuthAdapter();
    RESTAdapter restAdapter = new RESTAdapter();
    Map<String, Object> properties = new LinkedHashMap<>();
    transient Collection<RESTAdapterConf> pendingRESTDefs = new LinkedHashSet<>();

    // embedding CS support: nested (indirect) calls to start/stop are safe
    private transient Boolean starting = false;
    private transient Boolean stopping = false;

    @Override
    public void stop() throws IOException {
        synchronized (stopping) {
            if (!stopping) {
                stopping = true;
                onStopping();
                super.stop();
            }
        }
    }

    @Override
    public void start() throws IOException {
        synchronized (starting) {
            if (!starting) {
                starting = true;
                super.start();
                onStarted();
            }
        }
    }

    public HttpRunner() {
    }

    public HttpRunner(HttpApplication app) {
        this.app = app;
        http = new Http();
        http.configureApp(app);
    }

    public HttpRunner(HttpAuthenticator auth, HttpApplication app) {
        this.app = app;
        http = new Http(auth);
        http.configureApp(app);
    }

    public HttpRunner configureRESTAdapter(RESTAdapter restAdapter) {
        if (restAdapter != null) {
            this.restAdapter = restAdapter;
        }
        return this;
    }

    public HttpRunner configureAuthAdapter(AuthAdapter authAdapter) {
        if (authAdapter != null) {
            this.authAdapter = authAdapter;
        }
        return this;
    }

    /**
     * Add or replace HTTP authentication domain.
     *
     * @param domain
     * @return
     * @throws IOException
     */
    public HttpRunner configureDomain(Domain domain) throws IOException {
        if (getService().getAuthenticator() instanceof HttpSimpleAuth) {
            ((HttpSimpleAuth) getService().getAuthenticator()).configureDomain(domain);
        } else {
            getService().configureAuthentication(domain);
        }
        return this;
    }

    /**
     * Apply configurations if applicable.
     *
     * @param configs
     * @return
     * @throws IOException
     */
    @Override
    public HttpRunner configuration(Config... configs) throws IOException {
        super.configuration(configs);
        if (configs != null) {
            for (Config config : configs) {
                if (config instanceof HttpConfig) {
                    applyConfig((HttpConfig) config);
                }
            }
        }
        return this;
    }

    /**
     * Use Config instance to set compatible configuration parameters.
     *
     * @param config
     * @throws IOException
     */
    public void applyConfig(HttpConfig config) throws IOException {
        // http/rest
        initHttp();
        HttpConfig hc = (HttpConfig) config;
        if (hc.httpPort != null) {
            configureHttp(hc.httpPort);
        }
        if (hc.rest != null) {
            configureREST(hc.rest);
        }
        // authentication
        String domainName = getApp() != null ? getApp().getRoot() : "/";
        if (hc.authDomain != null || hc.tokenDelegate != null) {
            if (getService().getAuthenticator() instanceof HttpSimpleAuth) {
                if (((HttpSimpleAuth) getService().getAuthenticator()).domain(domainName) == null) {
                    Domain domain = new Domain(domainName);
                    ((HttpSimpleAuth) getService().getAuthenticator()).configureDomain(domain);
                }
            }
        }
        if (hc.basicAuth != null && getApp() != null) {
            getApp().configureBasicAuthentication(hc.basicAuth);
        }
        if (hc.tokenDelegate != null && hc.tokenDelegate.length > 0) {
            for (String td : hc.tokenDelegate) {
                for (String s : hc.tokenDelegate) {
                    AuthAdapterConf aac = authAdapter.createAuthadapterConf(s);
                    UserVerifier uv = authAdapter.createUserVerifier(aac);
                    if (uv != null) {
                        if (getService().getAuthenticator() instanceof HttpSimpleAuth) {
                            ((HttpSimpleAuth) getService().getAuthenticator()).domain(domainName)
                                    .getUserStore().verifiers().add(uv);
                        }
                    }
                }
            }
        }
        // rest publishing/stubs
        if (hc.stub != null) {
            String router_root = getApp() != null ? getApp().getRoot() + "/" : "/";
            String[] groups = hc.stub;
            Collection<String> types = new LinkedHashSet<>();
            for (String group : groups) {
                String[] ss = group.split(",");
                if (ss.length > 1) {
                    for (int i = 1; i < ss.length; i++) {
                        types.add(ss[i].trim());
                    }
                }
            }
            StubVirtualData svd = new StubVirtualData(router_root.substring(0, router_root.length() - 1), null, getREST(), types.toArray(new String[types.size()]))
                    .configure(new StubRESTHttpContext(null, null, true));
            for (String group : groups) {
                String[] ss = group.split(",");
                svd.configure(ss[0], Arrays.copyOfRange(ss, 1, ss.length));
            }
            configureStub(svd);
        }
        if (hc.context != null) {
            for (String c : hc.context) try {
                String[] cc = c.split("=");
                Class cl = Class.forName(cc.length > 1 ? cc[1] : cc[0]);
                Object instance = cl.newInstance();
                restAdapter.configureContext(cc[0], instance);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        if (hc.publish != null) {
            for (String c : hc.publish) {
                RESTAdapterConf conf = restAdapter.createRESTAdapterConf(c);
                MethodsProvider[] methodProviders = restAdapter.getMethodProviders(conf);
                Object[] contexts = restAdapter.getProviders(conf);
                if (contexts != null && contexts.length > 0 && contexts[0] != null) {
                    if (getREST() != null) {
                        getREST().registerProviders(conf.name != null ? new HttpMatcher(conf.name) : null, methodProviders, contexts);
                    } else {
                        pendingRESTDefs.add(conf);
                    }
                }
            }
        }
    }

    public void configUpdated(String key, Object oldValue, Object newValue) {
    }

    public AuthAdapter authAdapter() {
        return authAdapter;
    }

    public RESTAdapter restAdapter() {
        return restAdapter;
    }

    public void initHttp() {
        if (http == null) {
            http = new Http();
            Repository<HttpDataProcessor> rep = http.getHttpService().getDataProcessors(null, null);
            if (rep == null) {
                rep = new Repository<>();
                http.getHttpService().setDataProcessors(rep);
            }
            rep.addItem(new HttpDataProcessorFavIcon() {

                @Override
                public boolean isPreventCacheing(HttpData data) {
                    return false;
                }
            });
        }
    }

    public Integer getHttpPort() {
        return httpPort;
    }

    public HttpRunner configureHttp(Integer httpPort) {
        Integer old = this.httpPort;
        this.httpPort = httpPort;
        configUpdated(CFG_HTTP_PORT, old, httpPort);
        return this;
    }

    public HttpRunner configureREST(String path) throws IOException {
        if (rest == null && path != null) {
            if (getApp() != null) {
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
            } else {
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
            }

            rest = new RESTHttpDataProcessor(path);
            if (getApp() == null) {
                initHttp();
                if (http.getHttpService().getDataProcessors(null, null) == null) {
                    http.getHttpService().setDataProcessors(new Repository<>());
                }
                http.getHttpService().getDataProcessors(null, null).addItem(rest);
            } else {
                if (getApp().getDataProcessors() == null) {
                    getApp().setDataPorcessors(new Repository<>());
                }
                getApp().configureDataProcessor(1, rest);
            }
            configUpdated(CFG_REST_PATH, null, path);
        } else {
            throw new IOException("REST configuration is defined already.");
        }
        return this;
    }

    public HttpRunner configureStub(StubVirtualData<?> stub) {
        if (stubs == null && stub != null) {
            initHttp();
            stubs = new ArrayList<>();
            stubs.add(stub);
            String router_root = getApp() != null ? getApp().getRoot() + "/" : "/";
            stubDP = new HttpStaticDataProcessor();
            for (StubVirtualData.WR wr : stub.resources()) {
                stubDP.add(new HttpResourceBytes(stub, wr.getPath())); //, "text/javascript; encoding=utf-8"));
            }
            if (getApp() != null) {
                getApp().configureDataProcessor(0, stubDP);
            } else {
                getService().configureDataProcessor(0, stubDP);
            }
        } else if (stub != null) {
            stubs.add(stub);
            for (StubVirtualData.WR wr : stub.resources()) {
                stubDP.add(new HttpResourceBytes(stub, wr.getPath()));
            }
        }
        return this;
    }

    public List<StubVirtualData<?>> getStubs() {
        return stubs;
    }

    public void onStarted() throws IOException {
        if (httpPort != null) {
            if (http == null) {
                initHttp();
            }
            add(new TCPHandler(new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), httpPort)).defaultHandler(http.getDI()));
            if (getREST() != null) {
                String path = app != null ? app.getRoot() : "";
                if (!path.endsWith("/") || !getREST().getMatcher().getPath().startsWith("/")) {
                    path += "/";
                }
                path += getREST().getMatcher().getPath();
                try {
                    restURI = new URI("http://localhost:" + httpPort + path);
                } catch (Throwable th) {
                }
                if (!pendingRESTDefs.isEmpty()) {
                    synchronized (pendingRESTDefs) {
                        for (RESTAdapterConf conf : pendingRESTDefs) {
                            MethodsProvider[] methodProviders = restAdapter.getMethodProviders(conf);
                            Object[] contexts = restAdapter.getProviders(conf);
                            if (contexts != null && contexts.length > 0 && contexts[0] != null) {
                                getREST().registerProviders(conf.name != null ? new HttpMatcher(conf.name) : null, methodProviders, contexts);
                            }
                        }
                        pendingRESTDefs.clear();
                    }
                }
            }
        }
    }

    public void onStopping() throws IOException {

    }

    public HttpApplication getApp() {
        return app;
    }

    public RESTHttpDataProcessor getREST() {
        return rest;
    }

    public URI getREST_URI() {
        return restURI;
    }

    public HttpService getService() {
        return http != null ? http.getHttpService() : null;
    }

    public <T> T getProperty(String name) {
        return (T) properties.get(name);
    }

    public void setProperty(String name, Object value) {
        if (name != null) {
            properties.put(name, value);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append('{');
        sb.append("httpPort=" + httpPort);
        sb.append(", restURI=" + restURI);
        sb.append(", starting=" + starting);
        sb.append(", stopping=" + stopping);
        if (1 == 0 && http != null) {
            sb.append("\n  http=" + http.toString().replace("\n", "\n  "));
        }
        if (app != null) {
            sb.append("\n  app=" + app.toString().replace("\n", "\n  "));
        }
        if (rest != null) {
            //sb.append("\n  rest=" + rest.toString().replace("\n", "\n  "));
        }
        if (!properties.isEmpty()) {
            sb.append("\n  properties[" + properties.size() + "]");
            for (Entry e : properties.entrySet()) {
                sb.append("\n    " + e.getKey() + "=" + ("" + e.getValue()).replace("\n", "\n    "));
            }
        }
        sb.append('\n');
        sb.append('}');
        return sb.toString();
    }

    public static class HttpConfig extends Config {

        public static final String DEFAULT_BASE = "app.http";

        @Description("Default HTTP service port, int")
        public Integer httpPort;
        @Description("REST path. Absolute (starts with '/') or relative (within application)")
        public String rest;
        @Description(value = "REST code stub (generated code for client use)", pattern = "group=realm,stub type[,stub type]")
        public String[] stub;
        @Description(value = "Class aliases", pattern = "[name=]class name")
        public String[] context;
        @Description(value = "Interface or class to publish in REST", pattern = "[name=pre-path;]item=class or alias name[;type=x,wsdl,springboot,reflection or class name (implementing MethodsProvider)]")
        public String[] publish;
        @Description("Defines domain name for authentication. If empty string - application root is the domain (if any)")
        public String authDomain;
        @Description("Enable/disable basic authentication.")
        public Boolean basicAuth;
        @Description(
                value = "Delegated token authentication support implemented with AuthAdapter.",
                pattern = "type=(jwt|token)[;uri=(verificator URI)][;secret=(secret key)][;secretHeader=(header name for secret)][;tokenPrefix=(token prefix to select this verifier)]))")
        public String[] tokenDelegate;

        public HttpConfig(String base, String... args) {
            super(base != null ? base : DEFAULT_BASE, args);
        }
    }
}
