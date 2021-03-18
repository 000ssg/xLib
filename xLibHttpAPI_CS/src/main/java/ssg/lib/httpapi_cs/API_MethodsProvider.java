/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ssg.lib.httpapi_cs;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.api.API;
import ssg.lib.api.APICallable;
import ssg.lib.api.API_Publisher;
import ssg.lib.api.API_Publisher.API_Publishers;
import ssg.lib.api.APIParameterDirection;
import ssg.lib.api.APIParameter;
import ssg.lib.api.APIProcedure;
import ssg.lib.api.util.APIException;
import ssg.lib.api.util.APISearchable.APIMatcher.API_MATCH;
import ssg.lib.http.rest.MethodsProvider;
import ssg.lib.http.rest.RESTMethod;
import ssg.lib.http.rest.RESTParameter;
import ssg.lib.http.rest.RESTProvider;

/**
 *
 * @author 000ssg
 */
public class API_MethodsProvider implements MethodsProvider {

    APIStatistics baseStat;

    public API_MethodsProvider() {
    }

    public API_MethodsProvider(APIStatistics stat) {
        baseStat = stat;
    }

    @Override
    public boolean isOperable() {
        return true;
    }

    @Override
    public boolean canHandleClass(Class clazz) {
        return clazz != null && (API_Publishers.class.isAssignableFrom(clazz) || API_Publisher.class.isAssignableFrom(clazz) || API.class.isAssignableFrom(clazz));
    }

    public boolean canHandleAPI(String api) {
        return api != null;
    }

    public boolean canHandleMethod(String api, String name) {
        return name != null;
    }

    public Runnable invokeAsync(
            RESTMethod method,
            String name,
            APICallable m,
            Object service, Map<String, Object> parameters, RESTMethod.RESTMethodAsyncCallback callback
    ) throws IOException {
        //System.out.println("" + getClass().getName() + ".invokeAsync: API: " + name + "(" + parameters + ")");
        return method.invokeAsync(service, parameters, callback);
    }

