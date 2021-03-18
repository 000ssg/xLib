/*
 * The MIT License
 *
 * Copyright 2020 Sergey Sidorov/000ssg@gmail.com
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
package ssg.lib.http.rest;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import ssg.lib.http.base.HttpRequest;

/**
 *
 * @author 000ssg
 */
public class RESTMethod {

    public static enum RUN_MODE {
        immediate, // execute immediately rather thatn return Runnable
        asynchronous, // return Runnable to execute method
        delayed // WIP, currently treated as asynchronous!
    }

    private static Map<Class, Map<String, List<RESTMethod>>> cache = new HashMap<Class, Map<String, List<RESTMethod>>>();
    private static List<MethodsProvider> methodProviders = new ArrayList<MethodsProvider>();

    private RESTProvider provider;
    private Method method;
    private String name;
    private String path;
    Class returnType;
    private List<RESTParameter> params = new ArrayList<RESTParameter>();
    private RESTAccess access;
    private RESTMethodAsyncCallback defaultCallback;
    private RUN_MODE runMode = RUN_MODE.asynchronous;
    private Map<String, Object> properties;

    public static Iterable<MethodsProvider> getMethodProviders() {
        return Collections.unmodifiableCollection(methodProviders);
    }

    static {
        if (1 == 0) {
            MethodsProvider[] mps = new MethodsProvider[]{
                new WSDLMethodsProvider(),
                new XMethodsProvider()
            };
            for (MethodsProvider mp : mps) {
                if (mp.isOperable()) {
                    registerMethodProviders(mp);
                }
            }
        }
    }

    public static void registerMethodProviders(MethodsProvider... providers) {
        if (providers != null) {
            for (MethodsProvider provider : providers) {
                if (provider != null && !methodProviders.contains(provider)) {
                    methodProviders.add(provider);
                }
            }
        }
    }

    public static void unregisterMethodProviders(MethodsProvider... providers) {
        if (providers == null) {
            methodProviders.clear();
        } else {
            for (MethodsProvider provider : providers) {
                if (provider != null && methodProviders.contains(provider)) {
                    methodProviders.remove(provider);
                }
            }
        }
    }

    /**
     * Returns collection of WS methods for given name. if empty - no such web
     * method or class is not web service.
     *
     * @param clazz
     * @param name
     * @return
     */
    public static List<RESTMethod> getRESTMethod(Class clazz, String name) {
        if (cache.containsKey(clazz)) {
            Map<String, List<RESTMethod>> ms = cache.get(clazz);
            return ms.get(name);
        } else {
            Collection<String> mns = getRESTMethodNames(clazz);
            if (mns.contains(name)) {
                Map<String, List<RESTMethod>> ms = cache.get(clazz);
                return ms.get(name);
            } else {
                return Collections.emptyList();
            }
        }
    }

    /**
     * Returns all class methods with all signature variants.
     *
     * @param clazz
     * @return
     */
    public static Map<String, List<RESTMethod>> getRESTMethods(Class clazz) {
        if (cache.containsKey(clazz)) {
            Map<String, List<RESTMethod>> wms = cache.get(clazz);
            return wms;
        } else {
            getRESTMethodNames(clazz);
            Map<String, List<RESTMethod>> wms = cache.get(clazz);
            return wms;
        }
    }

    /**
     * Returns collection of available web methods. If empty - no web methods or
     * class is not web service.
     *
     * This is initializer method that scans provided class for presence of
     * WebMethod and related WebParameter annotations and caches results for
     * re-use.
     *
     * @param clazz
     * @return
     */
    public static List<String> getRESTMethodNames(Class clazz) {
        if (cache.containsKey(clazz)) {
            Map<String, List<RESTMethod>> wms = cache.get(clazz);
            return Arrays.asList(wms.keySet().toArray(new String[wms.size()]));
        }
        try {
            for (MethodsProvider provider : methodProviders) {
                if (provider == null || !provider.canHandleClass(clazz)) {
                    continue;
                }
                Map<String, List<RESTMethod>> wms = provider.findMethods(clazz);
                if (wms != null) {
                    cache.put(clazz, wms);
                    return Arrays.asList(wms.keySet().toArray(new String[wms.size()]));
                }
            }
            return Collections.emptyList();
        } catch (Throwable th) {
            return Collections.emptyList();
        }
    }

