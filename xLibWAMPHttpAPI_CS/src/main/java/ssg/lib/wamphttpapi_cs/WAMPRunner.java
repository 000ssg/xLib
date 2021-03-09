/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ssg.lib.wamphttpapi_cs;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import ssg.lib.api.APIAttr;
import ssg.lib.api.APICallable;
import ssg.lib.api.APIDataType;
import ssg.lib.api.APIDataType.APICollectionType;
import ssg.lib.api.APIDataType.APIObjectType;
import ssg.lib.api.APIError;
import ssg.lib.api.APIFunction;
import ssg.lib.api.APIParameter;
import ssg.lib.api.APIProcedure;
import ssg.lib.api.API_Publisher;
import ssg.lib.common.net.NetTools;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.HttpConnectionUpgrade;
import ssg.lib.http.rest.MethodsProvider;
import ssg.lib.httpapi_cs.APIRunner;
import ssg.lib.httpapi_cs.API_MethodsProvider;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPRealmFactory;
import ssg.lib.wamp.features.WAMP_FP_Reflection;
import ssg.lib.wamp.nodes.WAMPClient;
import ssg.lib.wamp.nodes.WAMPRouter;
import ssg.lib.wamp.rpc.impl.callee.CalleeCall;
import ssg.lib.wamp.rpc.impl.callee.CalleeProcedure;
import ssg.lib.wamp.stat.WAMPStatistics;
import ssg.lib.wamp.util.RB;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.util.WAMPTools;

/**
 * API runner defines WAMP/REST publishing and WAMP routing functionality to
 * enable simple mechanism of API exposure.
 *
 * @author sesidoro
 */
public class WAMPRunner extends APIRunner<WAMPClient> {

    public static final String CFG_WAMP_URI = "wampURI";
    public static final String CFG_WAMP_PATH = "wampPath";
    public static final String CFG_WAMP_PORT = "wampPort";

    // builders/holders
    Wamp wamp; // WAMP support (optional)
    URI wampRouterURI; // remote WAMP router uri (optional)
    boolean multiHost = false; // expand URI to all DNS IPs...
    Integer routerPort; // stand-alone (non-embedded) router port (optional)
    HttpConnectionUpgrade ws_wamp_connection_upgrade;

    // WAMP/REST support
    API_MethodsProvider wampOverREST;
    APIStatistics apiStat = new APIStatistics();
    Collection<String> registeredRESTAPIs = new HashSet<>();

    public WAMPRunner() {
    }

    public WAMPRunner(HttpApplication app) {
        super(app);
    }

    public WAMPRunner configure(WAMPRealmFactory realmFactory) {
        initWamp();
        wamp.configure(realmFactory);
        return this;
    }

    @Override
    public WAMPRunner configureAPI(String realm, String name, API_Publisher api) {
        return (WAMPRunner) super.configureAPI(realm, name, api);
    }

    @Override
    public WAMPRunner configureAPI(String realm, String name, API_Publisher api, URI uri) {
        return (WAMPRunner) super.configureAPI(realm, name, api, uri, null);
    }

    @Override
    public WAMPRunner configureAPI(String realm, String name, API_Publisher api, URI uri, String authid) {
        return (WAMPRunner) super.configureAPI(realm, name, api, uri, authid);
    }

    @Override
    public WAMPRunner configureHttp(Integer httpPort) {
        return (WAMPRunner) super.configureHttp(httpPort);
    }

    @Override
    public WAMPRunner configureREST(String path) throws IOException {
        return (WAMPRunner) super.configureREST(path);
    }

    @Override
    public void configUpdated(String key, Object oldValue, Object newValue) {
        super.configUpdated(key, oldValue, newValue);
        if (CFG_HTTP_PORT.equals(key)) {
            if (wampRouterURI != null && routerPort == null) try {
                wampRouterURI = new URI(wampRouterURI.toString().replace(":" + oldValue, ":" + newValue));
            } catch (URISyntaxException usex) {
            }
        }
    }

    /**
     * Returns true if created, false - if already exists.
     *
     * @return
     */
    public boolean initWamp() {
        if (wamp == null) {
            wamp = new Wamp().configureClient(true, null);
            return true;
        }
        return false;
    }

    public Wamp wamp() {
        return wamp;
    }

    public WAMPRouter getRouter() {
        return wamp != null ? wamp.getRouter() : null;
    }

    public Collection<WAMPClient> getClients() {
        return wamp.clientCS.getClients();
    }

