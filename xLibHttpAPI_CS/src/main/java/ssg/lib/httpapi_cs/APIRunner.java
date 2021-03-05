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
import ssg.lib.http.rest.MethodsProvider;
import ssg.lib.http_cs.HttpRunner;

/**
 * API runner defines WAMP/REST publishing and WAMP routing functionality to
 * enable simple mechanism of API exposure.
 *
 * @author sesidoro
 */
public class APIRunner<T> extends HttpRunner {

    public static final String CFG_API_NAME = "apiName";

    // API support
    Map<String, APIGroup> apis = new LinkedHashMap<>();
    Collection<String> registeredRESTAPIs = new HashSet<>();

    public class APIGroup {

        public String agent;
        public String realm;
        public API_Publisher.API_Publishers apis = new API_Publisher.API_Publishers();
        public Map<URI, T> clients = new LinkedHashMap<>();

        public void connect(URI uri) throws IOException {
            // publish APIs to DEMO WAMP namespace..., implicitly as REST bridge to WAMP.
            T client = publishPrefixedAPI(uri, realm, apis, apis.getAPINames());
            if (client != null) {
                clients.put(uri, client);
            }
        }
    }

    public APIRunner() {
    }

    public APIRunner(HttpApplication app) {
        super(app);
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
        APIGroup group = apis.get(realm);
        if (group == null) {
            group = new APIGroup();
            group.realm = realm;
            apis.put(realm, group);
        }
        group.apis.add(name != null ? name : api.getAPI().name, api);
        configUpdated(CFG_API_NAME, null, name != null ? name : api.getAPI().name);
        return this;
    }

//    /**
//     * Publish APIs for realm on given router.
//     *
//     * @param router - URI to WAMP router
//     * @param resolveMultiDNS - convert URI to IP (possibly several)
//     * @param realms - names of realm to publish, if none - publish all realms
//     * @throws WAMPException
//     * @throws IOException
//     */
//    public void publish(URI router, boolean resolveMultiDNS, String... realms) throws IOException {
//        if (realms == null) {
//            realms = apis.keySet().toArray(new String[apis.size()]);
//        }
//        URI[] all = (resolveMultiDNS) ? allURIs(router) : null;
//        if (all == null || all.length == 1) {
//            all = new URI[]{router};
//        }
//        for (String realm : realms) {
//            APIGroup group = apis.get(realm);
//            if (group != null) {
//                for (URI r : all) {
//                    group.connect(r);
//                }
//            }
//        }
//    }
    @Override
    public void onStarted() throws IOException {
        super.onStarted();
        if (!apis.isEmpty()) {
            URI[] uris = publishingURIs(null);
            for (Entry<String, APIGroup> entry : apis.entrySet()) {
                APIGroup group = entry.getValue();
                for (URI uri : uris) {
                    group.connect(uri);
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

    public T publishPrefixedAPI(URI wsURI, String realm, API_Publishers api, String... apiNames) throws IOException {
        if (apiNames != null) {
            return this.publishPrefixedAPI(wsURI, realm, api, Arrays.asList(apiNames));
        }
        return null;
    }

    /**
     * Placeholder for actual initializer when needed.
     *
     * @param uri
     * @param agent
     * @param realm
     * @return
     */
    public T initClient(URI uri, String agent, String realm) {
        return null;
    }

    public T publishPrefixedAPI(final URI wsURI, final String realm, API_Publishers apis, final Collection<String> apiNames) throws IOException {
        if (realm == null || apiNames == null) {
            return null;
        }

        T client = initClient(wsURI, "api_over_wamp", realm);

        // add callable APIs
        for (String apiName : apiNames) {
            if (apiName != null) {
                final API_Publisher api = apis.getAPIPublisher(apiName);
                for (final String pn : apis.getNames(apiName)) {
                    onPublishingAPI(wsURI, client, realm, apiName, api, pn);
                    onPublishedAPI(wsURI, client, apiName, api);
                }
            }
        }
        // connect and register API calls
        onPublishedAPI(wsURI, client, null, null);
        return client;
    }

    /**
     * Client-specific API publishing.
     *
     * @param wsURI
     * @param client
     * @param realm
     * @param apiName
     * @param api
     * @param pn
     */
    public void onPublishingAPI(final URI wsURI, final T client, final String realm, String apiName, API_Publisher api, String pn) {
    }

    public void onPublishedAPI(final URI wsURI, final T client, String apiName, API_Publisher api) throws IOException {
        if (client == null && getREST() != null) {
            getREST().registerProviders(new MethodsProvider[]{new API_MethodsProvider()}, api);
        }
    }
}