    /**
     * Searches in available methods for the best matching provided parameters.
     * All methods with more or equal # of parameters are chosen 1st. Then all
     * methods that do not have at least 1 provided named parameter are ignored.
     * Finally method with exactly same # of parameters is chosen or, if none -
     * 1st in the list.
     *
     * If no method is matching parameters set then returns null.
     *
     * @param ms
     * @param params
     * @return
     */
    public static RESTMethod findBestWSMethod(List<RESTMethod> ms, Map<String, Object> params) {
        if (ms == null) {
            return null;
        }
        List<RESTMethod> matching = new ArrayList<RESTMethod>();
        // use # of params
        for (RESTMethod m : ms) {
            if (m.getParams().size() >= params.size()) {
                matching.add(m);
            }
        }
        // remove those with mismatching parameter names
        for (int i = matching.size() - 1; i >= 0; i--) {
            RESTMethod m = matching.get(i);
            for (String pn : params.keySet()) {
                boolean exists = false;
                for (RESTParameter p : m.getParams()) {
                    if (pn.equals(p.getName()) || p.getType().isArray() && pn.equals(p.getName() + "[]")) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    matching.remove(m);
                }
            }
        }
        // return with same # of parameters if available
        for (RESTMethod m : matching) {
            if (m.getParams().size() == params.size()) {
                return m;
            }
        }
        // return if all mandatory params exist
        for (RESTMethod m : matching) {
            if (m.getParams().size() > params.size()) {
                boolean missingMandatoryParams = false;
                for (RESTParameter p : m.getParams()) {
                    if (!p.isOptional() && !params.containsKey(p.getName())) {
                        if (!HttpRequest.class.isAssignableFrom(p.getType())) {
                            missingMandatoryParams = true;
                        }
                        break;
                    }
                }
                if (missingMandatoryParams) {
                    continue;
                }
                return m;
            }
        }
        return null;
//        // return 1st or null if none
//        if (matching.isEmpty()) {
//            return null;
//        } else {
//            return matching.get(0);
//        }
    }

    /**
     * Prepares argument for calling the method. Uses order of method parameters
     * and puts properly named parameter value if available. For primitive types
     * assigns default 0-like or (for boolean) - false value. For matching
     * parameters applies convertToType() method to adjust value. If not
     * convertible/assignable - parameter is ignored.
     *
     * @param m
     * @param params
     * @return
     */
    public static Object[] prepareArgumentValuesForMethod(RESTMethod m, Map<String, Object> params) throws IOException {
        Object[] values = new Object[m.getParams().size()];
        for (int i = 0; i < m.getParams().size(); i++) {
            RESTParameter p = m.getParams().get(i);
            if (params.containsKey(p.getName())) {
                try {
                    values[i] = RESTDataTools.convertToType(p.getType(), params.get(p.getName()), true);
                } catch (Throwable th) {
                    throw new IOException("Failed to convert value for " + p.getName() + " to type " + p.getType() + ". Error: " + th);
                }
            } else if (p.getType().isArray() && params.containsKey(p.getName() + "[]")) {
                try {
                    values[i] = RESTDataTools.convertToType(p.getType(), params.get(p.getName() + "[]"), true);
                } catch (Throwable th) {
                    throw new IOException("Failed to convert value for " + p.getName() + " to type " + p.getType() + ". Error: " + th);
                }
            } else if (p.getType().isPrimitive()) {
                if (p.getType().equals(boolean.class)) {
                    values[i] = false;
                } else if (p.getType().equals(char.class)) {
                    values[i] = (char) 0;
                } else if (p.getType().equals(byte.class)) {
                    values[i] = (byte) 0;
                } else if (p.getType().equals(short.class)) {
                    values[i] = (short) 0;
                } else if (p.getType().equals(int.class)) {
                    values[i] = (int) 0;
                } else if (p.getType().equals(float.class)) {
                    values[i] = (float) 0;
                } else if (p.getType().equals(double.class)) {
                    values[i] = (double) 0;
                }
            }
        }
        return values;
    }

    /**
     * @return the method
     */
    public Method getMethod() {
        return method;
    }