    /**
     * WAMP client only. URI represents default WAMP router (optional).
     *
     * @param router
     * @return
     * @throws IOException
     */
    public WAMPRunner configureWAMPRouter(URI router, boolean multiHost) throws IOException {
        if (wamp == null) {
            initWamp();
            wampRouterURI = router;
            this.multiHost = multiHost;
            configUpdated(CFG_WAMP_URI, null, wampRouterURI);
        } else {
            throw new IOException("WAMP configuration is defined already.");
        }
        return this;
    }

    /**
     * Embedded WAMP router.
     *
     * @param path
     * @return
     * @throws IOException
     */
    public WAMPRunner configureWAMPRouter(String path) throws IOException {
        if (wamp == null) {
            initHttp();
            initWamp();
            wamp.configureRouter(true, null);
            // abs/rel path correction
            if (getApp() != null) {
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
            } else {
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
            }
            ws_wamp_connection_upgrade = wamp.getCSGroup().createWebSocketConnectionUpgrade(path, null);
            getService().configureConnectionUpgrade(0, ws_wamp_connection_upgrade);
            try {
                if (getApp() != null) {
                    path = getApp().getRoot() + "/" + path;
                }
                wampRouterURI = new URI("ws://localhost:" + getHttpPort() + path);
            } catch (URISyntaxException usx) {
            }
            configUpdated(CFG_WAMP_PATH, null, path);
        } else {
            throw new IOException("WAMP configuration is defined already.");
        }
        return this;
    }

