/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ssg.lib.httpapi_cs;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.api.API_Publisher;
import ssg.lib.api.API_Publisher.API_Publishers;
import ssg.lib.api.util.Reflective_API_Builder;
import ssg.lib.common.Config;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.HttpAuthenticator;
import ssg.lib.http.rest.MethodsProvider;
import ssg.lib.http.rest.StubVirtualData;
import ssg.lib.http_cs.HttpRunner;

/**
 * API runner defines WAMP/REST publishing and WAMP routing functionality to
 * enable simple mechanism of API exposure.
 *
 * @author 000ssg
 */
public class APIRunner<T> extends HttpRunner {

    public static final String CFG_API_NAME = "apiName";
    public static final String API_PUB_CLIENT_TITLE = "api-pub";

    // API support
    Map<String, Map<String, APIGroup>> apis = new LinkedHashMap<>();
    Collection<String> registeredRESTAPIs = new HashSet<>();
    APIStatistics apiStat;

    /**
     * API group enables defining multiple APIs for same context, that is realm,
     * authid, and uri.
     *
     * authid and uri are optional.
     *
     * On connection API groups are treated separately by establishing per-group
     * connections.
     *
     */
    public class APIGroup {

        public static final long O_NONE = 0x0000;
        public static final long O_COMPACT = 0x0001;

        public URI uri;
        public String authid;
        public String agent;
        public String realm;
        public long options = O_NONE;
        public API_Publisher.API_Publishers apis = new API_Publisher.API_Publishers();
        public Map<URI, T> clients = new LinkedHashMap<>();
        public APIStatistics apiStat;

        public void connect(URI uri) throws IOException {
            // publish APIs to DEMO WAMP namespace..., implicitly as REST bridge to WAMP.
            T client = APIRunner.this.publishAPI(uri, this, apis.getAPINames());
            if (client != null) {
                clients.put(uri, client);
            }
        }

        @Override
        public String toString() {
            return "APIGroup{" + "uri=" + uri + ", authid=" + authid + ", agent=" + agent + ", realm=" + realm + ", apis=" + apis + ", clients=" + clients.keySet() + '}';
        }

    }

    public APIRunner() {
    }

    public APIRunner(HttpApplication app) {
        super(app);
    }

    public APIRunner(HttpAuthenticator auth, HttpApplication app) {
        super(auth, app);
    }

    public APIRunner(HttpApplication app, APIStatistics stat) {
        super(app);
        this.apiStat = stat;
    }

    public APIRunner(HttpAuthenticator auth, HttpApplication app, APIStatistics stat) {
        super(auth, app);
        this.apiStat = stat;
    }

    public APIRunner configureAPIStatistics(APIStatistics stat) {
        this.apiStat = stat;
        return this;
    }

    @Override
    public APIRunner configureREST(String path) throws IOException {
        return (APIRunner) super.configureREST(path);
    }

    @Override
    public APIRunner configureHttp(Integer httpPort) {
        return (APIRunner) super.configureHttp(httpPort);
    }

    /**
     * Add API to realm (WAMP client functionality and/or REST)
     *
     * @param realm
     * @param name
     * @param api
     * @return
     */
    public APIRunner configureAPI(String realm, String name, API_Publisher api) {
        return this.configureAPI(realm, name, api, null, null);
    }

    /**
     * Add API to realm (WAMP client functionality and/or REST)
     *
     * @param realm
     * @param name
     * @param api
     * @param uri
     * @return
     */
    public APIRunner configureAPI(String realm, String name, API_Publisher api, URI uri) {
        return this.configureAPI(realm, name, api, uri, null);
    }

    /**
     * Add API to realm (WAMP client functionality and/or REST)
     *
     * @param realm
     * @param name
     * @param api
     * @param uri
     * @param authid
     * @return
     */
    public APIRunner configureAPI(String realm, String name, API_Publisher api, URI uri, String authid) {
        return this.configureAPI(realm, name, api, uri, authid, null);
    }

