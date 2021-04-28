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
package ssg.lib.wamphttpapi_cs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.api.APIAuthContext;
import ssg.lib.http.HttpUser;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.http.rest.MethodsProvider;
import ssg.lib.http.rest.RESTMethod;
import ssg.lib.http.rest.RESTParameter;
import ssg.lib.http.rest.RESTProvider;
import ssg.lib.httpapi_cs.APIStatistics;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPRealm;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ID;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_METHOD;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_PROVIDER;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ROLE;
import ssg.lib.wamp.features.WAMP_FP_Reflection;
import ssg.lib.wamp.features.WAMP_FP_VirtualSession;
import ssg.lib.wamp.nodes.WAMPClient;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_CALLER_ID_DISCLOSE_ME;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_CALLER_ID_KEY;
import ssg.lib.wamp.rpc.impl.WAMPRPCListener;
import ssg.lib.wamp.rpc.impl.dealer.WAMPRPCDealer;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author 000ssg
 */
public class REST_WAMP_MethodsProvider implements MethodsProvider {

    public static boolean DEBUG = false;
    public static final String REQUEST_PARAM_NAME = "$$$"; // used to pass HttpRequest info to enable use of HTTP-based authentication -> virtual WAMP sessions
    // per-realm (WAMPClient, registered mapped REST-WAMP API calls, virtual sessions (mapped HTTP-uathenticated user -> virtual session)
    Map<String, WCI> callers = new LinkedHashMap<>();
    // root statistics for per-realm clients
    APIStatistics baseStat;

    public REST_WAMP_MethodsProvider(APIStatistics stat, WAMPClient... callers) {
        this.baseStat = stat;
        addCallers(callers);
    }

    public REST_WAMP_MethodsProvider(APIStatistics stat, Collection<WAMPClient> callers) {
        this.baseStat = stat;
        if (callers != null) {
            addCallers(callers.toArray(new WAMPClient[callers.size()]));
        }
    }

    public void addCallers(WAMPClient... callers) {
        if (callers != null) {
            for (WAMPClient caller : callers) {
                WCI wci = this.callers.get(caller.getRealm());
                if (wci == null) {
                    this.callers.put(caller.getRealm(), new WCI(caller));
                } else {
                    if (wci.client == null || !wci.client.isConnected()) {
                        if (wci.client != caller) {
                            wci.client = caller;
                        }
                    }
                }
            }
        }
    }

    public WAMPClient caller(String realm) {
        WCI wci = callers.get(realm);
        return wci != null ? wci.client : null;
    }

    public Map<String, VSI> virtualSessions(String realm) {
        WCI wci = callers.get(realm);
        return wci != null ? wci.virtualSessions : null;
    }

    @Override
    public boolean isOperable() {
        return true;
    }

    @Override
    public boolean canHandleClass(Class clazz) {
        return WAMPRealm.class.isAssignableFrom(clazz);
    }

