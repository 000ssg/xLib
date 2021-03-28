/*
 * The MIT License
 *
 * Copyright 2021 sesidoro.
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
package ssg.lib.wamphttpapi_cs;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import ssg.lib.api.APIAccess;
import ssg.lib.api.APIAttr;
import ssg.lib.api.APIAuthContext;
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
import ssg.lib.http.HttpAuthenticator;
import ssg.lib.http.HttpConnectionUpgrade;
import ssg.lib.http.rest.MethodsProvider;
import ssg.lib.http.rest.StubVirtualData;
import ssg.lib.httpapi_cs.APIRunner;
import ssg.lib.httpapi_cs.APIStatistics;
import ssg.lib.httpapi_cs.API_MethodsProvider;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPRealm;
import ssg.lib.wamp.WAMPRealmFactory;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.WAMPTransport.WAMPTransportMessageListener;
import ssg.lib.wamp.auth.WAMPAuth;
import ssg.lib.wamp.features.WAMP_FP_Reflection;
import ssg.lib.wamp.flows.WAMPMessagesFlow;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMPMessageType;
import ssg.lib.wamp.nodes.WAMPClient;
import ssg.lib.wamp.nodes.WAMPNode;
import ssg.lib.wamp.nodes.WAMPNode.WAMPNodeListener;
import ssg.lib.wamp.nodes.WAMPRouter;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.ERROR_RPC_NOT_AUTHENTICATED;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.ERROR_RPC_NOT_AUTHORIZED;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_CALLER_ID_DISCLOSE_CALLER;
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
 * @author 000ssg
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
    // "saved" authentication headers for realm-authid -> [header,value]
    Map<String, Map<String, String[]>> wampTransportHeaders = new LinkedHashMap<>();
    // "saved" WAMP auth for realm-authid -> WAMPAuth
    Map<String, Map<String, WAMPAuth>> wampTransportAuths = new LinkedHashMap<>();

    // WAMP/REST support
    REST_WAMP_MethodsProvider wampAsREST;
    // WAMP/REST/API support
    API_MethodsProvider wampOverREST;
    Collection<String> registeredRESTAPIs = new HashSet<>();

    public WAMPRunner() {
    }

    public WAMPRunner(HttpApplication app) {
        super(app);
    }

    public WAMPRunner(HttpAuthenticator auth, HttpApplication app) {
        super(auth, app);
    }

    public WAMPRunner(HttpApplication app, APIStatistics stat) {
        super(app, stat);
    }

    public WAMPRunner(HttpAuthenticator auth, HttpApplication app, APIStatistics stat) {
        super(auth, app, stat);
    }

    public WAMPRunner configure(WAMPRealmFactory realmFactory) {
        initWamp();
        wamp.configure(realmFactory);
        return this;
    }

    public WAMPRunner configure(WAMPTransportMessageListener l) {
        initWamp();
        wamp.configure(l);
        return this;
    }

    public WAMPRunner configureHttpAuth(String realm, String authid, String header, String value) {
        if (realm != null && header != null && value != null) {
            Map<String, String[]> m = wampTransportHeaders.get(realm);
            if (m == null) {
                m = new LinkedHashMap<>();
                wampTransportHeaders.put(realm, m);
            }
            m.put(authid != null ? authid : "", new String[]{header, value});
        }
        return this;
    }

    public WAMPRunner configureTransportWAMPAuth(String realm, String authid, WAMPAuth auth) {
        if (realm != null && auth != null) {
            Map<String, WAMPAuth> m = wampTransportAuths.get(realm);
            if (m == null) {
                m = new LinkedHashMap<>();
                wampTransportAuths.put(realm, m);
            }
            m.put(authid != null ? authid : "", auth);
        }
        return this;
    }

    public WAMPRunner configureBridgeWAMPAuth(String realm, WAMPAuth auth) {
        return this.configureTransportWAMPAuth(realm, API_PUB_CLIENT_TITLE + "_provider", auth);
    }

    @Override
    public WAMPRunner configureAPIStatistics(APIStatistics stat) {
        super.configureAPIStatistics(stat);
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
    public WAMPRunner configureAPI(String realm, String name, API_Publisher api, URI uri, String authid, Long options) {
        return (WAMPRunner) super.configureAPI(realm, name, api, uri, authid, options);
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
    public WAMPRunner configureStub(StubVirtualData<?> stub) {
        super.configureStub(stub);
        return this;
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
        if (CFG_REST_PATH.equals(key) || CFG_WAMP_PORT.equals(key) || CFG_WAMP_PATH.equals(key)) {
            if (wampAsREST == null && wamp != null && getREST() != null) {
                wampAsREST = new REST_WAMP_MethodsProvider(
                        getAPIStatistics(null) != null ? getAPIStatistics(null).createChild(null, "wamp-rest") : null
                ) {
                    @Override
                    public WAMPClient caller(String realm) {
                        WAMPClient r = super.caller(realm);
                        if (r == null) try {
                            r = connect(WAMPRunner.this.getRouterURI(), "authid", "wamp-rest-agent", realm, new WAMPFeature[]{
                                WAMPFeature.caller_identification
                            }, WAMP.Role.caller);
                            if (r != null) {
                                r.waitEstablished(1000L);
                                addCallers(r);
                            }
                        } catch (WAMPException wex) {
                            wex.printStackTrace();
                        }
                        return r;
                    }
                };
                // add listener for API reflection events to modify published REST-WAMP methods
                wamp().getRouter().addWAMPNodeListener(new WAMPNodeListener() {
                    @Override
                    public void onCreatedRealm(WAMPNode node, WAMPRealm realm) {
                    }

                    @Override
                    public void onEstablishedSession(WAMPSession session) {
                    }

                    @Override
                    public void onClosedSession(WAMPSession session) {
                    }

                    @Override
                    public void onHandled(WAMPSession session, WAMPMessage msg, WAMPMessagesFlow mf) {
                        if (msg.getType().getId() == WAMPMessageType.T_PUBLISH) {
                            if (WAMP_FP_Reflection.WR_RPC_DEFINE.equals(msg.getUri(2))
                                    || WAMP_FP_Reflection.WR_EVENT_ON_UNDEFINE.equals(msg.getUri(2))) {
                                update_REST_WAMP(session.getRealm());
                            }
                        } else if (msg.getType().getId() == WAMPMessageType.T_REGISTER) {
                            if (msg.getDict(1).containsKey("reflection")) {
                                update_REST_WAMP(session.getRealm());
                            }
                        }
                    }

                    @Override
                    public void onFailed(WAMPSession session, WAMPMessage msg, WAMPMessagesFlow mf) {
                    }

                    @Override
                    public void onFatal(WAMPSession session, WAMPMessage msg) {
                    }

                    @Override
                    public void onSent(WAMPSession session, WAMPMessage msg, Throwable error) {
                    }
                });
            }
        }
    }

    @Override
    public void onAfterPublishAPI(URI wsURI, APIGroup group, Collection<String> names) {
        try {
            super.onAfterPublishAPI(wsURI, group, names);
        } finally {
            lockUrQueue.getAndDecrement();
            update_REST_WAMP(null);
        }
    }

    @Override
    public void onBeforePublishAPI(URI wsURI, APIGroup group, Collection<String> names) {
        try {
            super.onBeforePublishAPI(wsURI, group, names);
        } finally {
            lockUrQueue.getAndIncrement();
        }
    }

    transient private Collection<WAMPRealm> urQueue = new HashSet<>();
    transient private AtomicInteger lockUrQueue = new AtomicInteger();

    public void update_REST_WAMP(WAMPRealm realm) {
        if (realm != null) {
            if (lockUrQueue.get() > 0) {
                synchronized (urQueue) {
                    urQueue.add(realm);
                }
            } else {
                System.out.println("Registering REST_WAMP(" + realm.getName() + ")");
                getREST().registerProviders(new MethodsProvider[]{wampAsREST}, realm);
            }
        } else {
            if (lockUrQueue.get() == 0) {
                lockUrQueue.getAndIncrement();
                try {
                    WAMPRealm[] rs = null;
                    synchronized (urQueue) {
                        rs = urQueue.toArray(new WAMPRealm[urQueue.size()]);
                        urQueue.clear();
                    }
                    System.out.println("Registering REST_WAMP(" + rs.length + " realms)");
                    for (WAMPRealm r : rs) {
                        System.out.println("Registering REST_WAMP(" + r.getName() + ")");
                        getREST().registerProviders(new MethodsProvider[]{wampAsREST}, r);
                    }
                } finally {
                    lockUrQueue.getAndDecrement();
                    if (!urQueue.isEmpty()) {
                        update_REST_WAMP(null);
                    }
                }
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
     * @param router websocket URL
     * @param multiHost if true, establishes connections to all IPs for the URI.
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
     * @param needHttpAuth if true, requires authenticated HTTP connections only
     * @return
     * @throws IOException 
     */
    public WAMPRunner configureWAMPRouter(String path, boolean needHttpAuth) throws IOException {
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
            ws_wamp_connection_upgrade = wamp.getCSGroup().createWebSocketConnectionUpgrade(path, null, needHttpAuth);
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
            String apiKey = null;
            if (apiName == null && api == null) {
                if (!client.publish(WAMPTools.EMPTY_DICT, WAMP_FP_Reflection.WR_RPC_DEFINE, WAMPTools.EMPTY_LIST, WAMPTools.EMPTY_DICT)) {
                    // TODO: error or notification that reflection is not supported due to missing "publisher" role?
                    boolean est1 = client.isSessionEstablished();
                    boolean can = client.canPublish();
                    int a = 0;
                }
            } else {
                apiKey = group.realm + "/" + apiName;
            }
            if (!registeredRESTAPIs.contains(apiKey)) {
                if (getREST() != null && this.wampAsREST == null) {
                    if (wampOverREST == null) {
                        try {
                            wampOverREST = new REST_WAMP_API_MethodsProvider(getAPIStatistics(null),
                                    connect(
                                            wsURI != null ? wsURI : wampRouterURI,
                                            group.authid,
                                            "RoW-" + client.getRealm() + "-" + client.getAgent(),
                                            client.getRealm(),
                                            new WAMPFeature[]{WAMPFeature.shared_registration, WAMPFeature.caller_identification},
                                            WAMP.Role.caller)
                            );
                        } catch (IOException ioex) {
                            ioex.printStackTrace();
                        }
                    } else {
                        if (wampOverREST instanceof REST_WAMP_API_MethodsProvider) {
                            WAMPClient rclient = ((REST_WAMP_API_MethodsProvider) wampOverREST).caller(group.realm);
                            if (rclient == null) {
                                ((REST_WAMP_API_MethodsProvider) wampOverREST).addCallers(
                                        connect(
                                                wsURI != null ? wsURI : wampRouterURI,
                                                group.authid,
                                                "RoW-" + client.getRealm() + "-" + client.getAgent(),
                                                client.getRealm(),
                                                new WAMPFeature[]{WAMPFeature.shared_registration, WAMPFeature.caller_identification},
                                                WAMP.Role.caller)
                                );
                            }
                        }
                    }

                    try {
                        if (wampOverREST instanceof REST_WAMP_API_MethodsProvider) {
                            ((REST_WAMP_API_MethodsProvider) wampOverREST).setRealmTL(group.realm);
                        }
                        getREST().registerProviders(new MethodsProvider[]{wampOverREST}, api);
                        registeredRESTAPIs.add(apiKey);
                    } catch (Throwable th) {
                        th.printStackTrace();
                    } finally {
                        if (wampOverREST instanceof REST_WAMP_API_MethodsProvider) {
                            ((REST_WAMP_API_MethodsProvider) wampOverREST).setRealmTL(null);
                        }
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
        boolean prefixedAPI = (group.options & APIGroup.O_COMPACT) != 0;
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
                    // add all procedures meta for prefixed api here, since no per-procedure registrations
                    if (prefixedAPI) {
                        for (String iprocedure : group.apis.getNames(apiName)) {
                            final APICallable dbc = api.getCallable(iprocedure, null);
                            APIProcedure[] procs = dbc.getAPIProcedures();

                            List<RB> rps = Arrays.stream(procs).map(proc -> {
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
                            }).collect(Collectors.toList());
                            root.procedure(rps);
                        }
                    }

                    if (!client.publish(WAMPTools.EMPTY_DICT, WAMP_FP_Reflection.WR_RPC_DEFINE, WAMPTools.EMPTY_LIST, root.data())) {
                        // TODO: error or notification that reflection is not supported due to missing "publisher" role?
                        boolean est1 = client.isSessionEstablished();
                        boolean can = client.canPublish();
                        int a = 0;
                    }

                    // publish API as single prefixed procedure. In this case procedure name is passed as parameter and marshalled on callee.
                    if (prefixedAPI) {
                        addWAMPExecutor(client, group, api, apiName, null, WAMPTools.createDict("invoke", "roundrobin", "match", "prefix"));
                    }
                }
            } else { // procedure level publishing
                if (!prefixedAPI) {
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
                            for (APIProcedure proc : procs) {
                                if (proc.access != null) {
                                    opts.put(RPC_CALLER_ID_DISCLOSE_CALLER, true);
                                }
                            }
                        } else {
                            opts = new HashMap() {
                                {
                                    put("invoke", "roundrobin");
                                }
                            };
                        }

                        addWAMPExecutor(client, group, api, apiName, procedure, opts);
                    } catch (WAMPException wex) {
                        wex.printStackTrace();
                    }
                }
            }
        } catch (Throwable th) {
            System.out.println("Skipped callable for '" + procedure + "': " + th);
            th.printStackTrace();
        }
    }

    public void addWAMPExecutor(WAMPClient client, final APIGroup group, API_Publisher api, String apiName, String procedure, Map<String, Object> opts) throws WAMPException {
        client.addExecutor(
                opts,
                (procedure != null ? procedure : !apiName.endsWith(".") ? apiName + "." : apiName), new CalleeProcedure.Callee() {
            APIStatistics apiStat = getAPIStatistics(null) != null ? getAPIStatistics(group).createChild(null, "REST:" + (procedure != null ? procedure : apiName)) : null;

            @Override
            public Future invoke(CalleeCall call, ExecutorService executor, final WAMPAuth auth, final String name, final List args, final Map argsKw) throws WAMPException {
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
                            if (auth != null) {
                                APIProcedure proc = api.getCallable(name, null).getAPIProcedure(argsKw);
                                if (proc.access != null) {
                                    if (!proc.access.hasAccess(APIAccess.A_EXECUTE, true, auth.getRole())) {
                                        throw new WAMPException("Access denied (not authorized): " + proc, ERROR_RPC_NOT_AUTHORIZED);
                                    }
                                }
                            } else {
                                APIProcedure proc = api.getCallable(name, null).getAPIProcedure(argsKw);
                                if (proc.access != null) {
                                    throw new WAMPException("Access denied (not authenticated): " + proc, ERROR_RPC_NOT_AUTHENTICATED);
                                }
                            }
                            return api.getCallable(name, null).call(new WAMPAPIAuth(auth), argsKw);
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
            WAMPClient client = this.connect(
                    uri != null ? uri : wampRouterURI,
                    group.authid,
                    title + "_provider",
                    group.realm,
                    new WAMPFeature[]{WAMPFeature.shared_registration}, //, WAMPFeature.caller_identification},
                    WAMP.Role.callee, WAMP.Role.publisher, WAMP.Role.subscriber);
            return client;
        } catch (WAMPException wex) {
            wex.printStackTrace();
        }
        return null;
    }

    public URI getRouterURI() {
        return wampRouterURI;
    }

    /**
     * "Smart" connector.
     *
     * If URI matches local router URI, then establishes connection over "loop"
     * transport with optionally defined WAMPAuth transport authentication (via
     * getWAMPAuth(...)).
     *
     * Otherwise establishes networked connection with optionally provided HTTP
     * authentication/authorization headers (via getHttpAuthHeaders(...)).
     *
     * @param uri
     * @param authid
     * @param agent
     * @param realm
     * @param features
     * @param roles
     * @return
     * @throws WAMPException
     */
    public WAMPClient connect(URI uri, String authid, String agent, String realm, WAMPFeature[] features, WAMP.Role... roles) throws WAMPException {
        if (wamp != null) {
            try {
                if (wampRouterURI != null && wampRouterURI.equals(uri) && wamp.getRouter() != null) {
                    return wamp.connect(
                            wamp.getRouter(),
                            getWAMPAuth(uri, authid, agent, realm, features, roles),
                            agent,
                            features,
                            authid,
                            agent,
                            realm,
                            roles
                    ).configure(new WAMPStatistics(realm + "_" + agent));
                } else {
                    // prepare/keep client
                    return wamp.connect(
                            uri,
                            agent,
                            getHttpAuthHeaders(
                                    uri,
                                    authid,
                                    agent,
                                    realm,
                                    features,
                                    roles),
                            features,
                            authid,
                            agent,
                            realm,
                            roles
                    ).configure(new WAMPStatistics(realm + "_" + agent));
                }
            } catch (WAMPException wex) {
                wex.printStackTrace();
            }
        }
        return null;
    }

    public Map<String, String> getHttpAuthHeaders(URI uri, String authid, String agent, String realm, WAMPFeature[] features, WAMP.Role... roles) {
        Map<String, String[]> auths = this.wampTransportHeaders.get(realm != null ? realm : "");
        String[] ss = auths != null ? auths.get(authid != null ? authid : agent != null ? agent : null) : null;
        if (ss == null && authid != null && authid.contains(":")) try {
            // basic http auth
            ss = new String[]{"Authorization", "Basic " + Base64.getEncoder().encodeToString(authid.getBytes("UTF-8"))};
        } catch (IOException ioex) {
        }
        if (ss != null && ss.length > 0) {
            Map<String, String> r = new HashMap();
            r.put(ss[0], ss[1]);
            return r;
        }

        return null;
    }

    public WAMPAuth getWAMPAuth(URI uri, String authid, String agent, String realm, WAMPFeature[] features, WAMP.Role... roles) {
        Map<String, WAMPAuth> auths = this.wampTransportAuths.get(realm != null ? realm : "");
        return auths != null ? auths.get(authid != null ? authid : agent != null ? agent : null) : null;
    }

    public static class WAMPAPIAuth implements APIAuthContext {

        WAMPAuth auth;

        public WAMPAPIAuth(WAMPAuth auth) {
            this.auth = auth;
        }

        @Override
        public List<String> chain() {
            return new ArrayList() {
                {
                    add("API");
                    add("WAMP");
                    add("http");
                }
            };
        }

        @Override
        public String id() {
            return auth != null ? auth.getAuthid() : null;
        }

        @Override
        public String name() {
            return auth != null ? auth.getAuthid() : null;
        }

        @Override
        public String domain() {
            return auth != null ? auth.getMethod() : null;
        }

        @Override
        public String transport() {
            return "WAMP";
        }

        @Override
        public List<String> roles() {
            return auth != null && auth.getRole() != null ? Collections.singletonList(auth.getRole()) : Collections.emptyList();
        }
    }

}
