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
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.api.API_Publisher;
import ssg.lib.api.API_Publisher.API_Publishers;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.dp.HttpResourceBytes;
import ssg.lib.http.dp.HttpStaticDataProcessor;
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

    // API support
    Map<String, Map<String, APIGroup>> apis = new LinkedHashMap<>();
    Collection<String> registeredRESTAPIs = new HashSet<>();
    APIStatistics apiStat;
    StubVirtualData<?> stub;
    HttpStaticDataProcessor stubDP;

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

    public APIRunner(HttpApplication app, APIStatistics stat) {
        super(app);
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
        if (this.stub == null && stub != null) {
            initHttp();
            this.stub = stub;
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

        final String realm = group.realm;
        API_Publishers apis = group.apis;
        final Collection<String> apiNames = names != null ? names : group.apis.getAPINames();
        if (apiNames == null || apiNames.isEmpty()) {
            return null;
        }

        T client = initClient(wsURI, group, "api-pub");

        // add callable APIs
        for (String apiName : apiNames) {
            if (apiName != null) {
                final API_Publisher api = apis.getAPIPublisher(apiName);
                // enable API-level publishing
                onPublishingAPI(wsURI, group, client, apiName, api, null);
                for (final String pn : apis.getNames(apiName)) {
                    // proceed with procedure-level publishing
                    onPublishingAPI(wsURI, group, client, apiName, api, pn);
                    onPublishedAPI(wsURI, group, client, apiName, api);
                }
            }
        }
        // connect and register API calls
        onPublishedAPI(wsURI, group, client, null, null);
        return client;
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

    public StubVirtualData<?> getStub() {
        return stub;
    }
}
