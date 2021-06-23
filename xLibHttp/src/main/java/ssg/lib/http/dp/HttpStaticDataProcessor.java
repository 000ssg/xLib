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
package ssg.lib.http.dp;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;
import ssg.lib.common.Replacement;
import ssg.lib.di.DI;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.HttpDataProcessor;
import ssg.lib.http.HttpMatcher;
import ssg.lib.http.HttpResource;
import ssg.lib.http.HttpSession;
import ssg.lib.http.HttpUser;
import ssg.lib.http.base.Body;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.http.base.HttpResponse;
import ssg.lib.http.di.DIHttpData;
import ssg.lib.service.SERVICE_PROCESSING_STATE;

/**
 *
 * @author 000ssg
 * @param <P>
 */
public class HttpStaticDataProcessor<P extends Channel> extends HttpDataProcessor<P> {

    public boolean DEBUG = false;
    public boolean DEBUG_DP = false;

    public static final long UNKNOWN_SIZE = -2;
    public static long DEFAULT_DATA_PIPE_TIMEOUT = 1000 * 60 * 2;

    String pStarter = "${";
    String pEnder = "}";

    private Map<HttpMatcher, HttpResource> resources = new LinkedHashMap<>();
    private Map<HttpData, Runnable> runnables = new HashMap<>();
    private Collection<Runnable> initializers = new LinkedHashSet<>();
    private String localizables;
    private Map<String, ParameterResolver[]> resolvers = new HashMap<>();

    boolean useDataPipes = true;
    private Map<HttpData, DataPipe> dataPipes = new HashMap() {
        @Override
        public Object remove(Object key) {
            Object obj = super.remove(key);
            if (DEBUG_DP) {
                System.out.println(
                        HttpStaticDataProcessor.this.getClass().getSimpleName()
                        + ":removing " + key + ": " + obj);
            }
            return obj;
        }

        @Override
        public Object put(Object key, Object value) {
            if (DEBUG_DP) {
                System.out.println(
                        HttpStaticDataProcessor.this.getClass().getSimpleName()
                        + ":adding " + key + ": " + value);
            }
            return super.put(key, value);
        }
    };
    private Task dataPipeTask;
    private transient Collection<P> assigned = Collections.synchronizedCollection(new HashSet<>());

    public HttpStaticDataProcessor() {
        super.setMatcher(new HttpMatcher.HttpMatcherComposite() {
            @Override
            public Collection<HttpMatcher> matchers() {
                return resources.keySet();
            }

            @Override
            public HttpMatcherComposite add(HttpMatcher... matchers) {
                return this;
            }
        });
    }

    public HttpStaticDataProcessor configureUseDataPipes(boolean use) {
        useDataPipes = use;
        return this;
    }

    public HttpStaticDataProcessor debug(boolean common, boolean dp) {
        DEBUG = common;
        DEBUG_DP = dp;
        return this;
    }

    public HttpStaticDataProcessor add(HttpResource... newResources) {
        if (newResources != null) {
            for (HttpResource resource : newResources) {
                if (resource != null && resource.path() != null) {
                    resources.put(new HttpMatcher(resource.path()), resource);
                }
            }
        }
        return this;
    }

    public HttpStaticDataProcessor resourceBundle(String localizables) {
        this.localizables = localizables;
        return this;
    }

    public HttpStaticDataProcessor addDefaultParameterResolvers() {
        return this.addParameterResolvers(
                new ParameterResolverSession(),
                new ParameterResolverUser(),
                new ParameterResolverApp()
        );
    }