    /**
     * Add API to realm (WAMP client functionality and/or REST)
     *
     * @param realm
     * @param name
     * @param api
     * @param uri
     * @param authid
     * @param options
     * @return
     */
    public APIRunner configureAPI(String realm, String name, API_Publisher api, URI uri, String authid, Long options) {
        Map<String, APIGroup> groups = apis.get(realm);
        if (groups == null) {
            groups = new LinkedHashMap<>();
            apis.put(realm, groups);
        }
        APIGroup group = groups.get(authid != null ? authid : "");
        if (group == null) {
            group = new APIGroup();
            group.uri = uri;
            group.authid = authid;
            group.realm = realm;
            groups.put(authid != null ? authid : "", group);
            if (options != null) {
                group.options = options;
            }
        }
        group.apis.add(name != null ? name : api.getAPI().name, api);
        configUpdated(CFG_API_NAME, null, name != null ? name : api.getAPI().name);
        return this;
    }

    public APIRunner configureStub(StubVirtualData<?> stub) {
        super.configureStub(stub);
        return this;
    }

    @Override
    public APIRunner configuration(Config... configs) throws IOException {
        super.configuration(configs);
        if (configs != null) {
            for (Config cfg : configs) {
                if (cfg instanceof APIConfig) {
                    initAPI((APIConfig) cfg);
                }
            }
        }
        return this;
    }

    @Override
    public void onStarted() throws IOException {
        super.onStarted();
        if (!apis.isEmpty()) {
            URI[] uris = publishingURIs(null);
            for (Entry<String, Map<String, APIGroup>> ge : apis.entrySet()) {
                for (Entry<String, APIGroup> entry : ge.getValue().entrySet()) {
                    APIGroup group = entry.getValue();
                    for (URI uri : group.uri != null ? new URI[]{group.uri} : uris) {
                        group.connect(uri);
                    }
                }
            }
        }
    }

    /**
     * Enable multiple URIs for publishing APIs.
     *
     * @param uri
     * @return
     */
    public URI[] publishingURIs(URI uri) {
        return new URI[]{uri};
    }

    /**
     * Placeholder for actual initializer when needed.
     *
     * @param uri
     * @param group
     * @param title
     * @return
     */
    public T initClient(URI uri, APIGroup group, String title) {
        return null;
    }

