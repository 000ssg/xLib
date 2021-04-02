/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ssg.lib.httpapi_cs;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.api.API_Publisher;
import ssg.lib.api.API_Publisher.API_Publishers;
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
    //Collection<String> registeredRESTAPIs = new HashSet<>();
    APIStatistics apiStat;
    APIAdapter adapter;

    /**
     * API group enables defining multiple APIs for same context, that is
     * namespace, authid, and uri.
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
        public String namespace;
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
            return "APIGroup{" + "uri=" + uri + ", authid=" + authid + ", agent=" + agent + ", namespace=" + namespace + ", apis=" + apis + ", clients=" + clients.keySet() + '}';
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
     * Add API to namespace (WAMP client functionality and/or REST)
     *
     * @param namespace
     * @param name
     * @param api
     * @return
     */
    public APIRunner configureAPI(String namespace, String name, API_Publisher api) {
        return this.configureAPI(namespace, name, api, null, null);
    }

    /**
     * Add API to namespace (WAMP client functionality and/or REST)
     *
     * @param namespace
     * @param name
     * @param api
     * @param uri
     * @return
     */
    public APIRunner configureAPI(String namespace, String name, API_Publisher api, URI uri) {
        return this.configureAPI(namespace, name, api, uri, null);
    }

    /**
     * Add API to namespace (WAMP client functionality and/or REST)
     *
     * @param namespace
     * @param name
     * @param api
     * @param uri
     * @param authid
     * @return
     */
    public APIRunner configureAPI(String namespace, String name, API_Publisher api, URI uri, String authid) {
        return this.configureAPI(namespace, name, api, uri, authid, null);
    }

    /**
     * Add API to namespace (WAMP client functionality and/or REST)
     *
     * @param namespace
     * @param name
     * @param api
     * @param uri
     * @param authid
     * @param options
     * @return
     */
    public APIRunner configureAPI(String namespace, String name, API_Publisher api, URI uri, String authid, Long options) {
        Map<String, APIGroup> groups = apis.get(namespace);
        if (groups == null) {
            groups = new LinkedHashMap<>();
            apis.put(namespace, groups);
        }
        APIGroup group = groups.get(authid != null ? authid : "");
        if (group == null) {
            group = new APIGroup();
            group.uri = uri;
            group.authid = authid;
            group.namespace = namespace;
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

    public APIRunner configureAPIAdapter(APIAdapter adapter) {
        this.adapter = adapter;
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

    /**
     * Initialize APIRunner-specifics from APIConf, especially conf.api entries
     * for building API groups.
     *
     * @param config
     * @throws IOException
     */
    public void initAPI(APIConfig config) throws IOException {
        if (config.api != null && !config.api.isEmpty()) try {

            for (String api : config.api) {
                if (api == null || api.isEmpty()) {
                    continue;
                }

                APIAdapter.APIAdapterConf apiConf = adapter.createAPIAdapterConf(api);
                configureAPI(apiConf.namespace, apiConf.name, new API_Publisher()
                        .configureContext((Collection) adapter.getContexts(apiConf))
                        .configure(adapter.createAPI(apiConf)),
                        apiConf.uri != null ? new URI(apiConf.uri) : null,
                        apiConf.authid,
                        apiConf.prefixed ? APIGroup.O_COMPACT : null
                );
            }
        } catch (URISyntaxException usex) {
            usex.printStackTrace();
        } catch (IOException ioex) {
            ioex.printStackTrace();
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
            final String namespace = group.namespace;
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
            group.apiStat = apiStat.createChild(null, group.namespace + "/" + (group.authid != null ? group.authid : "<no-auth>"));
        }
        return group != null ? group.apiStat : apiStat;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.toString());
        sb.delete(sb.length() - 2, sb.length());
        if (adapter != null) {
            sb.append("\n  adapter=" + adapter.toString().replace("\n", "\n  "));
        }
        sb.append("\n  apis=" + apis.size());
        for (Entry<String, Map<String, APIGroup>> e : apis.entrySet()) {
            sb.append("\n    " + e.getKey() + "[" + e.getValue().size() + "]");
            for (Entry<String, APIGroup> e2 : e.getValue().entrySet()) {
                sb.append("\n      " + e2.getKey() + ": " + e2.getValue().toString().replace("\n", "\n      "));
            }
        }
//        sb.append("\n  registeredRESTAPIs[" + registeredRESTAPIs.size()+"]");
//        int off = 0;
//        for (String s : registeredRESTAPIs) {
//            if (off % 4 == 0) {
//                sb.append("\n    ");
//            } else {
//                sb.append(", ");
//            }
//            sb.append(s);
//        }
        if (apiStat != null) {
            sb.append("\n  apiStat=" + apiStat.toString().replace("\n", "\n  "));
        }
        sb.append('\n');
        sb.append('}');
        return sb.toString();
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