    /**
     * @param method the method to set
     */
    public void setMethod(Method method) {
        this.method = method;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the returnType
     */
    public Class getReturnType() {
        return returnType;
    }

    /**
     * @param returnType the returnType to set
     */
    public void setReturnType(Class returnType) {
        this.returnType = returnType;
    }

    /**
     * @return the params
     */
    public List<RESTParameter> getParams() {
        return params;
    }

    /**
     * @param params the params to set
     */
    public void setParams(List<RESTParameter> params) {
        this.params = params;
    }

    /**
     * @return the path
     */
    public String getPath() {
        return path;
    }

    /**
     * @param path the path to set
     */
    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append((getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName()));
        sb.append("{");
        sb.append("\n  method=" + method);
        sb.append("\n  name=" + name);
        sb.append("\n  path=" + path);
        sb.append("\n  returnType=" + returnType);
        sb.append("\n  params=" + ((params != null) ? params.size() : "<none>"));
        if (params != null) {
            for (RESTParameter rp : params) {
                sb.append("\n    " + rp.toString().replace("\n", "\n    "));
            }
        }
        if (access != null) {
            sb.append("\n  access=" + access.toString().replace("\n", "\n  "));
        }
        sb.append("\n");
        sb.append('}');
        return sb.toString();
    }

    /**
     * @return the provider
     */
    public RESTProvider getProvider() {
        return provider;
    }

    /**
     * @param provider the provider to set
     */
    public void setProvider(RESTProvider provider) {
        this.provider = provider;
    }

    /**
     * @return the roles
     */
    public RESTAccess getAccess() {
        return access;
    }

    /**
     * @param roles the roles to set
     */
    public void setAccess(RESTAccess access) {
        this.access = access;
    }

    /**
     * Execution of method. Defaults to call of reflective "Method.invoke".
     *
     * @param <T>
     * @param service
     * @param parameters
     * @return
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws IOException
     */
    public <T> T invoke(Object service, Object[] parameters) throws
            IllegalAccessException,
            InvocationTargetException,
            IOException {
        return (T) getMethod().invoke(service, parameters);
    }

    /**
     * Provides Runnable for asynchronous execution of REST method with given
     * parameters returning result via callback. Actual method invocation is
     * implemented in "invoke" method.
     *
     * Converts named parameters to ordered ones and invokes "invoke".
     *
     * If no delayed processing is needed do invocation inline and
     *
     * @param service
     * @param parameters
     * @param callback
     * @return
     * @throws IOException
     */
    public Runnable invokeAsync(Object service, Map<String, Object> parameters, RESTMethodAsyncCallback callback) throws IOException {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                RESTMethod m = RESTMethod.this;
                long start = System.nanoTime();
                Object result = null;
                Throwable error = null;
                try {
                    Object[] values = RESTMethod.prepareArgumentValuesForMethod(m, parameters);

                    // invoke
                    if (m.getReturnType() != null && !m.getReturnType().equals(void.class)) {
                        Object obj = m.invoke(service, values);
                        result = obj;
                    } else {
                        m.invoke(service, values);
                    }
                } catch (Throwable th) {
                    error = th;
                }
                if (callback != null) {
                    callback.onResult(m, service, parameters, result, System.nanoTime() - start, error, null);
                } else {
                    getDefaultCallback().onResult(m, service, parameters, result, System.nanoTime() - start, error, null);
                }
            }
        };
        return r;
    }

    /**
     * Support for asynchronous REST method invocation.
     */
    public static interface RESTMethodAsyncCallback {

        void onResult(RESTMethod method, Object service, Map<String, Object> parameters, Object result, long nano, Throwable error, String errorMessage);
    }

    /**
     * @return the defaultCallback
     */
    public RESTMethodAsyncCallback getDefaultCallback() {
        return defaultCallback;
    }

    /**
     * @param defaultCallback the defaultCallback to set
     */
    public void setDefaultCallback(RESTMethodAsyncCallback defaultCallback) {
        this.defaultCallback = defaultCallback;
    }

    /**
     * @return the runMode
     */
    public RUN_MODE getRunMode() {
        return runMode;
    }

    /**
     * @param runMode the runMode to set
     */
    public void setRunMode(RUN_MODE runMode) {
        this.runMode = runMode;
    }

    public void setProperty(String name, Object v) {
        if (name == null || properties == null && v == null) {
            return;
        }
        if (properties == null) {
            properties = new LinkedHashMap<>();
        }
        if (v != null) {
            properties.put(name, v);
        } else if (properties.containsKey(name)) {
            properties.remove(name);
        }
    }

    public <Z> Z getProperty(String name) {
        return properties != null ? (Z) properties.get(name) : null;
    }
}
