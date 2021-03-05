/*
 * The MIT License
 *
 * Copyright 2020 sesidoro.
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
package ssg.lib.api.wamp_cs;

import ssg.lib.api.wamp_cs.db.DBC;
import ssg.lib.api.wamp_cs.rest.JS_API_WAMP;
import ssg.lib.api.wamp_cs.rest.REST_WAMP_API_MethodsProvider;
import ssg.lib.api.wamp_cs.rest.API_MethodsProvider;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
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
import ssg.lib.api.API_Publisher.API_Publishers;
import ssg.lib.api.wamp_cs.rest.JS_API_WAMP.WAMP_Context;
import ssg.lib.http.HttpDataProcessor;
import ssg.lib.http.HttpService;
import ssg.lib.http.rest.MethodsProvider;
import ssg.lib.http.rest.RESTHttpDataProcessor;
import ssg.lib.http.rest.ReflectiveMethodsProvider;
import ssg.lib.net.CS;
import ssg.lib.service.Repository;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPFeatureProvider;
import ssg.lib.wamp.features.WAMP_FP_Reflection;
import ssg.lib.wamp.nodes.WAMPClient;
import ssg.lib.wamp.nodes.WAMPNode.WAMPNodeListener;
import ssg.lib.wamp.rpc.impl.callee.CalleeCall;
import ssg.lib.wamp.rpc.impl.callee.CalleeProcedure.Callee;
import ssg.lib.wamp.stat.WAMPStatistics;
import ssg.lib.wamp.util.RB;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.websocket.WebSocket;

/**
 *
 * @author 000ssg
 */
public class WAMP_CS_API {

    IWS wcs;
    // published APIs info per Dealer instance...
    Map<URL, WAMPClient> publishers = Collections.synchronizedMap(new LinkedHashMap<>());
    // DB connections
    Map<String, DBC> dataSources = new LinkedHashMap<>();
    APIStatistics apiStat = new APIStatistics();
    // REST support
    API_MethodsProvider dbAPI_REST_MP;
    Collection<String> registeredRESTAPIs = new HashSet<>();
    Map<WAMPFeature, WAMPFeatureProvider> featureProviders = WAMPTools.createMap(true);

    public WAMP_CS_API() {
        wcs = new WSJavaCS();
    }

    public WAMP_CS_API(CS cs) {
        wcs = new WSJavaCS(cs);
    }

    public <Z extends WAMP_CS_API> Z configure(WAMPFeature feature, WAMPFeatureProvider featureProvider) {
        if (feature != null) {
            if (wcs instanceof WSJavaCS) {
                if (featureProvider != null) {
                    featureProviders.put(feature, featureProvider);
                } else {
                    featureProviders.remove(feature);
                }
                ((WSJavaCS) wcs).configure(feature, featureProvider);
            }
        }
        return (Z) this;
    }

    public WAMP_CS_API router(int... ports) {
        if (wcs instanceof WSJavaCS) {
            ((WSJavaCS) wcs).router(ports);
        }
        return this;
    }

    public WAMP_CS_API noRouter() {
        if (wcs instanceof WSJavaCS) {
            ((WSJavaCS) wcs).noRouter();
        }
        return this;
    }

    public WAMP_CS_API rest(int... ports) {
        if (wcs instanceof WSJavaCS) {
            ((WSJavaCS) wcs).rest(ports);
        }
        return this;
    }

    public WAMP_CS_API addDataSource(String name, DBC ds) {
        dataSources.put(name, ds);
        return this;
    }

    public WAMP_CS_API addRouterListener(WAMPNodeListener... ls) {
        if (wcs instanceof WSJavaCS) {
            ((WSJavaCS) wcs).addRouterListener(ls);
        }
        return this;
    }

    public WAMP_CS_API removeRouterListener(WAMPNodeListener... ls) {
        if (wcs instanceof WSJavaCS) {
            ((WSJavaCS) wcs).removeRouterListener(ls);
        }
        return this;
    }