    public void initAPI(APIConfig config) throws IOException {
        if (config.api != null && !config.api.isEmpty()) try {
            for (String api : config.api) {
                if (api == null || api.isEmpty()) {
                    continue;
                }
                String[] ss = api.split(";");
                String uri = null;
                String realm = null;
                String name = null;
                String authid = null;
                String type = "reflection";
                String clazz = null;
                boolean prefixed = false;
                for (String s : ss) {
                    if (s.contains("=")) {
                        int idx = s.indexOf("=");
                        String n = s.substring(0, idx);
                        String v = s.substring(idx + 1);
                        if ("uri".equals(n)) {
                            uri = v;
                        } else if ("realm".equals(n)) {
                            realm = v;
                        } else if ("type".equals(n)) {
                            type = v;
                        } else if ("name".equals(n)) {
                            name = v;
                        } else if ("authid".equals(n)) {
                            authid = v;
                        } else if ("class".equals(n)) {
                            clazz = v;
                        }
                    } else {
                        if ("prefixed".equals(s.trim())) {
                            prefixed = true;
                        }
                    }
                }
                if (realm == null || clazz == null || name == null) {
                    break;
                }
                URI apiURI = null;
                if (uri != null) try {
                    apiURI = new URI(uri);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                String[] realms = realm.split(",");
                String[] types = type.split(",");
                String[] classes = clazz.split(",");
                for (String r : realms) {
                    for (String t : types) {
                        initAPI(apiURI, r, name, authid, prefixed, t, classes);
                    }
                }
            }
        } catch (IOException ioex) {
            ioex.printStackTrace();
        }
    }

    void initAPI(URI uri, String realm, String name, String authid, boolean prefixed, String type, String... clazz) throws IOException {
        Class cl = null;
        Throwable error = null;
        try {
            Map<Class, Object> cls = new LinkedHashMap<>();
            for (String clz : clazz) {
                cl = this.contextClass(clz);
                Object obj = getContext(clz);
                if (obj == null) {
                    obj = createContext(clz);
                }
                cls.put(cl, obj);
            }
            configureAPI(realm, name, new API_Publisher()
                    .configure(Reflective_API_Builder.buildAPI(name, null, cls.keySet().toArray(new Class[cls.size()])))
                    .configureContext(cls.values()),
                    uri,
                    authid,
                    prefixed ? APIGroup.O_COMPACT : null
            );
        } catch (Throwable th) {
            error = th;
            if (th instanceof IOException) {
                throw (IOException) th;
            }
            throw new IOException(th);
        } finally {
            System.out.println("API: " + realm + ":" + type + ":" + Arrays.asList(clazz) + " (" + (cl != null ? cl.getName() : "<no class>") + ")" + (error != null ? ", error: " + error : ""));
        }
    }

    /**
     * Publish apis (names) from API group. If no names - all API group apis are
     * published.
     *
     * @param wsURI
     * @param group
     * @param names
     * @return
     * @throws IOException
     */
    public T publishAPI(final URI wsURI, APIGroup group, String... names) throws IOException {
        return publishAPI(wsURI, group, names != null ? Arrays.asList(names) : null);
    }

    /**
     * Publish apis (names) from API group. If no names - all API group apis are
     * published.
     *
     * @param wsURI
     * @param group
     * @param names
     * @return
     * @throws IOException
     */
    public T publishAPI(final URI wsURI, APIGroup group, Collection<String> names) throws IOException {
        if (group == null) {
            return null;
        }

        onBeforePublishAPI(wsURI, group, names);

        try {
            final String realm = group.realm;
            API_Publishers apis = group.apis;
            final Collection<String> apiNames = names != null ? names : group.apis.getAPINames();
            if (apiNames == null || apiNames.isEmpty()) {
                return null;
            }

            T client = initClient(wsURI, group, API_PUB_CLIENT_TITLE);

            // add callable APIs
            for (String apiName : apiNames) {
                if (apiName != null) {
                    final API_Publisher api = apis.getAPIPublisher(apiName);
                    // enable API-level publishing
                    onPublishingAPI(wsURI, group, client, apiName, api, null);
                    for (final String pn : apis.getNames(apiName)) {
                        // proceed with procedure-level publishing
                        onPublishingAPI(wsURI, group, client, apiName, api, pn);
                    }
                    onPublishedAPI(wsURI, group, client, apiName, api);
                }
            }
            // connect and register API calls
            onPublishedAPI(wsURI, group, client, null, null);
            return client;
        } finally {
            onAfterPublishAPI(wsURI, group, names);
        }
    }

    /**
     * API publishing pre-event
     *
     * @param wsURI
     * @param group
     * @param names
     */
    public void onBeforePublishAPI(URI wsURI, APIGroup group, Collection<String> names) {
    }

    /**
     * API publishing post-event
     *
     * @param wsURI
     * @param group
     * @param names
     */
    public void onAfterPublishAPI(URI wsURI, APIGroup group, Collection<String> names) {
    }

    /**
     * Client-specific API publishing.
     *
     * @param uri
     * @param group
     * @param client
     * @param apiName
     * @param api
     * @param procedure
     */
    public void onPublishingAPI(URI uri, APIGroup group, T client, String apiName, API_Publisher api, String procedure) {
    }

    /**
     * Invoked once API is published to allow post-processing
     *
     * @param uri
     * @param group
     * @param client
     * @param apiName
     * @param api
     * @throws IOException
     */
    public void onPublishedAPI(URI uri, APIGroup group, T client, String apiName, API_Publisher api) throws IOException {
        if (client == null && getREST() != null) {
            getREST().registerProviders(new MethodsProvider[]{new API_MethodsProvider(getAPIStatistics(group))}, api);
        }
    }

    public Map<String, Map<String, APIGroup>> getAPIGroups() {
        return apis;
    }

    public APIStatistics getAPIStatistics(APIGroup group) {
        if (apiStat != null && group != null && group.apiStat == null) {
            group.apiStat = apiStat.createChild(null, group.realm + "/" + (group.authid != null ? group.authid : "<no-auth>"));
        }
        return group != null ? group.apiStat : apiStat;
    }

    public static class APIConfig extends Config {

        public APIConfig() {
            super("app.api");
        }

        public APIConfig(String base, String... args) {
            super(base, args);
        }

        public List<String> api;
    }
}
