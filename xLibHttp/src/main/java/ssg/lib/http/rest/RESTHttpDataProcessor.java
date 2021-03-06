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

import ssg.lib.http.HttpDataProcessor;
import ssg.lib.http.HttpMatcher;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.http.base.HttpResponse;
import ssg.lib.http.base.MultipartBody;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.common.JSON;
import ssg.lib.common.Refl;
import ssg.lib.common.Refl.ReflJSON;
import ssg.lib.di.DI;
import ssg.lib.http.HttpMatcher.HttpMatcherComposite;
import ssg.lib.http.HttpSession;
import ssg.lib.http.HttpUser;
import ssg.lib.http.base.HttpResponseListener;
import ssg.lib.service.SERVICE_PROCESSING_STATE;

/**
 * REST http processor takes responsibility for managed paths.
 *
 * @author 000ssg
 */
public class RESTHttpDataProcessor<P extends Channel> extends HttpDataProcessor<P> implements HttpResponseListener {

    // root path
    String root;
    // pattern match: String[] is set of fixed ("text") or dynamic ("${text}") path elements.
    Map<HttpMatcher, RESTMethod[]> methods = new LinkedHashMap<HttpMatcher, RESTMethod[]>();
    Map<RESTMethod, Object> serviceProviders = new LinkedHashMap<RESTMethod, Object>();
    Refl refl = new ReflJSON();
    private RESTHttpHelper restHelper = new RESTHttpHelper();
    boolean traceRequests = false;

    public RESTHttpDataProcessor(String root) {
        super(root);
        this.root = root;
        getMatcher().setPathIsPrefix(true);
    }

    public RESTHttpDataProcessor(String root, MethodsProvider[] methodsProviders, Object... providers) {
        super(root);
        this.root = root;
        getMatcher().setPathIsPrefix(true);
        registerProviders(methodsProviders, providers);
    }

    /**
     * Register methods from given providers based on set of method providers.
     * Returns registered paths.
     *
     * @param methodsProviders
     * @param providers
     * @return
     */
    public Collection<HttpMatcher> registerProviders(MethodsProvider[] methodsProviders, Object... providers) {
        return this.registerProviders(null, methodsProviders, providers);
    }