    /**
     * WAMP router explicit.
     *
     * @param routerPort
     * @return
     * @throws IOException
     */
    public WAMPRunner configureWAMPRouter(int routerPort) throws IOException {
        if (wamp == null) {
            initWamp();
            wamp.configureRouter(true, null).configureClient(true, null);
            this.routerPort = routerPort;
            wamp.getCSGroup().addListenerAt(this, InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), routerPort);
            configUpdated(CFG_WAMP_PORT, null, routerPort);
        } else {
            throw new IOException("WAMP configuration is defined already.");
        }
        return this;
    }

    @Override
    public void onStarted() throws IOException {
        if (wamp != null) {
            wamp.onStarted(this);
            //wamp.TRACE_MESSAGES = true;
        }
        super.onStarted();
    }

    @Override
    public void onPublishedAPI(URI wsURI, APIGroup group, WAMPClient client, String apiName, API_Publisher api) throws IOException {
        if (client != null) {
            if (apiName == null && api == null) {
                //client.connect();
            } else if (!registeredRESTAPIs.contains(apiName)) {
                if (getREST() != null) {
                    if (wampOverREST == null) {
                        try {
                            wampOverREST = new REST_WAMP_API_MethodsProvider(wamp.connect(
                                    wsURI != null ? wsURI : wampRouterURI,
                                    "rest_api_over_wamp_provider",
                                    new WAMPFeature[]{WAMPFeature.shared_registration},
                                    group.authid,
                                    "rest_api_over_wamp",
                                    client.getRealm(),
                                    WAMP.Role.caller
                            ));

                        } catch (IOException ioex) {
                            ioex.printStackTrace();
                        }
                    }

                    try {
                        getREST().registerProviders(new MethodsProvider[]{wampOverREST}, api);
                        registeredRESTAPIs.add(apiName);
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            }
        } else {
            super.onPublishedAPI(wsURI, group, client, apiName, api);
        }
    }

    @Override
    public void onPublishingAPI(URI wsURI, APIGroup group, WAMPClient client, String apiName, API_Publisher api, String procedure) {
        boolean supportsReflection = wamp != null ? wamp.supportsFeature(WAMPFeature.procedure_reflection) : false;
        if (client != null)
                    try {
            boolean est = 0 == client.waitEstablished(2000L);
            if (procedure == null) { // API level publishing
                if (supportsReflection) {
                    // add types/errors definitions used in procs
                    RB root = RB.root();
                    for (Entry<String, APIDataType> entry : api.getAPI().types.entrySet()) {
                        APIDataType dt = entry.getValue();
                        RB type = RB.type(entry.getKey());
                        if (dt.isObjectType()) {
                            type.value("category", "object");
                            RB attrs = RB.root();
                            for (APIAttr attr : ((APIObjectType) dt).attributes().values()) {
                                attrs.value(attr.name, attr.type.fqn());
                            }
                            type.value("attributes", attrs.data());
                        } else if (dt.isCollectionType()) {
                            type.value("category", "collection");
                            APIDataType itemType = ((APICollectionType) dt).itemType();
                            if (itemType != null) {
                                type.value("itemType", itemType.fqn());
                            }
                        } else {
                            type.value("category", "scalar");
                        }
                        root.element(type);
                    }
                    for (Entry<String, APIError> entry : api.getAPI().errors.entrySet()) {
                        RB err = RB.error(entry.getKey());
                        root.element(err);
                    }

                    if (!client.publish(WAMPTools.EMPTY_DICT, WAMP_FP_Reflection.WR_RPC_DEFINE, WAMPTools.EMPTY_LIST, root.data())) {
                        // TODO: error or notification that reflection is not supported due to missing "publisher" role?
                        boolean est1 = client.isSessionEstablished();
                        boolean can = client.canPublish();
                        int a = 0;
                    }
                }
            } else { // procedure level publishing
                final APICallable dbc = api.getCallable(procedure, null);
                APIProcedure[] procs = dbc.getAPIProcedures();
                try {
                    Map opts = null;
                    if (supportsReflection) {
                        opts = RB.root()
                                .value("invoke", "roundrobin")
                                .value("reflection", RB.root()
                                        .procedure(Arrays.stream(procs).map(proc -> {
                                            RB rb = (proc instanceof APIFunction) ? RB.function(proc.fqn()).returns(((APIFunction) proc).response.type.fqn()) : RB.procedure(proc.fqn());
                                            if (proc.params != null) {
                                                for (Entry<String, APIParameter> pe : proc.params.entrySet()) {
                                                    rb.parameter(-1, pe.getKey(), pe.getValue().type.fqn(), !pe.getValue().mandatory);
                                                }
                                            }
                                            if (proc.errors != null) {
                                                for (APIError err : proc.errors) {
                                                    rb.element(RB.error(err.fqn()));
                                                }
                                            }
                                            return rb;
                                        }).collect(Collectors.toList()))
                                        .data("proc", procs[0].fqn())
                                ).data();
                    } else {
                        opts = new HashMap() {
                            {
                                put("invoke", "roundrobin");
                            }
                        };
                    }

                    client.addExecutor(
                            opts,
                            (procedure != null ? procedure : !apiName.endsWith(".") ? apiName + "." : apiName), new CalleeProcedure.Callee() {
                        @Override
                        public Future invoke(CalleeCall call, ExecutorService executor, final String name, final List args, final Map argsKw) throws WAMPException {
                            if (apiStat != null) {
                                apiStat.onTryInvoke();
                            }
                            return executor.submit(new Callable() {
                                @Override
                                public Object call() throws Exception {
                                    String old = Thread.currentThread().getName();
                                    try {
                                        Thread.currentThread().setName("exec_" + group.realm + "_" + name);
                                        if (apiStat != null) {
                                            apiStat.onInvoke();
                                        }
                                        return api.getCallable(name, null).call(argsKw);
                                    } catch (Throwable th) {
                                        if (apiStat != null) {
                                            apiStat.onError();
                                        }
                                        if (th instanceof WAMPException) {
                                            throw (WAMPException) th;
                                        }
                                        throw new WAMPException(th);
                                    } finally {
                                        if (apiStat != null) {
                                            apiStat.onDone();
                                        }
                                        Thread.currentThread().setName(old);
                                    }
                                }
                            });
                        }
                    });
                } catch (WAMPException wex) {
                    wex.printStackTrace();
                }
            }
        } catch (Throwable th) {
            System.out.println("Skipped callable for '" + procedure + "': " + th);
            th.printStackTrace();
        }
    }

    @Override
    public void onStopping() throws IOException {
        super.onStopping();
        if (wamp != null) {
            wamp.onStop(this);
        }
    }

    @Override
    public URI[] publishingURIs(URI uri) {
        if (uri == null && wampRouterURI != null) {
            uri = wampRouterURI;
        }
        URI[] all = (multiHost) ? NetTools.allURIs(uri) : super.publishingURIs(uri);
        return all;
    }

    @Override
    public WAMPClient initClient(URI uri, APIGroup group, String title) {
        try {
            return this.connect(
                    uri != null ? uri : wampRouterURI,
                    group.authid,
                    title + "_provider",
                    group.realm,
                    new WAMPFeature[]{WAMPFeature.shared_registration},
                    WAMP.Role.callee, WAMP.Role.publisher, WAMP.Role.subscriber);
        } catch (WAMPException wex) {
            wex.printStackTrace();
        }
        return null;
    }

    public URI gewtRouterURI() {
        return wampRouterURI;
    }

//    public API_MethodsProvider getWampOverRest() {
//        return wampOverREST;
//    }
    public WAMPClient connect(URI uri, String authid, String agent, String realm, WAMPFeature[] features, WAMP.Role... roles) throws WAMPException {
        if (wamp != null) {
            try {
                // prepare/keep client
                return wamp.connect(
                        uri,
                        agent,
                        features,
                        authid,
                        agent,
                        realm,
                        roles
                ).configure(new WAMPStatistics(realm + "_" + agent));
            } catch (WAMPException wex) {
                wex.printStackTrace();
            }
        }
        return null;
    }
}