    public WAMP_CS_API setRouterFrameMonitor(WebSocket.FrameMonitor fm) {
        if (wcs instanceof WSJavaCS) {
            ((WSJavaCS) wcs).setRouterFrameMonitor(fm);
        }
        return this;
    }

    public void start() throws IOException {
        wcs.start();
    }

    public void stop() throws IOException {
        try {
            wcs.stop();
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    public WAMPClient stopAPI(URL url) {
        try {
            if (url != null) {
                WAMPClient client = publishers.remove(url);
                if (client != null && client.isConnected()) {
                    client.disconnect("maintenance");
                }
                return client;
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        for (Entry<String, DBC> ds : dataSources.entrySet()) {
            try {
                DBC odb = ds.getValue();
                odb.close();
                System.out.println("ODB closed: " + odb.getStat().replace("\n", "\n  "));
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }

        return null;
    }

    public WAMPClient wsConnect(URI wsURI, WAMPFeature[] features, String agent, String realmS, Role... roles) throws IOException {
        return wcs.connect(
                wsURI,
                WAMP.WS_SUB_PROTOCOL_JSON,
                features,
                agent,
                realmS,
                roles
        );
    }

    public WAMPClient publishAPI(URI wsURI, String realm, API_Publishers api, String... apiNames) throws WAMPException, IOException {
        if (apiNames != null) {
            return this.publishAPI(wsURI, realm, api, Arrays.asList(apiNames));
        }
        return null;
    }

    public WAMPClient publishAPI(final URI wsURI, final String realm, API_Publishers api, final Collection<String> apiNames) throws WAMPException, IOException {
        if (wsURI == null || realm == null || apiNames == null) {
            return null;
        }
        boolean supportsReflection = featureProviders.containsKey(WAMPFeature.procedure_reflection);
        // prepare/keep client
        WAMPClient client = wsConnect(
                wsURI,
                new WAMPFeature[]{WAMPFeature.shared_registration},
                "api_over_wamp_provider",
                realm,
                Role.callee,
                supportsReflection ? Role.publisher : Role.callee
        );
        long et = client.waitEstablished(2000L);
        if (et < 0) {
            System.err.println("FYI: Too long session establishing [" + et + "]... " + client);
        }

        // add callable APIs
        for (String apiName : apiNames) {
            if (apiName != null) {
                API_Publisher dbAPI = api.getAPIPublisher(apiName);
                Collection<String> names = (api != null) ? api.getNames(apiName) : null;
                if (names != null) {
                    if (supportsReflection) {
                        // add types/errors definitions used in procs
                        RB root = RB.root();
                        for (Entry<String, APIDataType> entry : dbAPI.getAPI().types.entrySet()) {
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
                        for (Entry<String, APIError> entry : dbAPI.getAPI().errors.entrySet()) {
                            RB err = RB.error(entry.getKey());
                            root.element(err);
                        }

                        client.publish(WAMPTools.EMPTY_DICT, WAMP_FP_Reflection.WR_RPC_DEFINE, WAMPTools.EMPTY_LIST, root.data());
                    }
                    for (final String pn : names) {
                        final APICallable dbc = dbAPI.getCallable(pn, null);
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
                            client.addExecutor(opts, pn, new Callee() {
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
                                                return dbc.call(argsKw);
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
                }
                onPublishedAPI(wsURI, client, apiName, dbAPI);
            }
        }

        // connect and register API calls
        onPublishedAPI(wsURI, client, null, null);
        return client;
    }

    public WAMPClient publishPrefixedAPI(URI wsURI, String realm, API_Publishers api, String... apiNames) throws WAMPException, IOException {
        if (apiNames != null) {
            return this.publishPrefixedAPI(wsURI, realm, api, Arrays.asList(apiNames));
        }
        return null;
    }

    public WAMPClient publishPrefixedAPI(final URI wsURI, final String realm, API_Publishers api, final Collection<String> apiNames) throws WAMPException, IOException {
        if (wsURI == null || realm == null || apiNames == null) {
            return null;
        }
        // prepare/keep client
        WAMPClient client = wsConnect(
                wsURI,
                new WAMPFeature[]{WAMPFeature.shared_registration},
                "api_over_wamp_provider",
                realm,
                Role.callee
        ).configure(new WAMPStatistics("ws-pub"));

        // add callable APIs
        for (String apiName : apiNames) {
            if (apiName != null) {
                final API_Publisher dbAPI = api.getAPIPublisher(apiName);
                for (final String pn : api.getNames(apiName)) {
                    try {
                        final APICallable dbc = dbAPI.getCallable(pn, null);
                        try {
                            client.addExecutor(new HashMap() {
                                {
                                    put("match", "prefix");
                                    put("invoke", "roundrobin");
                                }
                            }, (!apiName.endsWith(".") ? apiName + "." : apiName), new Callee() {
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
                                                return dbAPI.getCallable(name, null).call(argsKw);
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
                    //break;
                }
                onPublishedAPI(wsURI, client, apiName, dbAPI);
            }
        }

        // connect and register API calls
        onPublishedAPI(wsURI, client, null, null);
        return client;
    }

    public void onPublishedAPI(final URI wsURI, final WAMPClient client, String apiName, API_Publisher dbAPI) throws WAMPException {
        if (client != null) {
            if (apiName == null && dbAPI == null) {
                //client.connect();
            } else if (!registeredRESTAPIs.contains(apiName)) {
                if (wcs instanceof WSJavaCS) {
                    WSJavaCS wsj = (WSJavaCS) wcs;
                    HttpService http = wsj.getHttpService();
                    if (http != null) {
                        Repository<HttpDataProcessor> rep = http.getDataProcessors(null, null);
                        if (rep != null) {
                            RESTHttpDataProcessor rest = null;
                            for (HttpDataProcessor hdp : rep.items()) {
                                if (hdp instanceof RESTHttpDataProcessor) {
                                    rest = (RESTHttpDataProcessor) hdp;
                                    break;
                                }
                            }
                            if (rest == null) {
                                rest = new RESTHttpDataProcessor("/rest");
                                rep.addItem(rest, 0);
                                rest.getRESTHelper().addAPITypes(new JS_API_WAMP(new WAMP_Context() {
                                    @Override
                                    public String getURI() {
                                        return wsURI.toString() + "/ws";
                                    }

                                    @Override
                                    public String getRealm() {
                                        return client.getRealm();
                                    }
                                }));
                                if (wsj.countersREST != null) {
                                    rest.registerProviders(new MethodsProvider[]{new ReflectiveMethodsProvider()}, wsj.countersREST);
                                }
                            }
                            if (dbAPI_REST_MP == null) {
                                try {
                                    dbAPI_REST_MP = new REST_WAMP_API_MethodsProvider(wsConnect(
                                            wsURI,
                                            new WAMPFeature[]{WAMPFeature.shared_registration},
                                            "rest_api_over_wamp_provider",
                                            client.getRealm(),
                                            Role.caller
                                    ));

                                } catch (IOException ioex) {
                                    ioex.printStackTrace();
                                }
                            }

                            try {
                                rest.registerProviders(new MethodsProvider[]{dbAPI_REST_MP}, dbAPI);
                                registeredRESTAPIs.add(apiName);
                            } catch (Throwable th) {
                                th.printStackTrace();
                            }

                        }
                    }
                }
            }
        }
    }

    public HttpService getHttpService() {
        if (wcs instanceof WSJavaCS) {
            return ((WSJavaCS) wcs).getHttpService();
        }
        return null;
    }

    public List<URI> getRouterURIs() {
        if (wcs instanceof WSJavaCS) {
            try {
                return Arrays.asList(((WSJavaCS) wcs).getRouterURIs());
            } catch (Throwable th) {
            }
        }
        return null;
    }

    public List<URI> getWebSocketURIs() {
        if (wcs instanceof WSJavaCS) {
            try {
                return Arrays.asList(((WSJavaCS) wcs).wsGroup.getListeningURIs(true));
            } catch (Throwable th) {
            }
        }
        return null;
    }

}
