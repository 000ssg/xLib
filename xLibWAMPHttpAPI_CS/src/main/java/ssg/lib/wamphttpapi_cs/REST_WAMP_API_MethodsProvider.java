/*
 * The MIT License
 *
 * Copyright 2020 000ssg.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.api.APICallable;
import ssg.lib.api.API_Publisher;
import ssg.lib.http.HttpUser;
import ssg.lib.http.rest.RESTMethod;
import ssg.lib.http.rest.RESTProvider;
import ssg.lib.httpapi_cs.APIStatistics;
import ssg.lib.httpapi_cs.API_MethodsProvider;
import ssg.lib.wamp.nodes.WAMPClient;
import ssg.lib.wamp.rpc.impl.WAMPRPCListener;

/**
 *
 * @author 000ssg
 */
public class REST_WAMP_API_MethodsProvider extends API_MethodsProvider {

    public static boolean DEBUG = false;
    Map<String, WAMPClient> callers = new LinkedHashMap<>();
    ThreadLocal<String> realm = new ThreadLocal<>();

//    public REST_WAMP_API_MethodsProvider(WAMPClient... callers) {
//        addCallers(callers);
//    }
    public REST_WAMP_API_MethodsProvider(APIStatistics stat, WAMPClient... callers) {
        super(stat);
        addCallers(callers);
    }

    public void addCallers(WAMPClient... callers) {
        if (callers != null) {
            for (WAMPClient caller : callers) {
                this.callers.put(caller.getRealm(), caller);
            }
        }
    }

    WAMPClient caller(String realm) {
        return callers.get(realm);
    }

    @Override
    public Runnable invokeAsync(
            final HttpUser user,
            final RESTMethod method,
            final String name,
            final APICallable m,
            final Object service,
            final Map<String, Object> parameters,
            final RESTMethod.RESTMethodAsyncCallback callback) throws IOException {
        WAMPClient caller = caller(method.getProvider().getProperty("realm"));
        if (caller != null && caller.isConnected()) {
            if (DEBUG) {
                System.out.println("" + getClass().getName() + ".invokeAsync: REST over WAMP[" + caller.getAgent() + "]: " + name + "(" + parameters + ")");
            }

            APIStatistics apiStat = method.getProperty("apiStat");
            if (apiStat != null) {
                apiStat.onTryInvoke();
                apiStat.onInvoke();
            }

            final long started = System.nanoTime();
            caller.addWAMPRPCListener(new WAMPRPCListener.WAMPRPCListenerBase(new HashMap(), name, Collections.emptyList(), parameters) {
                @Override
                public void onCancel(long callId, String reason) {
                    if (apiStat != null) {
                        apiStat.onDone();
                    }
                    if (DEBUG) {
                        System.out.println("" + getClass().getName() + ".invokeAsync: REST over WAMP[" + caller.getAgent() + "] - CANCEL [" + (System.nanoTime() - started) / 1000000f + "ms]: " + name + "(" + parameters + ")");
                    }
                    callback.onResult(method, service, parameters, null, System.nanoTime() - started, null, reason);
                }

                @Override
                public boolean onResult(long callId, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                    if (apiStat != null) {
                        apiStat.onDone();
                    }
                    if (DEBUG) {
                        System.out.println("" + getClass().getName() + ".invokeAsync: REST over WAMP[" + caller.getAgent() + "] - RESULT [" + (System.nanoTime() - started) / 1000000f + "ms]: " + name + "(" + parameters + ")");
                    }
                    callback.onResult(method, service, parameters, (argsKw != null) ? argsKw : args, System.nanoTime() - started, null, null);
                    return true;
                }

                @Override
                public void onError(long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                    if (apiStat != null) {
                        apiStat.onError();
                    }
                    if (DEBUG) {
                        System.out.println("" + getClass().getName() + ".invokeAsync: WAMP[" + caller.getAgent() + "] - ERROR  [" + (System.nanoTime() - started) / 1000000f + "ms]: " + name + "(" + parameters + ")");
                    }
                    callback.onResult(method, service, parameters, (argsKw != null) ? argsKw : args, System.nanoTime() - started, null, error);
                }
            });
            // no runnable is needed. callback is invoked via WAMPRPCListener event...
            return null;
        } else {
            return super.invokeAsync(user, method, name, m, service, parameters, callback);
        }
    }

    @Override
    public String adjustPath(RESTProvider pr, API_Publisher apis, String path) {
        String p = super.adjustPath(pr, apis, path);
        String r = realm.get();
        pr.setProperty("realm", r);
        if (r != null && !r.isEmpty()) {
            if (p.equalsIgnoreCase(apis.getAPI().name)) {
                p = "";
            }
            p = r + (r.endsWith("/") || p.isEmpty() || p.startsWith("/") ? "" : "/") + p;
        }
        return p;
    }

    public void setRealmTL(String realm) {
        this.realm.set(realm);
    }

}
