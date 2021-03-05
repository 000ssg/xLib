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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import ssg.lib.api.APICallable;
import ssg.lib.api.API_Publisher;
import ssg.lib.api.util.Reflective_API_Builder;
import ssg.lib.common.net.NetTools;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.HttpConnectionUpgrade;
import ssg.lib.http.rest.MethodsProvider;
import ssg.lib.httpapi_cs.APIRunner;
import ssg.lib.httpapi_cs.API_MethodsProvider;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.features.WAMP_FP_Reflection;
import ssg.lib.wamp.nodes.WAMPClient;
import ssg.lib.wamp.rpc.impl.callee.CalleeCall;
import ssg.lib.wamp.rpc.impl.callee.CalleeProcedure;
import ssg.lib.wamp.stat.WAMPStatistics;
import ssg.lib.wamp.util.WAMPException;

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

    @Override
    public WAMPRunner configureAPI(String realm, String name, API_Publisher api) {
        return (WAMPRunner) super.configureAPI(realm, name, api);
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

    public void initWamp() {
        if (wamp == null) {
            wamp = new Wamp().configureClient(true, null);
        }
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
            wamp.configureRouter(true, null).configureClient(true, null);
            ws_wamp_connection_upgrade = wamp.getCSGroup().createWebSocketConnectionUpgrade(path, null);
            getService().configureConnectionUpgrade(0, ws_wamp_connection_upgrade);
            try {
                if (getApp() != null) {
                    path = getApp().getRoot() + (!path.startsWith("/") && !getApp().getRoot().endsWith("/") ? "/" : "") + path;
                } else {
                    path = (!path.startsWith("/") ? "/" : "") + path;
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
    public void onPublishedAPI(URI wsURI, WAMPClient client, String apiName, API_Publisher api) throws IOException {
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
            super.onPublishedAPI(wsURI, client, apiName, api);
        }
    }

    @Override
    public void onPublishingAPI(URI wsURI, WAMPClient client, String realm, String apiName, API_Publisher api, String pn) {
        if (client != null)
                    try {
            final APICallable dbc = api.getCallable(pn, null);
            try {
                client.addExecutor(new HashMap() {
                    {
                        put("match", "prefix");
                        put("invoke", "roundrobin");
                    }
                }, (!apiName.endsWith(".") ? apiName + "." : apiName), new CalleeProcedure.Callee() {
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
                                    Thread.currentThread().setName("exec_" + realm + "_" + name);
                                    if (apiStat != null) {
                                        apiStat.onInvoke();
                                    }
                                    //return dbAPI.find(name).call(argsKw);
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
        } catch (Throwable th) {
            System.out.println("Skipped callable for '" + pn + "': " + th);
            th.printStackTrace();
        }
    }

    @Override
    public void onStopping() throws IOException {
        super.onStopping();
        if (wamp != null) {
            wamp.onStop(this);
            //for(APIGroup group:groups)
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
    public WAMPClient initClient(URI uri, String agent, String realm) {
        if (wamp != null) {
            try {
                // prepare/keep client
                return wamp.connect(
                        uri != null ? uri : wampRouterURI,
                        agent + "_provider",
                        new WAMPFeature[]{WAMPFeature.shared_registration},
                        agent,
                        realm,
                        WAMP.Role.callee
                ).configure(new WAMPStatistics("wamp-pub"));
            } catch (WAMPException wex) {
                wex.printStackTrace();
            }
        }
        return null;
    }

    public static class DemoHW {

        public String getHello(String who) {
            return "Hello, " + who + "!";
        }
    }

    public static void main(String... args) throws Exception {
        WAMP_FP_Reflection reflectionFeature = new WAMP_FP_Reflection();

        HttpApplication app = new HttpApplication("App", "/app");
        WAMPRunner r1 = new WAMPRunner(app)
                .configureWAMPRouter("wamp")
                //.configureWAMPRouter(30002)
                .configureAPI("demo", "test", new API_Publisher()
                        .configure(Reflective_API_Builder.buildAPI("test", null, DemoHW.class))
                        .configureContext(new DemoHW())
                )
                .configureHttp(30001)
                .configureREST("rest");
        if (r1.wamp != null) {
            r1.wamp.configureFeature(WAMPFeature.procedure_reflection, reflectionFeature);
        }
        r1.start();
        int a = 0;
    }
}