    @Override
    public Map<String, List<RESTMethod>> findMethods(Object obj) {
        Map<String, List<RESTMethod>> r = new LinkedHashMap<>();
        WAMP_FP_Reflection.RR rr = obj instanceof WAMPRealm ? rr((WAMPRealm) obj) : null;
        if (rr != null) {
            RESTProvider pr = new RESTProvider();
            {
                String s = ((WAMPRealm) obj).getName();
                pr.setName(s);
                pr.setPaths(s);
                pr.setProperty("realm", s);
            }
            WCI wci = callers.get(pr.getName());
            if (wci == null) {
                wci = new WCI(null);
                callers.put(pr.getName(), wci);
            }
            synchronized (rr) {
                for (String operationName : rr.getNames("proc")) {
                    List<RESTMethod> rms = new ArrayList<>();
                    synchronized (wci.registered) {
                        try {
                            for (Map<String, Object> map : rr.getMaps("proc", operationName)) {
                                String keyPrint = methodKeyprint(operationName, map);
                                if (wci.registered.contains(keyPrint)) {
                                    continue;
                                }
                                RESTMethod mth = new RESTMethod() {
                                    RESTMethod self = this;
                                    boolean inAsynch = false; // avoid short circuit...
                                    APIStatistics apiStat = baseStat != null ? baseStat.createChild(baseStat, "REST:" + operationName) : null;

                                    {
                                        if (apiStat != null) {
                                            setProperty("apiStat", apiStat);
                                        }
                                    }

                                    /**
                                     * Pass async invocation via method provider
                                     * to enable alternative solution.
                                     */
                                    @Override
                                    public Runnable invokeAsync(HttpUser user, Object service, Map<String, Object> parameters, RESTMethod.RESTMethodAsyncCallback callback) throws IOException {
                                        String realm = self.getProvider().getProperty("realm");
                                        WAMPClient caller = caller(realm);
                                        if (caller != null && caller.isConnected()) {
                                            if (DEBUG) {
                                                System.out.println("" + getClass().getName() + ".invokeAsync: REST over WAMP[" + caller.getAgent() + "]: " + operationName + "(" + parameters + ")");
                                            }

                                            APIStatistics apiStat = self.getProperty("apiStat");
                                            if (apiStat != null) {
                                                apiStat.onTryInvoke();
                                                apiStat.onInvoke();
                                            }

                                            // prepare the call
                                            final long started = System.nanoTime();
                                            Map<String, Object> options = WAMPTools.createDict(RPC_CALLER_ID_DISCLOSE_ME, true);
                                            WAMPRPCListener.WAMPRPCListenerBase call = new WAMPRPCListener.WAMPRPCListenerBase(options, operationName, Collections.emptyList(), parameters) {
                                                @Override
                                                public void onCancel(long callId, String reason) {
                                                    if (apiStat != null) {
                                                        apiStat.onDone();
                                                    }
                                                    if (DEBUG) {
                                                        System.out.println("" + getClass().getName() + ".invokeAsync: REST over WAMP[" + caller.getAgent() + "] - CANCEL [" + (System.nanoTime() - started) / 1000000f + "ms]: " + operationName + "(" + parameters + ")");
                                                    }
                                                    callback.onResult(self, service, parameters, null, System.nanoTime() - started, null, reason);
                                                }

                                                @Override
                                                public boolean onResult(long callId, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                                                    if (apiStat != null) {
                                                        apiStat.onDone();
                                                    }
                                                    if (DEBUG) {
                                                        System.out.println("" + getClass().getName() + ".invokeAsync: REST over WAMP[" + caller.getAgent() + "] - RESULT [" + (System.nanoTime() - started) / 1000000f + "ms]: " + operationName + "(" + parameters + ")");
                                                    }
                                                    callback.onResult(self, service, parameters, (argsKw != null) ? argsKw : args, System.nanoTime() - started, null, null);
                                                    return true;
                                                }

                                                @Override
                                                public void onError(long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                                                    if (apiStat != null) {
                                                        apiStat.onError();
                                                    }
                                                    if (DEBUG) {
                                                        System.out.println("" + getClass().getName() + ".invokeAsync: WAMP[" + caller.getAgent() + "] - ERROR  [" + (System.nanoTime() - started) / 1000000f + "ms]: " + operationName + "(" + parameters + ")");
                                                    }
                                                    callback.onResult(self, service, parameters, (argsKw != null) ? argsKw : args, System.nanoTime() - started, null, error);
                                                }
                                            };

                                            if (user != null) {
                                                if (user != null) {
                                                    // virtual session id (if any)
                                                    Long id = null;

                                                    // registered/to be registered ids
                                                    Map<String, VSI> vss = virtualSessions(realm);
                                                    synchronized (vss) {
                                                        String un = user.getId();
                                                        String ur = "guest";
                                                        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
                                                            ur = user.getRoles().get(0);
                                                        }
                                                        String uk = un + ":" + ur;
                                                        VSI vs = vss.get(uk);
                                                        if (vs == null) {
                                                            // initialize virtual session evaluation, replace call with it, on success add for execution original call.
                                                            vs = new VSI();
                                                            vss.put(uk, vs);
                                                            vs.waitingCalls.add(call);
                                                            final VSI vsi = vs;
                                                            final String authId = un;
                                                            final String authRole = ur;
                                                            final String authMethod = (String) user.getProperties().get("authMethod");
                                                            call = new WAMPRPCListener.WAMPRPCListenerBase(options, WAMP_FP_VirtualSession.VS_REGISTER, Collections.emptyList(), WAMPTools.createDict(map -> {
                                                                map.put(K_AUTH_ID, authId);
                                                                map.put(K_AUTH_ROLE, authRole);
                                                                map.put(K_AUTH_METHOD, "any");
                                                                map.put(K_AUTH_PROVIDER, authMethod);
                                                                map.put("transport", "http");
                                                                map.put("roles", user.getRoles());
                                                            })) {
                                                                @Override
                                                                public void onCancel(long callId, String reason) {
                                                                    vsi.id = 0;
                                                                    if (DEBUG) {
                                                                        System.out.println("" + getClass().getName() + ".invokeAsync: REST over WAMP[" + caller.getAgent() + "] - CANCEL REGISTER VIRTUAL SESSION [" + (System.nanoTime() - started) / 1000000f + "ms]: " + operationName + "(" + parameters + ")");
                                                                    }
                                                                    synchronized (vsi.waitingCalls) {
                                                                        WAMPRPCListener[] ls = vsi.waitingCalls.toArray(new WAMPRPCListener[vsi.waitingCalls.size()]);
                                                                        vsi.waitingCalls.clear();
                                                                        for (WAMPRPCListener l : ls) {
                                                                            l.onCancel(l.getCallId(), reason);
                                                                        }
                                                                    }
                                                                }

                                                                @Override
                                                                public boolean onResult(long callId, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                                                                    vsi.id = (Long) args.get(0);
                                                                    if (DEBUG) {
                                                                        System.out.println("" + getClass().getName() + ".invokeAsync: REST over WAMP[" + caller.getAgent() + "] - REGISTERED VIRTUAL SESSION, " + vsi.id + " [" + (System.nanoTime() - started) / 1000000f + "ms]: " + operationName);
                                                                    }
                                                                    synchronized (vsi.waitingCalls) {
                                                                        WAMPRPCListener[] ls = vsi.waitingCalls.toArray(new WAMPRPCListener[vsi.waitingCalls.size()]);
                                                                        vsi.waitingCalls.clear();
                                                                        for (WAMPRPCListener l : ls) {
                                                                            try {
                                                                                l.getOptions().put(RPC_CALLER_ID_KEY, vsi.id);
                                                                                caller.addWAMPRPCListener(l);
                                                                            } catch (WAMPException wex) {
                                                                                wex.printStackTrace();
                                                                            }
                                                                        }
                                                                    }
                                                                    return true;
                                                                }

                                                                @Override
                                                                public void onError(long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                                                                    vsi.id = 0;
                                                                    if (DEBUG) {
                                                                        System.out.println("" + getClass().getName() + ".invokeAsync: REST over WAMP[" + caller.getAgent() + "] - REGISTER VIRTUAL SESSION FAILED [" + (System.nanoTime() - started) / 1000000f + "ms]: " + operationName + " -> " + error);
                                                                    }
                                                                    synchronized (vsi.waitingCalls) {
                                                                        WAMPRPCListener[] ls = vsi.waitingCalls.toArray(new WAMPRPCListener[vsi.waitingCalls.size()]);
                                                                        vsi.waitingCalls.clear();
                                                                        for (WAMPRPCListener l : ls) {
                                                                            l.onError(l.getCallId(), error, details, args, argsKw);
                                                                        }
                                                                    }
                                                                }
                                                            };
                                                        } else {
                                                            if (vs.id < 0) {
                                                                // add to waiting queue (if not proceded while waitd...
                                                                synchronized (vs.waitingCalls) {
                                                                    if (vs.id < 0) {
                                                                        vs.waitingCalls.add(call);
                                                                        call = null;
                                                                    }
                                                                }
                                                            }
                                                            if (vs.id > 0) {
                                                                vs.lastUsed = System.currentTimeMillis();
                                                                options.put(RPC_CALLER_ID_KEY, vs.id);
                                                            } else if (vs.id == 0) {
                                                                call.onError(0, "access.denied", WAMPTools.EMPTY_DICT, WAMPTools.EMPTY_LIST, WAMPTools.EMPTY_DICT);
                                                                call = null;
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            if (call != null) {
                                                caller.addWAMPRPCListener(call);
                                            }
                                            // no runnable is needed. callback is invoked via WAMPRPCListener event...
                                            return null;
                                        } else {
                                            throw new IOException("No connection to WAMP service!");
                                        }
                                    }

                                    /**
                                     * Replace reflective invocation with
                                     * DBCaller call providing proper
                                     * parametrization.
                                     */
                                    @Override
                                    public <T> T invoke(HttpUser user, Object service, Object[] parameters) throws IOException {
                                        throw new IOException("Only asynchronous calls are supported!");
                                    }
                                };
                                mth.setProvider(pr);
                                //mth.setMethod(m);
                                mth.setName(operationName);
                                mth.setPath("");

                                boolean hasRequest = false;
                                int pri = 0;
                                List<Map<String, Object>> params = (List) map.get("parameters");
                                if (params != null) {
                                    for (Map<String, Object> prm : params) {
                                        if (HttpRequest.class.isAssignableFrom(type2class(prm.get("type")))) {
                                            hasRequest = true;
                                            continue;
                                        }
                                        if (APIAuthContext.class.isAssignableFrom(type2class(prm.get("type")))) {
                                            continue;
                                        }
                                        if ((!prm.containsKey("direction") || prm.get("direction").toString().contains("in"))) {
                                            RESTParameter wsp = new RESTParameter();
                                            wsp.setName((String) prm.get("name"));
                                            wsp.setType(type2class(prm.get("type")));
                                            wsp.setOptional(prm.containsKey("optional") && Boolean.TRUE.equals(prm.get("optional")));
                                            mth.getParams().add(wsp);
                                            pri++;
                                        }
                                    }
                                }
                                if (1 == 0 && !hasRequest) {
                                    // need to add HttpRequest to enable http-authenticated identity...
                                    RESTParameter wsp = new RESTParameter();
                                    wsp.setName(REQUEST_PARAM_NAME);
                                    wsp.setType(HttpRequest.class);
                                    wsp.setOptional(true);
                                    mth.getParams().add(wsp);
                                    pri++;
                                }

                                if (map.containsKey("returns")) {
                                    mth.setReturnType(type2class(((Map) map.get("returns")).get("type")));
                                }
                                rms.add(mth);
                                wci.registered.add(keyPrint);
                            }
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }

                        if (!rms.isEmpty()) {
                            r.put(operationName, rms);
                        }
                    } // END synchronized wci.registered
                }
            } // END synchronized rr
        }
        return r;
    }

    // Converts java or WAMP types to Java class
    public Class type2class(Object type) {
        if (type instanceof Class) {
            return (Class) type;
        }
        if (type instanceof String) {
            if ("dict".equals(type)) {
                return Map.class;
            } else if ("list".equals(type)) {
                return List.class;
            } else if ("uri".equals(type)) {
                return String.class;
            } else if ("id".equals(type)) {
                return Long.class;
            } else if ("bool".equals(type)) {
                return Boolean.class;
            } else if ("integer".equals(type)) {
                return Long.class;
            } else if ("String".equals(type)) {
                return String.class;
            }
            try {
                return Class.forName((String) type);
            } catch (Throwable th) {
                return Object.class;
            }
        }
        if (type != null) {
            return type.getClass();
        } else {
            return Object.class;
        }
    }

    static String methodKeyprint(String operationName, Map<String, Object> proc) {
        StringBuilder sb = new StringBuilder();
        if (proc != null) {
            sb.append("n=" + (operationName != null ? operationName : proc.get("name")));
            List<Map<String, Object>> params = (List) proc.get("parameters");
            if (params == null) {
                sb.append("p=<none>");
            } else {
                sb.append("p=" + params.size());
                for (Map<String, Object> param : params) {
                    sb.append(";n=" + param.get("name"));
                    Object t = param.get("type");
                    if (t == null) {
                        sb.append(";t=<none>");
                    } else {
                        sb.append(";t=" + (t instanceof Map ? ((Map) t).get("type") : t));
                    }
                }
            }
        }
        return sb.toString();
    }

    static WAMP_FP_Reflection.RR rr(WAMPRealm api) {
        if (api != null) {
            WAMPRPCDealer rpcd = api.getActor(WAMP.Role.dealer);
            WAMP_FP_Reflection wr = rpcd.getFeatureProvider(WAMPFeature.procedure_reflection);
            if (wr != null) {
                WAMP_FP_Reflection.RR rr = wr.getRegistrations(api.getName());
                return rr;
            }
        }
        return null;
    }

    /**
     * Per-realm procedure registrations tracking to enable incremental updates
     *
     * Virtual sessions are used to store/wait till session: long[(session id|-1
     * - wip|-2 failed), (last accesses)]
     */
    public class WCI {

        WAMPClient client;
        Collection<String> registered = new HashSet<>();
        Map<String, VSI> virtualSessions = new HashMap<>();

        public WCI(WAMPClient client) {
            this.client = client;
        }
    }

    public class VSI {

        long id = -1;
        long lastUsed = System.currentTimeMillis();
        List<WAMPRPCListener> waitingCalls = new ArrayList<>();
    }
}