    public Collection<HttpMatcher> registerProviders(HttpMatcher base, MethodsProvider[] methodsProviders, Object... providers) {
        Collection<HttpMatcher> result = new ArrayList<HttpMatcher>();

        if (methodsProviders == null) {
            methodsProviders = new MethodsProvider[]{new XMethodsProvider()};
        }

        if (providers != null && providers.length > 0) try {
            for (MethodsProvider mp : methodsProviders) {
                if (providers != null) {
                    for (Object p : providers) {
                        if (p == null) {
                            continue;
                        }
                        Class pc = (p instanceof Class) ? (Class) p : p.getClass();
                        if (p instanceof Class) {
                            try {
                                p = pc.newInstance();
                            } catch (Throwable th) {
                                th.printStackTrace();
                                continue;
                            }
                        }
                        if (mp.canHandleClass(pc)) {
                            Map<String, List<RESTMethod>> mpcs = mp.findMethods(p);
                            //System.out.println("MPcs size=" + mpcs.size());
                            for (Collection<RESTMethod> rms : mpcs.values()) {
                                registerMethods(base, p, result, rms);
                            }
                        }
                    }
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return result;
    }

    public RESTHttpDataProcessor registerMethods(Object provider, Collection<HttpMatcher> paths, RESTMethod... methods) {
        return registerMethods(null, provider, paths, methods);
    }

    public RESTHttpDataProcessor registerMethods(HttpMatcher base, Object provider, Collection<HttpMatcher> paths, RESTMethod... methods) {
        if (methods != null && methods.length > 0) {
            return registerMethods(base, provider, paths, Arrays.asList(methods));
        }
        return this;
    }

    public RESTHttpDataProcessor registerMethods(Object provider, Collection<HttpMatcher> paths, Collection<RESTMethod>... methods) {
        return this.registerMethods(null, provider, paths, methods);
    }

    public RESTHttpDataProcessor registerMethods(HttpMatcher base, Object provider, Collection<HttpMatcher> paths, Collection<RESTMethod>... methods) {
        if (methods != null) {
            for (Collection<RESTMethod> ms : methods) {
                if (ms == null || ms.isEmpty()) {
                    continue;
                }
                for (RESTMethod m : ms) {
                    if (m == null) {
                        continue;
                    }
                    boolean unique = true;
                    HttpMatcher rm = prepareMethod(m);
                    RESTMethod[] rms = this.methods.get(rm);
                    if (rms == null) {
                        rms = new RESTMethod[]{m};
                    } else {
                        // check if not duplicate
                        for (RESTMethod mi : rms) {
                            if (mi.getMethod() != null && mi.getMethod().equals(m.getMethod())) {
                                unique = false;
                                break;
                            } else if (mi.getParams().toString().equals(m.getParams().toString())) {
                                unique = false;
                                break;
                            }
                        }
                        if (unique) {
                            rms = Arrays.copyOf(rms, rms.length + 1);
                            rms[rms.length - 1] = m;
                        }
                    }

                    if (unique) {
                        this.methods.put(base != null ? base.append(rm) : rm, rms);
                        serviceProviders.put(m, provider);
                        //System.out.println(getClass().getSimpleName() + ": " + root + "/" + m.getPath() + ": " + getRESTHelper().getDefaultAPIType().generateAPIMethodSignature(getRESTHelper(), m, true).replace(",done)", ")").replace("(done)", "()") + " -> " + m.getReturnType().getName());
                        System.out.println(getClass().getSimpleName() + ": " + rm.getPath() + ": " + getRESTHelper().getDefaultAPIType().generateAPIMethodSignature(getRESTHelper(), m, true).replace(",done)", ")").replace("(done)", "()") + (m.getReturnType() != null ? " -> " + m.getReturnType().getName() : ""));
                        paths.add(rm);
                    } else {
                        System.out.println(getClass().getSimpleName() + ": IGNORED DUPLICATE: " + rm.getPath() + ": " + getRESTHelper().getDefaultAPIType().generateAPIMethodSignature(getRESTHelper(), m, true).replace(",done)", ")").replace("(done)", "()") + (m.getReturnType() != null ? " -> " + m.getReturnType().getName() : ""));
                    }
                }
            }
        }
        return this;
    }

    public RESTHttpDataProcessor traceRequests(boolean trace) {
        this.traceRequests = trace;
        return this;
    }

    public HttpMatcher prepareMethod(RESTMethod m) {
        String path = m.getPath();
        if (path == null || path.isEmpty()) {
            path = m.getName();
            boolean fixed = false;
            if (path.startsWith("get") && path.length() > 3) {
                path = path.substring(3);
                fixed = true;
            } else if (path.startsWith("is") && path.length() > 2) {
                path = path.substring(2);
                fixed = true;
            }
            if (fixed && Character.isUpperCase(path.charAt(0))) {
                path = path.substring(0, 1).toLowerCase() + path.substring(1);
            }
        }

        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        RESTProvider rp = m.getProvider();
        if (rp != null && rp.getPaths() != null && rp.getPaths().length > 1) {
            HttpMatcher[] rs = new HttpMatcher[rp.getPaths().length];
            for (int i = 0; i < rs.length; i++) {
                String pp = rp.getPaths()[i];
                pp = (!pp.startsWith("/") ? "/" : "") + pp + (pp.endsWith("/") ? "" : "/") + path;
                rs[i] = new HttpMatcher((root == null || root.equals("/") ? "" : root) + pp);
            }
            HttpMatcherComposite mc = new HttpMatcherComposite(rs);
            return mc;
        } else {
            if (rp != null && rp.getPaths() != null && rp.getPaths().length == 1) {
                String pp = rp.getPaths()[0];
                path = (!pp.isEmpty() && !pp.startsWith("/") ? "/" : "") + pp + (pp.isEmpty() || pp.endsWith("/") || path.startsWith("/") ? "" : "/") + path;
            }
            HttpMatcher rm = new HttpMatcher((root == null || root.equals("/") ? "" : root) + path);
            return rm;
        }
    }

    public Collection<HttpMatcher> getPaths() {
        return Collections.unmodifiableCollection(methods.keySet());
    }

    @Override
    public SERVICE_PROCESSING_STATE check(P provider, DI<ByteBuffer, P> data) throws IOException {
        SERVICE_PROCESSING_STATE r = super.check(provider, data);
        return r;
    }

    @Override
    public void onCompleted(HttpData data) throws IOException {
        super.onCompleted(data); //To change body of generated methods, choose Tools | Templates.

        final HttpRequest req = (HttpRequest) data;
        req.addHttpResponseListener(this);
        if (req.getBody() instanceof MultipartBody) {
            final Map<String, Object> params = new LinkedHashMap<String, Object>();
            RESTMethod m = prepareMethodAndParams(req, params);

            if (m == null) {
//                if (params.containsKey("js")) {
//                    generateJS(req, "" + params.get("js"));
//                } else if (req != null && req.getMatcher().hasQueryPathParameter("js")) {
//                    generateJS(req, req.getMatcher().getQueryPathParameter("js"));
//                } else {
                HttpResponse resp = req.getResponse();
                resp.setResponseCode(500, "Server Error");
                resp.addHeader(HttpData.HH_TRANSFER_ENCODING, HttpData.HTE_CHUNKED);
                resp.onHeaderLoaded();
                resp.add(wrapResponseData(null, "No REST method found at '" + root + "' handler for " + ("" + req.getMatcher()), null));
                resp.onLoaded();
//                }
                return;
                //throw new IOException("No REST method found for " + qrm.toString());
            } else {
                addProcessor(req, m, params);
            }
        } else {
            final Map<String, Object> params = new LinkedHashMap<String, Object>();
            RESTMethod m = prepareMethodAndParams(req, params);
            if (m == null) {
                HttpResponse resp = req.getResponse();
                resp.setResponseCode(500, "Server Error");
                resp.addHeader(HttpData.HH_TRANSFER_ENCODING, HttpData.HTE_CHUNKED);
                resp.onHeaderLoaded();
                resp.add(wrapResponseData(null, "No REST method found at '" + root + "' handler for " + ("" + req.getMatcher()), null));
                resp.onLoaded();
            } else {
                addProcessor(req, m, params);
            }
        }
    }

    public String getBaseURL(HttpData data) {
        String host = data.getHead().getHeader1("Host");
        String protocol = (data.isSecure()) ? "https://" : "http://";
        return protocol + host + this.root;
    }

    @Override
    public void onHeaderLoaded(HttpData data) throws IOException {
        final HttpRequest req = (HttpRequest) data;
        if (req.getBody() instanceof MultipartBody) {
            return;
        }

        final Map<String, Object> params = new LinkedHashMap<String, Object>();
        RESTMethod m = prepareMethodAndParams(req, params);

        if (m == null) {
            if (req.isCompleted()) {
                // if no method for completed request -> error
                HttpResponse resp = req.getResponse();
                resp.setResponseCode(500, "Server Error");
                resp.addHeader(HttpData.HH_TRANSFER_ENCODING, HttpData.HTE_CHUNKED);
                resp.onHeaderLoaded();
                resp.add(wrapResponseData(null, "No REST method found at '" + root + "' handler for " + ("" + req.getMatcher()), null));
                resp.onLoaded();
            } else {
                // need more data: wait, e.g. if content type is "application/x-www-form-urlencoded"
                return;
            }
            //throw new IOException("No REST method found for " + qrm.toString());
        } else {
            addProcessor(req, m, params);
        }
    }

    public RESTMethod prepareMethodAndParams(HttpRequest req, Map params) throws IOException {
        HttpMatcher qrm = req.getMatcher();
        HttpMatcher parentRM = req.getHttpSession().getApplication() != null ? req.getHttpSession().getApplication().getMatcher() : null;

        // add form-data parameters, if any
        if (req.canHaveFormParameters()) {
            req.getFormParameters(params);
        }

        // get path-based matches
        HttpMatcher[] brms = bestMatches(parentRM, qrm);
        if (brms != null) {
            for (HttpMatcher brm : brms) {
                Map probeParams = new LinkedHashMap();
                Map brmParams = brm.getParameters(qrm, false);
                probeParams.putAll(params);
                probeParams.putAll(brmParams);
                RESTMethod[] ms = methods.get(brm);
                RESTMethod m = RESTMethod.findBestWSMethod(Arrays.asList(ms), probeParams);

                // do provider-level access check
                if (m != null && m.getProvider() != null && m.getProvider().getAccess() != null) {
                    HttpSession ctx = req.getHttpSession();
                    if (ctx == null || !m.getAccess().test(ctx.getRAT())) {
                        if (ctx.getUser() == null && ctx.getApplication() != null) {
                            if (ctx.getApplication().doAuthentication(req)) {
                                return null;
                            }
                        }
                        throw new IOException("Not authorized to execute REST methods ('" + m.getName() + "') for " + m.getProvider().getName() + "/" + m.getProvider().getDescription()
                                + "\n  need: " + m.getAccess().toString().replace("\n", "  ")
                                + "\n  have: " + ("" + ctx.getRAT()).replace("\n", "  "));
                    }
                }

                // do method-level access check
                if (m != null && m.getAccess() != null) {
                    HttpSession ctx = req.getHttpSession();
                    if (ctx == null || !m.getAccess().test(ctx.getRAT())) {
                        if (ctx.getUser() == null && ctx.getApplication() != null) {
                            if (ctx.getApplication().doAuthentication(req)) {
                                return null;
                            }
                        }
                        throw new IOException("Not authorized to execute REST method '" + m.getName() + "'"
                                + "\n  need: " + m.getAccess().toString().replace("\n", "  ")
                                + "\n  have: " + ("" + ctx.getRAT()).replace("\n", "  "));
                    }
                }

                if (m != null) {
                    params.putAll(brmParams);
                    return m;
                }
            }
        }
        return null;
    }

    public void addProcessor(final HttpRequest req, final RESTMethod m, final Map params) {
        // ensure parameter typed as HttpRequest/HttpSession/HttpUser is set if present
        if (m != null && !m.getParams().isEmpty()) {
            List<RESTParameter> rps = m.getParams();
            for (RESTParameter rp : rps) {
                if (HttpRequest.class.isAssignableFrom(rp.getType())) {
                    params.put(rp.getName(), req);
                } else if (HttpSession.class.isAssignableFrom(rp.getType())) {
                    params.put(rp.getName(), req.getHttpSession());
                } else if (HttpUser.class.isAssignableFrom(rp.getType())) {
                    params.put(rp.getName(), req.getHttpSession() != null ? req.getHttpSession().getUser() : null);
                }
            }
        }

        try {
            HttpUser user = req != null && req.getHttpSession() != null ? req.getHttpSession().getUser() : null;
            if (beforeRESTMethodInvoke(user, m, params)) {
                Runnable exec = m.invokeAsync(user, serviceProviders.get(m), params,
                        (RESTMethod method, Object service, Map<String, Object> parameters, Object result, long nano, Throwable error, String errorMessage) -> {
                            if (error == null && errorMessage == null) {
                                try {
                                    result = afterRESTMethodInvoke(user, m, parameters, result);

                                    HttpResponse resp = req.getResponse();
                                    if (!resp.isCompleted()) {
                                        resp.setResponseCode(200, "OK");
                                        resp.addHeader(HttpData.HH_TRANSFER_ENCODING, HttpData.HTE_CHUNKED);
                                        resp.setHeader(HttpData.HH_CONTENT_TYPE, "application/json; encoding=utf-8");
                                        List<ByteBuffer> body = wrapResponseData(null, result, null);
                                        resp.onHeaderLoaded();
                                        resp.add(body);
                                        resp.onLoaded();
                                    }
                                } catch (Throwable th) {
                                    error = th;
                                }
                            }
                            if (error != null || errorMessage != null) {
                                try {
                                    HttpResponse resp = req.getResponse();
                                    resp.setResponseCode(500, "Server Error");
                                    resp.addHeader(HttpData.HH_TRANSFER_ENCODING, HttpData.HTE_CHUNKED);
                                    resp.setHeader(HttpData.HH_CONTENT_TYPE, "application/json; encoding=utf-8");
                                    resp.onHeaderLoaded();
                                    resp.add(wrapResponseData(null, "Failed to execute REST method: " + m + " -> " + ((errorMessage != null) ? errorMessage + "\n" : "") + error, error));
                                    resp.onLoaded();
                                } catch (IOException ioex2) {
                                    ioex2.printStackTrace();
                                }
                            }
                        });
                if (exec != null) {
                    switch (m.getRunMode()) {
                        case immediate: // execute immediately rather thatn return Runnable
                            exec.run();
                            break;
                        case asynchronous: // return Runnable to execute method
                            req.addProcessors(exec);
                            break;
                        case delayed: // WIP, currently treated as asynchronous!
                            req.addProcessors(exec);
                            break;
                    }
                }
            } else {
                // return null result!
                HttpResponse resp = req.getResponse();
                if (!resp.isCompleted()) {
                    resp.setResponseCode(200, "OK");
                    resp.addHeader(HttpData.HH_TRANSFER_ENCODING, HttpData.HTE_CHUNKED);
                    resp.setHeader(HttpData.HH_CONTENT_TYPE, "application/json; encoding=utf-8");
                    resp.onHeaderLoaded();
                    resp.add(wrapResponseData(null, null, null));
                    resp.onLoaded();
                }
            }
        } catch (IOException ioex) {
            // do error if can or ignore if channel is unwritable...
            try {
                HttpResponse resp = req.getResponse();
                if (!resp.isCompleted()) {
                    this.do500(req, "text", ioex.toString().getBytes());
                }
            } catch (Throwable th) {
            }
        }
    }

//    @Override
    public boolean matches(HttpData req) {
        if (req instanceof HttpRequest) {
            HttpMatcher r = req.getMatcher();
            //rm.s if(hreq.getContentType())
            for (HttpMatcher rm : methods.keySet()) {
                if (rm.match(r) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public HttpMatcher bestMatch(HttpMatcher r) {
        HttpMatcher result = null;
        float resultf = 0;
        //System.out.println("brm: " + r);
        for (HttpMatcher rm : methods.keySet()) {
            float f = rm.match(r);
            //System.out.println("  [" + resultf + "]\t-> " + f + "\t" + rm.toString().replace("\n", "\n    \t\t"));
            if (f > 0 && f > resultf) {
                result = rm;
                resultf = f;
            }
        }
        return result;
    }

    public HttpMatcher[] bestMatches(HttpMatcher parent, HttpMatcher r) {
        HttpMatcher[] result = new HttpMatcher[methods.size()];
        Float[] resultf = new Float[result.length];
        int off = 0;
        //System.out.println("brm: " + r);
        for (HttpMatcher rm : methods.keySet()) {
            float f = rm.match(parent, r);
            //System.out.println("  [" + resultf + "]\t-> " + f + "\t" + rm.toString().replace("\n", "\n    \t\t"));
            if (f > 0) {
                result[off] = rm;
                resultf[off++] = f;
                // move up if better match...
                if (off > 1) {
                    int pos = - 1;
                    for (int i = off - 2; i >= 0; i--) {
                        if (resultf[i] < f) {
                            pos = i;
                        } else {
                            break;
                        }
                    }
                    if (pos != -1) {
                        for (int i = off - 1; i >= pos; i--) {
                            if (i == 0) {
                                // why are we here?
                                break;
                            }
                            result[i] = result[i - 1];
                        }
                        result[pos] = rm;
                    }
                }
            }
        }
        if (off == 0) {
            return null;
        }
        if (off < result.length) {
            result = Arrays.copyOf(result, off);
        }
        return result;
    }

    public Object execute(HttpRequest req, Object service, RESTMethod m, Map<String, Object> params) throws IOException {
        Object result = null;
        if (m != null) {
            try {
                //System.out.println("REST start[" + System.identityHashCode(req) + "]: " + m);
                Object[] values = RESTMethod.prepareArgumentValuesForMethod(m, params);
                if (values != null) {
                    List<RESTParameter> rps = m.getParams();
                    for (int i = 0; i < values.length; i++) {
                        if (values[i] == null && HttpRequest.class.isAssignableFrom(rps.get(i).getType())) {
                            values[i] = req;
                            break;
                        }
                    }
                    for (int i = 0; i < values.length; i++) {
                        if (values[i] == null && HttpUser.class.isAssignableFrom(rps.get(i).getType())) {
                            values[i] = req.getHttpSession() != null ? req.getHttpSession().getUser() : null;
                            break;
                        }
                    }
                }

                HttpUser user = req != null && req.getHttpSession() != null ? req.getHttpSession().getUser() : null;
                // invoke
                if (beforeRESTMethodInvoke(user, m, params)) {
                    if (m.getReturnType() != null && !m.getReturnType().equals(void.class)) {
                        Object obj = m.invoke(user, service, values);
                        // callback
                        obj = afterRESTMethodInvoke(user, m, params, obj);
                        result = obj;
                    } else {
                        m.invoke(user, service, values);
                        // callback
                        result = afterRESTMethodInvoke(user, m, params, null);
                    }

                } else {
                    //ctx.setFailure(null, "Processing denied.");
                }
            } catch (Throwable th) {
                //ctx.setFailure((th.getCause() != null) ? th.getCause() : th, "Failed");
                if (th instanceof IOException) {
                    throw (IOException) th;
                } else {
                    throw new IOException(th);
                }
            } finally {
                //System.out.println("REST done: " + result);
            }
        } else {
            //System.out.println("REST not done: no method");
        }
        return result;
    }

    public List<ByteBuffer> wrapResponseData(String format, Object result, Throwable th) throws IOException {
        List<ByteBuffer> r = new ArrayList<>();
        if (format == null) {
            format = "json";
        }
        if (format.toLowerCase().contains("json")) {
            Map map = new LinkedHashMap();
            if (th != null) {
                map.put("error", th.toString());
                if (result != null) {
                    map.put("errorData", result);
                }
            } else {
                map.put("result", result);
            }

            JSON.Encoder enc = new JSON.Encoder("UTF-8", refl);
            refl.clearRefs();
            enc.put(map);
            int c = 4096;
            ByteBuffer bb = null;
            while ((bb = enc.read(c)) != null) {
                r.add(bb);
            }
        } else {
            if (th != null) {
                r.add(ByteBuffer.wrap(("" + th + "\n").getBytes()));
                if (result != null) {
                    r.add(ByteBuffer.wrap(("" + result).getBytes()));
                }
            } else {
                r.add(ByteBuffer.wrap(("" + result).getBytes()));
            }
        }
        return r;
    }

    /**
     * Callback method for enabling/disabling method processing...
     *
     * @param ctx
     * @param m
     * @param values
     * @return
     */
    public boolean beforeRESTMethodInvoke(HttpUser user, RESTMethod m, Map<String, Object> params) {
        return true;
    }

    /**
     * Callback method for handling result.
     *
     * @param ctx
     * @param m
     * @param values
     * @param result
     * @return
     */
    public Object afterRESTMethodInvoke(HttpUser user, RESTMethod m, Map<String, Object> params, Object result) throws IOException {
        return result;
    }

    @Override
    public Runnable getRunnable(P provider, DI<ByteBuffer, P> data) {
        Runnable run = null;
        HttpData http = http(provider, data);
        if (http.hasProcessors()) {
            final Runnable[] runs = http.fetchProcessors();
            if (runs != null) {
                run = new Runnable() {
                    @Override
                    public void run() {
                        for (Runnable r : runs) {
                            r.run();
                        }
                    }
                };
            }
        }
        return run;
    }

    /**
     * By default there're no processes associated/associable with REST data
     * processor...
     *
     * @return
     */
    @Override
    public List<Task> getTasks(TaskPhase... phases) {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append((getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName()));
        sb.append("{" + "root=" + root + ", methods=" + methods.size() + ", trace requests=" + traceRequests);
        if (!methods.isEmpty()) {
            for (Entry<HttpMatcher, RESTMethod[]> e : methods.entrySet()) {
                sb.append("\n  " + e.getKey().toString().replace("\n", "\n  "));
                sb.append("\n  ==>");
                for (RESTMethod rm : e.getValue()) {
                    sb.append("\n    " + rm.toString().replace("\n", "\n    "));
                }
            }
            sb.append("\n");
        }
        sb.append('}');

        return sb.toString();
    }

    /**
     * @return the restHelper
     */
    public RESTHttpHelper getRESTHelper() {
        return restHelper;
    }

    /**
     * Assigns default RESTHelper instance. Null is ignored!
     *
     * @param restHelper the restHelper to set
     */
    public void setRESTHelper(RESTHttpHelper restHelper) {
        if (restHelper != null) {
            this.restHelper = restHelper;
        }
    }

    /////////////////////////////////////////////////////// HttpResponseListener
    @Override
    public void onResponseSent(HttpResponse resp) {
    }

    @Override
    public void onResponseHeaderSent(HttpResponse resp) {
    }

    @Override
    public void onResponseLoaded(HttpResponse resp) {
        if (traceRequests) {
            System.out.println("[" + System.currentTimeMillis() + "][" + Thread.currentThread().getName() + "].request[" + resp.getQuery() + "] completed in " + (System.currentTimeMillis() - resp.getRequestedAt()) + "ms, size=" + resp.getOutputSize()+" for "+resp.getConnectionInfo());
        }
    }

    @Override
    public void onResponseHeaderLoaded(HttpResponse resp) {
    }

}