    @Override
    public Map<String, List<RESTMethod>> findMethods(Object obj) {
        Map<String, List<RESTMethod>> wms = new LinkedHashMap<String, List<RESTMethod>>();
        if (canHandleClass((obj != null) ? obj.getClass() : null)) {
            API_Publishers apiss = (obj instanceof API_Publishers) ? (API_Publishers) obj : null;
            API_Publisher apis = (obj instanceof API_Publisher) ? (API_Publisher) obj : null;
            API dbAPI = (obj instanceof API) ? (API) obj : null;
            if (apis == null && apiss == null && dbAPI != null) {
                apiss = new API_Publishers().add(null, dbAPI);
            }
            if (apiss == null && apis != null) {
                apiss = new API_Publishers().add(null, apis);
            }

            List<RESTMethod> ms = new ArrayList<RESTMethod>();

            for (String apiName : apiss.getAPINames().toArray(new String[apiss.getAPINames().size()])) {
                apis = apiss.getAPIPublisher(apiName);

                RESTProvider pr = new RESTProvider();
                {
                    String s = apiName.replace(".", "/");
                    pr.setName(s);
                    pr.setPaths(adjustPath(pr, apis, s.toLowerCase()));
                }

                for (APIProcedure p : (Collection<APIProcedure>) (Object) apis.getAPI().find((item) -> {
                    return item instanceof APIProcedure ? API_MATCH.exact : API_MATCH.partial;
                }, APIProcedure.class, null)) {
                    String operationName = p.fqn();
                    final APICallable m = apis.getCallable(operationName, null);
                    if (m == null) {
                        continue;
                    }

                    String[] operationPaths = new String[]{operationName};

                    for (String operationPath : operationPaths) {
                        RESTMethod mth = new RESTMethod() {
                            RESTMethod self = this;
                            boolean inAsynch = false; // avoid short circuit...
                            APIStatistics apiStat = baseStat != null ? baseStat.createChild(baseStat, "REST:"+operationName) : null;

                            {
                                if (apiStat != null) {
                                    setProperty("apiStat", apiStat);
                                }
                            }

                            /**
                             * Pass async invocation via method provider to
                             * enable alternative solution.
                             */
                            @Override
                            public Runnable invokeAsync(Object service, Map<String, Object> parameters, RESTMethod.RESTMethodAsyncCallback callback) throws IOException {
                                if (inAsynch) {
                                    // if loop - create runnable.
                                    return new Runnable() {
                                        @Override
                                        public void run() {
                                            if (apiStat != null) {
                                                apiStat.onTryInvoke();
                                            }
                                            long started = System.nanoTime();
                                            Object result = null;
                                            Throwable error = null;
                                            try {
                                                if (apiStat != null) {
                                                    apiStat.onInvoke();
                                                }
                                                result = m.call(parameters);
                                            } catch (Throwable th) {
                                                error = th;
                                                if (apiStat != null) {
                                                    apiStat.onError();
                                                }
                                            } finally {
                                                if (apiStat != null) {
                                                    apiStat.onDone();
                                                }
                                                if (callback != null) {
                                                    callback.onResult(self, service, parameters, result, System.nanoTime() - started, error, error != null ? error.toString() : null);
                                                }
                                            }
                                        }
                                    };
                                } else {
                                    inAsynch = true;
                                    try {
                                        return API_MethodsProvider.this.invokeAsync(
                                                this,
                                                getName(),
                                                m,
                                                service, parameters, callback);
                                    } finally {
                                        inAsynch = false;
                                    }
                                }
                            }

                            /**
                             * Replace reflective invocation with DBCaller call
                             * providing proper parametrization.
                             */
                            @Override
                            public <T> T invoke(Object service, Object[] parameters) throws IllegalAccessException, InvocationTargetException, IOException {
                                if (apiStat != null) {
                                    apiStat.onTryInvoke();
                                }
                                try {
                                    Map<String, Object> ps = m.toParametersMap(parameters);
                                    if (apiStat != null) {
                                        apiStat.onInvoke();
                                    }
                                    return (T) m.call(ps);
                                } catch (APIException sex) {
                                    if (apiStat != null) {
                                        apiStat.onError();
                                    }
                                    throw new InvocationTargetException(sex);
                                } finally {
                                    if (apiStat != null) {
                                        apiStat.onDone();
                                    }
                                }
                            }
                        };
                        mth.setProvider(pr);
                        //mth.setMethod(m);
                        mth.setName(operationName);
                        mth.setPath(operationPath);
                        APIProcedure proc = m.getAPIProcedure(null);
                        int pri = 0;
                        for (String pn : proc.params.keySet()) {
                            APIParameter prm = proc.params.get(pn);
                            if (prm.direction == APIParameterDirection.in || prm.direction == APIParameterDirection.in_out) {
                                RESTParameter wsp = new RESTParameter();
                                wsp.setName(pn);
                                wsp.setType(prm.type.getJavaType());
                                wsp.setOptional(!prm.mandatory);
                                mth.getParams().add(wsp);
                                pri++;
                            }
                        }

                        mth.setReturnType(API.APIResult.class);
                        if (ms.isEmpty()) {
                            ms.add(mth);
                            wms.put(operationName, ms);
                        } else if (ms.get(0).getName().equals(operationName)) {
                            ms.add(mth);
                        } else {
                            ms = new ArrayList<RESTMethod>();
                            ms.add(mth);
                            wms.put(operationName, ms);
                        }
                    }

                }
            }

        }

        return wms;
    }

    /**
     * Optional modify path, e.g. add prefix to separate namespaces.
     *
     * @param apis
     * @param path
     * @return
     */
    public String adjustPath(RESTProvider pr, API_Publisher apis, String path) {
        return path;
    }
}
