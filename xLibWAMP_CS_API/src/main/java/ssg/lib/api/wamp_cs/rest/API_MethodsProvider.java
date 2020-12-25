/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ssg.lib.api.wamp_cs.rest;

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
 * @author sesidoro
 */
public class API_MethodsProvider implements MethodsProvider {

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
        System.out.println("" + getClass().getName() + ".invokeAsync: API: " + name + "(" + parameters + ")");
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
                RESTProvider pr = new RESTProvider();
                {
                    String s = apiName.replace(".", "/");
                    pr.setName(s);
                    pr.setPaths(s.toLowerCase());
                }

                apis = apiss.getAPIPublisher(apiName);

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
                            /**
                             * Pass async invocation via method provider to
                             * enable alternative solution.
                             */
                            @Override
                            public Runnable invokeAsync(Object service, Map<String, Object> parameters, RESTMethod.RESTMethodAsyncCallback callback) throws IOException {
                                return API_MethodsProvider.this.invokeAsync(
                                        this,
                                        getName(),
                                        m,
                                        service, parameters, callback);
                            }

                            /**
                             * Replace reflective invocation with DBCaller call
                             * providing proper parametrization.
                             */
                            @Override
                            public <T> T invoke(Object service, Object[] parameters) throws IllegalAccessException, InvocationTargetException, IOException {
                                try {
                                    Map<String, Object> ps = m.toParametersMap(parameters);
                                    return (T) m.call(ps);
                                } catch (APIException sex) {
                                    throw new InvocationTargetException(sex);
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
}