    public HttpStaticDataProcessor addParameterResolvers(
            ParameterResolver... resolvers) {
        if (resolvers != null) {
            for (ParameterResolver pr : resolvers) {
                if (pr == null) {
                    continue;
                }
                String prefix = pr.getParametersPrefix();
                if (prefix == null) {
                    prefix = "";
                }
                ParameterResolver[] rs = this.resolvers.get(prefix);
                if (rs == null) {
                    this.resolvers.put(prefix, new ParameterResolver[]{pr});
                } else {
                    boolean found = false;
                    for (ParameterResolver r : rs) {
                        if (r.equals(pr)) {
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        rs = Arrays.copyOf(rs, rs.length + 1);
                        rs[rs.length - 1] = pr;
                        this.resolvers.put(prefix, rs);
                    }
                }
            }
        }
        return this;
    }

    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onDeassigned(P p, DI<?, P> di) {
        super.onDeassigned(p, di);
        assigned.remove(p);
    }

    @Override
    public void onAssigned(P p, DI<?, P> di) {
        assigned.add(p);
        super.onAssigned(p, di);
    }

    @Override
    public void setMatcher(HttpMatcher matcher) {
        // do nothing: this class provides own matcher...
    }

    @Override
    public void onHeaderLoaded(HttpData data) throws IOException {
        super.onHeaderLoaded(data);
        if (((HttpRequest) data).getResponse().isCompleted()) {
            return;
        }
        String path = ((HttpRequest) data).getQuery();
        HttpMatcher key = ((HttpMatcher.HttpMatcherComposite) getMatcher()).matching(new HttpMatcher(path));
        HttpResource resBase = resources.get(key);
        HttpResource res = resBase;
        if (res != null) {
            HttpResource subRes = res.find(path);
            if (res != subRes) {
                res = subRes;
            }
        }
        if (res != null) {
            // check if client-cached version has expired -> return 304 response...
            String modifiedSince = data.getHead().getHeader1(HttpData.HH_IF_MODIFIED_SINCE);
            long timestamp = resolveTimestamp(res, resBase);
            Long expires = resolveExpires(res, resBase);
            if (expires == null) {
                long timeOffset = System.currentTimeMillis() - timestamp;
                if (timeOffset < 0) {
                    timeOffset = 0;
                }
                if (res.path().contains(".")) {
                    String rp = res.path();
                    int idx = rp.lastIndexOf(".");
                    rp = rp.substring(idx);
                    if (rp.toLowerCase().equals(".html")) {
                        expires = timeOffset + 1000L * 60 * 5;
                    }
                }
                if (expires == null) {
                    expires = timeOffset + 1000L * 60 * 60;
                }
            }

            Replacement[] replacements = null;
            {
                String[] params = res.parameters();
                Map<byte[], byte[]> pvs = resolveParameters(data, res, resBase);
                if (pvs != null && !pvs.isEmpty()) {
                    if (DEBUG) {
                        System.out.println(getClass().getSimpleName() + ":REQ: " + ((HttpRequest) data).getQuery());
                    }
                    replacements = new Replacement[pvs.size()];
                    int off = 0;
                    for (Map.Entry<byte[], byte[]> entry : pvs.entrySet()) {
                        replacements[off++] = new Replacement(entry.getKey(), entry.getValue());
                        if (DEBUG) {
                            System.out.println("  '" + new String(entry.getKey(), "ISO-8859-1") + "' -> '" + new String(entry.getValue(), "ISO-8859-1"));
                        }
                    }
                } else {
                    long lastTimestamp = HttpData.fromHeaderDatetime(modifiedSince);
                    if (lastTimestamp != -1
                            && timestamp <= lastTimestamp
                            && timestamp != 0) {
                        do304(data, lastTimestamp, expires);
                        return;
                    }
                }
            }
            long resSize = res.size(data);
            if (resSize == UNKNOWN_SIZE) {
                final HttpResponse resp = ((HttpRequest) data).getResponse();
                final HttpResource respRes = res;
                final Replacement[] replace = replacements;
                resp.setHeader(HttpData.HH_CONTENT_TYPE, res.contentType(data));
                resp.addHeader(HttpData.HH_TRANSFER_ENCODING, HttpData.HTE_CHUNKED);
                if (!isPreventCacheing(data)) {
                    resp.setHeader(HttpData.HH_CACHE_CONTROL, HttpData.HCC_PUBLIC + ", max-age=" + expires / 1000 + ", must-revalidate");
                }
                //resp.setHeader(HttpData.HH_PRAGMA, null);
                resp.setHeader(HttpData.HH_DATE, HttpData.toHeaderDatetime(timestamp));
                resp.setHeader(HttpData.HH_LAST_MODIFIED, HttpData.toHeaderDatetime(timestamp));
                if (!isPreventCacheing(data)) {
                    resp.setHeader(HttpData.HH_EXPIRES, HttpData.toHeaderDatetime(timestamp + expires));
                }
                // disable compression of image files...
                if (res.contentType(data).toLowerCase().contains("image")) {
                    String s = resp.getHead().getHeader1(HttpData.HH_CONTENT_ENCODING);
                    if (s != null && HttpData.HTE_GZIP.equalsIgnoreCase(s)) {
                        resp.getHead().setHeader(HttpData.HH_CONTENT_ENCODING, null);
                    }
                }
                resp.onHeaderLoaded();
                if (data instanceof HttpRequest && useDataPipes) {
                    DataPipe dp = null;
                    if (respRes.requiresInitialization(data)) {
                        dp = new DataPipe((HttpRequest) data, respRes, replace);
                    } else {
                        dp = new DataPipe((HttpRequest) data, respRes.open(data, replace));
                    }
                    synchronized (dataPipes) {
                        dataPipes.put(data, dp);
                    }
                } else {
                    Runnable run = new Runnable() {
                        @Override
                        public void run() {
                            try {
                                InputStream is = respRes.open(data, replace);
                                Body body = resp.getBody();
                                byte[] buf = new byte[1024 * 4];
                                int c = 0;
                                while ((c = is.read(buf)) != -1) {
                                    long timeout = System.currentTimeMillis() + 1000 * 60;
                                    body.add(ByteBuffer.wrap(buf, 0, c));
                                    //body.add(ByteBuffer.wrap(Arrays.copyOf(buf, c)));
                                    while (body.size() > 0) {
                                        Thread.sleep(1);
                                        if (System.currentTimeMillis() > timeout) {
                                            throw new IOException("Buffer not read within specified timeout for " + resp);
                                        }
                                        if (resp.isCancelled()) {
                                            throw new IOException("Response cancelled." + resp);
                                        }
                                    }
                                }
                            } catch (IOException ioex) {
                                ioex.printStackTrace();
                            } catch (Throwable th) {
                                th.printStackTrace();
                            } finally {
                                resp.onLoaded();
                                //System.out.println(resp.getQuery() + ":  " + ((HttpRequest) data).getProperties().get("connection") + "\n  " + resp.toString().replace("\n", "\n  "));
                            }
                        }
                    };
                    runnables.put(data, run);
                }
            } else {
                byte[] buf = res.data(data, replacements);
                HttpResponse resp = ((HttpRequest) data).getResponse();
                resp.setHeader(HttpData.HH_CONTENT_TYPE, res.contentType(data));
                resp.setHeader(HttpData.HH_CONTENT_LENGTH, "" + buf.length);
                if (!isPreventCacheing(data)) {
                    resp.setHeader(HttpData.HH_CACHE_CONTROL, HttpData.HCC_PUBLIC + ", max-age=" + expires / 1000 + ", must-revalidate");
                }
                //resp.setHeader(HttpData.HH_PRAGMA, null);
                resp.setHeader(HttpData.HH_DATE, HttpData.toHeaderDatetime(timestamp));
                resp.setHeader(HttpData.HH_LAST_MODIFIED, HttpData.toHeaderDatetime(timestamp));
                if (!isPreventCacheing(data)) {
                    resp.setHeader(HttpData.HH_EXPIRES, HttpData.toHeaderDatetime(timestamp + expires));
                }
                resp.onHeaderLoaded();
                resp.getBody().add(buf);
                resp.onLoaded();
            }
        } else {
            this.onNoResource(data);
        }
    }

    public void onNoResource(HttpData data) throws IOException {
        do500(data, "text/plain; charset=utf-8", ("No resource to match " + ((HttpRequest) data).getHostURL() + ((HttpRequest) data).getQuery()).getBytes("UTF-8"));
    }

    @Override
    public boolean isPreventCacheing(HttpData data) {
        boolean r = super.isPreventCacheing(data);
        if (r && data instanceof HttpRequest && ((HttpRequest) data).getResponse() != null) {
            HttpResponse resp = ((HttpRequest) data).getResponse();
            String ct = resp.getHead().getHeader1(HttpData.HH_CONTENT_TYPE);
            if (ct != null && (ct.toLowerCase().contains("image") || ct.toLowerCase().contains("javascript"))) {
                r = false;
            }
        }
        return r;
    }

    public long resolveTimestamp(HttpResource res, HttpResource base) {
        long r = res.timestamp();
        String[] params = res.parameters();
        if (params != null) {
            for (String param : params) {
                if (param.contains("include ")) {
                    String rn = param.substring(param.indexOf(" ", param.indexOf("include ")));
                    if (rn.length() > 1) {
                        rn = rn.substring(0, rn.length() - 1).trim();
                    }
                    HttpResource ri = base.find(rn);
                    if (ri != null) {
                        r = Math.max(r, resolveTimestamp(ri, base));
                    }
                }
            }
        }
        return r;
    }

    @Override
    public Long evaluateExpires(HttpRequest req) {
        String path = req.getQuery();
        HttpMatcher key = ((HttpMatcher.HttpMatcherComposite) getMatcher()).matching(new HttpMatcher(path));
        HttpResource resBase = resources.get(key);
        HttpResource res = resBase;
        if (res != null) {
            HttpResource subRes = res.find(path);
            if (res != subRes) {
                res = subRes;
            }
        }
        if (res != null) {
            return resolveExpires(res, resBase);
        } else {
            return super.evaluateExpires(req);
        }
    }

    @Override
    public Long evaluateTimestamp(HttpRequest req) {
        String path = req.getQuery();
        HttpMatcher key = ((HttpMatcher.HttpMatcherComposite) getMatcher()).matching(new HttpMatcher(path));
        HttpResource resBase = resources.get(key);
        HttpResource res = resBase;
        if (res != null) {
            HttpResource subRes = res.find(path);
            if (res != subRes) {
                res = subRes;
            }
        }
        if (res != null) {
            return resolveTimestamp(res, resBase);
        } else {
            return super.evaluateTimestamp(req);
        }
    }

    public Long resolveExpires(HttpResource res, HttpResource base) {
        Long r = res.expires();
        String[] params = res.parameters();
        if (params != null) {
            for (String param : params) {
                if (param.contains("include ")) {
                    String rn = param.substring(param.indexOf(" ", param.indexOf("include ")));
                    if (rn.length() > 1) {
                        rn = rn.substring(0, rn.length() - 1).trim();
                    }
                    HttpResource ri = base.find(rn);
                    if (ri != null) {
                        Long exp = resolveExpires(ri, base);
                        if (exp != null) {
                            r = Math.min(r, exp);
                        }
                    }
                }
            }
        }
        return r;
    }

    public Map<byte[], byte[]> resolveParameters(HttpData data, HttpResource res, HttpResource base) throws IOException {
        String[] params = res.parameters();
        Map<byte[], byte[]> pvs = new LinkedHashMap<>();
        if (params != null) {
            if (base instanceof HttpResourceCollection) {
                HttpResourceCollection hrc = (HttpResourceCollection) base;
                pStarter = hrc.getParametersBounds()[0];
                pEnder = hrc.getParametersBounds()[1];
            }
            for (String param : params) {
                boolean resolved = false;
                if (!resolvers.isEmpty()) {
                    try {
                        // scoped/prefix
                        String pn = param.substring(pStarter.length(), param.length() - pEnder.length());
                        for (Entry<String, ParameterResolver[]> rs : resolvers.entrySet()) {
                            if (pn.startsWith(rs.getKey())) {
                                String n = pn.substring(rs.getKey().length());
                                for (ParameterResolver pr : rs.getValue()) {
                                    if (pr.canResolveParameter(data, n)) {
                                        String v = pr.resolveParameter(data, n);
                                        pvs.put(param.getBytes("UTF-8"), ("" + v).getBytes("UTF-8"));
                                        resolved = true;
                                        break;
                                    }
                                }
                            }
                            if (resolved) {
                                break;
                            }
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }

                if (!resolved) {
                    try {
                        if (param.contains("resource.name")) {
                            String name = res.path();
                            if (name.contains("/")) {
                                name = name.substring(name.lastIndexOf("/") + 1);
                            }
                            pvs.put(param.getBytes("UTF-8"), name.getBytes("UTF-8"));
                        } else if (param.contains("resource.path")) {
                            pvs.put(param.getBytes("UTF-8"), res.path().getBytes("UTF-8"));
                        } else if (param.contains("include ")) {
                            String rn = param.substring(param.indexOf(" ", param.indexOf("include ")));
                            if (rn.length() > 1) {
                                rn = rn.substring(0, rn.length() - 1).trim();
                            }
                            HttpResource ri = base.find(rn);
                            if (ri != null) {
                                Replacement[] replacements = null;
                                Map<byte[], byte[]> pvi = resolveParameters(data, ri, base);
                                if (pvi != null && !pvi.isEmpty()) {
                                    if (pvi != null && !pvi.isEmpty()) {
                                        replacements = new Replacement[pvi.size()];
                                        int off = 0;
                                        for (Map.Entry<byte[], byte[]> entry : pvi.entrySet()) {
                                            replacements[off++] = new Replacement(entry.getKey(), entry.getValue());
                                        }
                                    }
                                }
                                pvs.put(param.getBytes("UTF-8"), ri.data(data, replacements));
                            }
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            }
        }
        if (localizables != null && res.localizeable() != null && res.localizeable().length > 0) {
            HttpSession sess = ((HttpRequest) data).getHttpSession();
            ResourceBundle bundle = ResourceBundle.getBundle(localizables, sess.getLocale());
            String pStarter = "#{";
            String pEnder = "}";
            for (String l : res.localizeable()) {
                String n = l.substring(pStarter.length(), l.length() - pEnder.length());
                String v = n;
                try {
                    v = bundle.getString(n);
                } catch (MissingResourceException mrex) {
                    System.err.println(mrex);
                }
                pvs.put(l.getBytes("UTF-8"), (v != null) ? v.getBytes("UTF-8") : n.getBytes("UTF-8"));
            }
        }
        return pvs;
    }

    @Override
    public Runnable getRunnable(P provider, DI<ByteBuffer, P> data) {
        Runnable r = null;
        if (data instanceof DIHttpData) {
            HttpData http = ((DIHttpData) data).http(provider);
            r = fetchRunnable(http);
//            if (runnables.containsKey(http)) {
//                r = runnables.remove(http);
//            }
        }
        return r;
    }

    public Runnable fetchRunnable(HttpData http) {
        if (runnables.containsKey(http)) {
            return runnables.remove(http);
        }
        return null;
    }

    @Override
    public SERVICE_PROCESSING_STATE check(P provider, DI<ByteBuffer, P> data) throws IOException {
        if (data instanceof DIHttpData) {
            HttpData http = ((DIHttpData) data).http(provider);
            if (runnables.containsKey(http)) {
                return SERVICE_PROCESSING_STATE.needProcess;
            }
        }
        return super.check(provider, data); //To change body of generated methods, choose Tools | Templates.
    }

    public Replacement[] getReplacements(P provider, HttpData data, HttpResource resource) {
        return null;
    }

    static AtomicInteger NEXT_TASK_ID = new AtomicInteger();

    @Override
    public List<Task> getTasks(TaskPhase... phases) {
        if (useDataPipes && (dataPipeTask == null || dataPipeTask.getCompleted() != 0) && !assigned.isEmpty()) {
            if (!dataPipes.isEmpty()) synchronized (this) {
                if (dataPipeTask == null) {
                    dataPipeTask = new Task(new Runnable() {
                        byte[] buf = new byte[1024 * 4];

                        @Override
                        public void run() {
                            String oldName = Thread.currentThread().getName();
                            String paths = "";
                            for (HttpMatcher m : resources.keySet()) {
                                paths += m.getPath() + ";";
                            }
                            if (paths.length() > 30) {
                                paths = paths.substring(0, 27) + "...";
                            }
                            Thread.currentThread().setName(HttpStaticDataProcessor.this.getClass().getSimpleName() + ":pipe[" + NEXT_TASK_ID.getAndIncrement() + "]: " + paths);
                            try {
                                // exit task once no actions during 5 sec.
                                //long timeout = System.currentTimeMillis() + 100;//0*5;
                                while (!assigned.isEmpty()) {//true) {
                                    while (!dataPipes.isEmpty()) {
                                        DataPipe[] dps = null;
                                        synchronized (dataPipes) {
                                            dps = dataPipes.values().toArray(new DataPipe[dataPipes.size()]);
                                        }
                                        for (DataPipe dp : dps) {
                                            try {
                                                if (dp.cycles == 0) {
                                                    DataPipeStatistics.add(dp.req, dp.size, System.currentTimeMillis() - dp.started, null);
                                                    if (dp.getInitializer() != null) {
                                                        initializers.add(dp.getInitializer());
                                                    }
                                                }
                                                int c = dp.runCycle(buf);
                                                if (c == -1) {
                                                    synchronized (dataPipes) {
                                                        dataPipes.remove(dp.req);
                                                    }
                                                    DataPipeStatistics.done(dp.req, dp.size, dp.completed - dp.started, null);
                                                }
                                            } catch (Throwable th) {
                                                synchronized (dataPipes) {
                                                    dp.req.getResponse().onLoaded();
                                                    dataPipes.remove(dp.req);
                                                    DataPipeStatistics.done(dp.req, dp.size, System.currentTimeMillis() - dp.started, th);
                                                }
                                            }
                                        }
                                        // update timeout if some activity...
                                        //timeout = System.currentTimeMillis() + 100;//0*5;
                                    }
//                            if (System.currentTimeMillis() >= timeout) {
//                                break;
//                            }
                                    try {
                                        Thread.sleep(10);
                                    } catch (Throwable th) {
                                        break;
                                    }
                                }
                            } finally {
                                dataPipeTask = null;
                                Thread.currentThread().setName(oldName);
                            }
                        }
                    }, Task.TaskPriority.high);
                    return Collections.singletonList(dataPipeTask);
                }
            }
        } else if (!initializers.isEmpty()) {
            List<Task> r = new ArrayList<>();
            Runnable[] rr = null;
            synchronized (initializers) {
                rr = initializers.toArray(new Runnable[initializers.size()]);
                initializers.clear();
            }
            for (Runnable ru : rr) {
                if (ru != null) {
                    r.add(new Task(ru));
                }
            }
            return r;
        }
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append('{');
        sb.append("DEBUG=" + DEBUG);
        sb.append(", DEBUG_DP=" + DEBUG_DP);
        sb.append(", pStarter=" + pStarter);
        sb.append(", pEnder=" + pEnder);
        sb.append(", useDataPipes=" + useDataPipes);
        if (runnables != null) {
            sb.append(", runnables=" + runnables.size());
        }
        if (initializers != null) {
            sb.append(", initializers=" + initializers.size());
        }
        if (localizables != null) {
            sb.append(", localizables=" + localizables);
        }
        if (resolvers != null) {
            sb.append(", resolvers=" + resolvers.size());
        }
        if (dataPipes != null) {
            sb.append(", dataPipes=" + dataPipes.size());
        }
        if (dataPipeTask != null) {
            sb.append(", dataPipeTask=" + dataPipeTask);
        }
        if (assigned != null) {
            sb.append(", assigned=" + assigned.size());
        }
        if (resources != null) {
            sb.append("\n  resources[" + resources.size() + "]:");
            for (Entry<HttpMatcher, HttpResource> e : resources.entrySet()) {
                sb.append("\n    " + e.getKey().toString().replace("\n", "\n    "));
                sb.append("\n      " + e.getValue().toString().replace("\n", "\n      "));
            }
        }
        sb.append('\n');
        sb.append('}');
        return sb.toString();
    }

    /**
     * Parameter resolver provides resolution of parameters within given name
     * space (prefix).
     *
     * It exposes name space (prefix), available parameters (may vary depending
     * on context (data)).
     *
     * Parameter should be resolved only if it can be resolved.I
     */
    public static interface ParameterResolver {

        String getParametersPrefix();

        Collection<String> getParameterNames(HttpData data, boolean withPrefix);

        boolean canResolveParameter(HttpData data, String parameterName);

        String resolveParameter(HttpData data, String parameterName);
    }

    public static class ParameterResolverRequest implements ParameterResolver {

        @Override
        public String getParametersPrefix() {
            return "request.";
        }

        @Override
        public Collection<String> getParameterNames(HttpData data, boolean withPrefix) {
            Collection<String> r = new HashSet<>();
            r.add("hostURL");
            r.add("host");
            r.add("headers");

            List<String> ns = new ArrayList<>(r.size());
            HttpRequest req = (HttpRequest) data;
            for (String hn : req.getHead().getHeaders().keySet()) {
                ns.add("header." + hn);
            }

            ns.addAll(r);
            Collections.sort(ns);
            if (withPrefix) {
                String pfx = getParametersPrefix();
                for (int i = 0; i < ns.size(); i++) {
                    ns.set(i, pfx + ns.get(i));
                }
            }

            return ns;
        }

        @Override
        public boolean canResolveParameter(HttpData data, String parameterName) {
            return data instanceof HttpRequest && parameterName != null;
        }

        @Override
        public String resolveParameter(HttpData data, String parameterName) {
            if (canResolveParameter(data, parameterName)) {
                HttpRequest req = (HttpRequest) data;
                String[] ss = parameterName.split("\\.");

                if ("hostURL".equals(parameterName)) {
                    return "" + req.getHostURL();
                } else if ("host".equals(parameterName)) {
                    return "" + req.getHead().getHeader1(parameterName);
                } else if ("headers".equals(parameterName)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[");
                    for (String s : req.getHead().getHeaders().keySet()) {
                        if (sb.length() > 1) {
                            sb.append(',');
                        }
                        sb.append('"');
                        sb.append(s.replace("\"", "\\\""));
                        sb.append('"');
                    }
                    sb.append("]");
                    return sb.toString();
                } else if (ss[0].equals("header") && ss.length == 2) {
                    String[] si = req.getHead().getHeader(ss[1]);
                    if (si == null) {
                        return "";
                    } else if (si.length == 1) {
                        return si[0];
                    } else {
                        StringBuilder sb = new StringBuilder();
                        sb.append("[");
                        for (String s : si) {
                            if (sb.length() > 1) {
                                sb.append(',');
                            }
                            sb.append('"');
                            sb.append(s.replace("\"", "\\\""));
                            sb.append('"');
                        }
                        sb.append("]");
                        return sb.toString();
                    }
                }
            }
            return null;
        }
    }

    public static class ParameterResolverSession implements ParameterResolver {

        HttpSession session(HttpData data) {
            return (data instanceof HttpRequest)
                    ? ((HttpRequest) data).getHttpSession()
                    : null;
        }

        @Override
        public String getParametersPrefix() {
            return "session.";
        }

        @Override
        public Collection<String> getParameterNames(HttpData data, boolean withPrefix) {
            Collection<String> r = new HashSet<>();
            r.add("id");

            HttpSession session = session(data);
            if (session != null) {
                r.addAll(session.getProperties().keySet());
            }

            List<String> ns = new ArrayList<>(r.size());
            ns.addAll(r);
            Collections.sort(ns);
            if (withPrefix) {
                String pfx = getParametersPrefix();
                for (int i = 0; i < ns.size(); i++) {
                    ns.set(i, pfx + ns.get(i));
                }
            }

            return ns;
        }

        @Override
        public boolean canResolveParameter(HttpData data, String parameterName) {
            return session(data) instanceof HttpSession && parameterName != null;
        }

        @Override
        public String resolveParameter(HttpData data, String parameterName) {
            if (canResolveParameter(data, parameterName)) {
                HttpSession sess = session(data);

                if ("id".equals(parameterName)) {
                    return "" + sess.getId();
                } else if ("locale".equals(parameterName)) {
                    return "" + (sess.getLocale() != null ? sess.getLocale().getLanguage() : "");
                } else {
                    Object v = sess.getProperties().get(parameterName);
                    if (v != null) {
                        return "" + v;
                    }
                }
            }
            return null;
        }
    }

    public static class ParameterResolverUser implements ParameterResolver {

        static Collection<String> names = new HashSet<String>() {
            {
                add("id");
                add("name");
                add("domain");
                add("roles");
            }
        };

        HttpUser user(HttpData data) {
            return data instanceof HttpRequest && ((HttpRequest) data).getHttpSession() != null
                    ? ((HttpRequest) data).getHttpSession().getUser()
                    : null;
        }

        @Override
        public String getParametersPrefix() {
            return "user.";
        }

        @Override
        public Collection<String> getParameterNames(HttpData data, boolean withPrefix) {
            Collection<String> r = new HashSet<>();
            r.addAll(names);

            HttpUser user = user(data);
            if (user != null) {
                r.addAll(user.getProperties().keySet());
            }

            List<String> ns = new ArrayList<>(r.size());
            ns.addAll(r);
            Collections.sort(ns);
            if (withPrefix) {
                String pfx = getParametersPrefix();
                for (int i = 0; i < ns.size(); i++) {
                    ns.set(i, pfx + ns.get(i));
                }
            }

            return ns;
        }

        @Override
        public boolean canResolveParameter(HttpData data, String parameterName) {
            if (user(data) instanceof HttpUser && parameterName != null) {
                if (names.contains(parameterName)) {
                    return true;
                }
                HttpUser user = user(data);
                if (user.getProperties().containsKey(parameterName)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String resolveParameter(HttpData data, String parameterName) {
            if (canResolveParameter(data, parameterName)) {
                HttpUser user = user(data);
                if ("id".equals(parameterName)) {
                    return user.getId();
                } else if ("name".equals(parameterName)) {
                    return user.getName();
                } else if ("domain".equals(parameterName)) {
                    return user.getDomainName();
                } else if ("roles".equals(parameterName)) {
                    StringBuilder sb = new StringBuilder();
                    if (user.getRoles() != null) {
                        for (String s : user.getRoles()) {
                            if (sb.length() > 0) {
                                sb.append(",");
                            }
                            sb.append(s);
                        }
                    }
                    return sb.toString();
                } else {
                    Object v = (user != null) ? user.getProperties().get(parameterName) : "";
                    if (v != null) {
                        return "" + v;
                    }
                }
            }
            return null;
        }

    }

    public static class ParameterResolverApp implements ParameterResolver {

        HttpApplication app(HttpData data) {
            return data instanceof HttpRequest && ((HttpRequest) data).getHttpSession() != null
                    ? ((HttpRequest) data).getHttpSession().getApplication()
                    : null;
        }

        @Override
        public String getParametersPrefix() {
            return "app.";
        }

        @Override
        public boolean canResolveParameter(HttpData data, String parameterName) {
            return app(data) instanceof HttpApplication && parameterName != null;
        }

        @Override
        public Collection<String> getParameterNames(HttpData data, boolean withPrefix) {
            Collection<String> r = new HashSet<>();
            r.add("name");
            r.add("root");

            HttpApplication app = app(data);
            if (app != null) {
                r.addAll(app.getProperties().keySet());
            }

            List<String> ns = new ArrayList<>(r.size());
            ns.addAll(r);
            Collections.sort(ns);
            if (withPrefix) {
                String pfx = getParametersPrefix();
                for (int i = 0; i < ns.size(); i++) {
                    ns.set(i, pfx + ns.get(i));
                }
            }

            return ns;
        }

        @Override
        public String resolveParameter(HttpData data, String parameterName) {
            if (canResolveParameter(data, parameterName)) {
                HttpApplication app = app(data);

                if ("name".equals(parameterName)) {
                    return "" + app.getName();
                } else if ("root".equals(parameterName)) {
                    return "" + app.getRoot();
                } else {
                    Object v = app.getProperties().get(parameterName);
                    if (v != null) {
                        return "" + v;
                    }
                }
            }
            return null;
        }
    }

    /**
     * Data pipe
     */
    public static class DataPipe {

        static AtomicInteger NEXT_ID = new AtomicInteger();
        final int id = NEXT_ID.getAndIncrement();
        HttpRequest req;
        InputStream is;
        long timeout = System.currentTimeMillis() + DEFAULT_DATA_PIPE_TIMEOUT;
        long size;
        long started = System.currentTimeMillis();
        long completed = 0;
        int cycles = 0;
        Runnable initializer;
        Throwable error;

        public DataPipe(HttpRequest req, InputStream is) {
            this.req = req;
            this.is = is;
            //System.out.println("DataPipe(" + req.getQuery() + ") start without initializer");
            DataPipeStatistics.init(req, "start without initializer", null);
        }

        public DataPipe(HttpRequest req, final HttpResource respRes, final Replacement... replace) {
            this.req = req;
            initializer = new Runnable() {
                @Override
                public void run() {
                    try {
                        //System.out.println("DataPipe.initializer(" + req.getQuery() + ") start");
                        DataPipeStatistics.init(req, "start", null);
                        is = respRes.open(req, replace);
                    } catch (IOException ioex) {
                        error = ioex;
                        DataPipeStatistics.init(req, "failed", ioex);
                        //System.out.println("DataPipe.initializer(" + req.getQuery() + ") I/O ERROR: " + ioex);
                    } catch (Throwable th) {
                        error = th;
                        DataPipeStatistics.init(req, "failed", th);
                        //System.out.println("DataPipe.initializer(" + req.getQuery() + ") I/O ERROR: " + th);
                    } finally {
                        DataPipeStatistics.init(req, "end", null);
                        //System.out.println("DataPipe.initializer(" + req.getQuery() + ") end");
                        initializer = null;
                    }
                }
            };
            //this.is = is;
        }

        public Runnable getInitializer() {
            return initializer;
        }

        public int runCycle(byte[] buf) throws IOException {
            cycles++;
            if (getInitializer() != null) {
                timeout = System.currentTimeMillis() + DEFAULT_DATA_PIPE_TIMEOUT;
                return 0;
            } else if (error != null) {
                if (error instanceof IOException) {
                    throw (IOException) error;
                } else {
                    throw new IOException(error);
                }
            } else if (is == null) {
                // TODO: throw IOExeption???
                return -1;
            }
            HttpResponse resp = req.getResponse();
            Body body = req.getResponse().getBody();
            //System.out.println("rc[" + req.getQuery() + "]: sentSize=" + resp.getSentSize() + ", outputSize=" + resp.getOutputSize() + ", body.size=" + body.size());
            if (body.size() > 0) {
                return 0;
            }
            if (System.currentTimeMillis() > timeout) {
                throw new IOException("Buffer not read within specified timeout for " + req.getResponse());
            }
            timeout = System.currentTimeMillis() + DEFAULT_DATA_PIPE_TIMEOUT;

            try {
//                    if (is instanceof InputStreamReplacement) {
//                        ((InputStreamReplacement) is).DEBUG = true;
//                    }
                int c = is.read(buf);
                if (c > 0) {
                    size += c;
                    //System.out.println("[" + System.currentTimeMillis() + "][" + Thread.currentThread().getName() + "].runCycle[" + id + ", " + req.getQuery() + ", " + cycles + ", " + c + ", " + size + "] " + new String(buf, 0, c, "ISO-8859-1"));
                    body.add(ByteBuffer.wrap(buf, 0, c));
                } else if (c == -1) {
                    //System.out.println("[" + System.currentTimeMillis() + "][" + Thread.currentThread().getName() + "].runCycle[" + id + ", " + req.getQuery() + ", " + cycles + ", " + c + ", " + size + "] close response");
                    req.getResponse().onLoaded();
                    completed = System.currentTimeMillis();
                }
                return c;
            } catch (Throwable th) {
                th.printStackTrace();
                if (th instanceof IOException) {
                    throw (IOException) th;
                }
                throw new IOException(th);
            }
        }

        @Override
        public String toString() {
            return "DataPipe{" + "req=" + (req != null ? req.getQuery() : "<none>") + ", is=" + is + ", timeout=" + timeout + ", size=" + size + ", started=" + started + ", completed=" + completed + ", cycles=" + cycles + ", initializer=" + initializer + ", error=" + error + '}';
        }
    }

    public static class DataPipeStatistics {

        public static PrintStream out = System.out;

        public static void init(HttpRequest req, String suffix, Throwable error) {
            if (out != null) {
                out.println(DataPipeStatistics.class.getSimpleName() + ".init[" + getConnection(req) + "]: " + req.getHead().getHeader1(HttpData.HH_HOST) + ((suffix != null) ? " " + suffix : "") + ((error != null) ? "ERROR: " + error : ""));
            }
        }

        public static void add(HttpRequest req, long responseSize, long responseTime, Throwable error) {
            if (out != null) {
                out.println(DataPipeStatistics.class.getSimpleName() + ".add [" + getConnection(req) + "]: " + req.getHead().getHeader1(HttpData.HH_HOST) + ":" + req.getQuery() + ":" + req.getResponse().getResponseCode() + ": " + responseSize + "/" + req.getResponse().getOutputSize() + ((req.getResponse().isSent() ? "(completed)" : "(wip)")) + " in " + responseTime / 1000f + "s" + ((error != null) ? "ERROR: " + error : ""));
            }
        }

        public static void done(HttpRequest req, long responseSize, long responseTime, Throwable error) {
            if (out != null) {
                out.println(DataPipeStatistics.class.getSimpleName() + ".done[" + getConnection(req) + "]: " + req.getHead().getHeader1(HttpData.HH_HOST) + ":" + req.getQuery() + ":" + req.getResponse().getResponseCode() + ": " + responseSize + "/" + req.getResponse().getOutputSize() + ((req.getResponse().isSent() ? "(completed)" : "(wip)")) + " in " + responseTime / 1000f + "s" + ((error != null) ? "ERROR: " + error : ""));
            }
        }

        public static String getConnection(HttpRequest req) {
            Object conn = req.getProperties().get("connection");
            return (conn != null) ? "" + conn : "";
        }
    }
}
