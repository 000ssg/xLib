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
package ssg.lib.http;

import ssg.lib.http.di.DIHttpData;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.base.HttpEventListener;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.http.base.HttpResponse;
import ssg.lib.http.base.MultipartBody;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.List;
import ssg.lib.common.CommonTools;
import ssg.lib.di.DI;
import ssg.lib.http.base.HttpContext;
import ssg.lib.service.DataProcessor;
import ssg.lib.service.DataProcessor.HST;
import ssg.lib.service.SERVICE_PROCESSING_STATE;

/**
 *
 * @author 000ssg
 * @param <P>
 */
public class HttpDataProcessor<P extends Channel> implements DataProcessor<P>, HttpEventListener {

    private HttpMatcher matcher;
    HST processingScope = HST.process;
    private boolean preventCacheing = false;

    public HttpDataProcessor() {
    }

    public HttpDataProcessor(HttpMatcher matcher) {
        this.matcher = matcher;
    }

    public HttpDataProcessor(URI uri, String... methods) {
        matcher = new HttpMatcher(uri.toString());
        matcher.methods = methods;
    }

    public HttpDataProcessor(String uri, String... methods) {
        matcher = new HttpMatcher(uri);
        matcher.methods = methods;
    }

    public <T extends HttpDataProcessor> T noCacheing() {
        setPreventCacheing(true);
        return (T) this;
    }

    @Override
    public void onAssigned(P p, DI<?, P> di) {
        // no default actions on assign/deassign
    }

    @Override
    public void onDeassigned(P p, DI<?, P> di) {
        // no default actions on assign/deassign
    }

    @Override
    public HST probe(P provider, DI<ByteBuffer, P> data) {
        HttpData http = http(provider, data);
        if (http != null) {
            HttpContext ctx = http.getContext();
            HttpMatcher parent = null;
            if (matcher!=null && !matcher.absolutePath && ctx instanceof HttpSession && ((HttpSession) ctx).getApplication() != null) {
                parent = ((HttpSession) ctx).getApplication().getMatcher();
            }
            if (matcher == null) {
                int a = 0;
            } else if (matcher.match(parent, http.getMatcher()) > 0) {
                return processingScope;
            }
        }
        return HST.none;
    }

    @Override
    public Runnable getRunnable(P provider, DI<ByteBuffer, P> data) {
        return null;
    }

    @Override
    public List<Task> getTasks(TaskPhase... phases) {
        return null;
    }

    @Override
    public SERVICE_PROCESSING_STATE check(P provider, DI<ByteBuffer, P> data) throws IOException {
        HttpData http = http(provider, data);
        if (http != null) {
            if (data instanceof DIHttpData) {
                DIHttpData pdiData = (DIHttpData) data;
                if (pdiData.isNew(provider)) {
                    pdiData.notNew(provider);

                    http.addHttpEventListener(this);

                    if (http.isHeaderLoaded()) {
                        onHeaderLoaded(http);
                    }
                    if (http.isCompleted()) {
                        onCompleted(http);
                    }
                }
            }

            if (http.isCompleted() && http.isSent()) {
                return SERVICE_PROCESSING_STATE.OK;
            } else if (http.hasProcessors()) {
                return SERVICE_PROCESSING_STATE.needProcess;
            } else {
                return SERVICE_PROCESSING_STATE.preparing;
            }
        } else {
            return SERVICE_PROCESSING_STATE.failed;
        }
    }

    public HttpData http(P provider, DI<ByteBuffer, P> data) {
        return (data instanceof DIHttpData) ? ((DIHttpData) data).http(provider) : null;
    }

    /**
     * @return the matcher
     */
    public HttpMatcher getMatcher() {
        return matcher;
    }

    /**
     * @param matcher the matcher to set
     */
    public void setMatcher(HttpMatcher matcher) {
        this.matcher = matcher;
    }

    /**
     * Cache/304 response support
     *
     * @param req
     * @return
     */
    public Long evaluateTimestamp(HttpRequest req) {
        return (req != null && req.isHeaderLoaded()) ? System.currentTimeMillis() : null;
    }

    /**
     * Cache/304 response support: can return time (ms) relative to now for
     * resource expiration. Defaults to 1h
     *
     * @param req
     * @return
     */
    public Long evaluateExpires(HttpRequest req) {
        return 1000L * 60 * 60 * 24;
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////// HttpEventListener
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void onHeaderLoaded(HttpData data) throws IOException {
        String modifiedSince = data.getHead().getHeader1(HttpData.HH_IF_MODIFIED_SINCE);
        Long timestamp = evaluateTimestamp(((HttpRequest) data));
        Long expires = evaluateExpires((HttpRequest) data);

        if (modifiedSince != null && timestamp != null && !isPreventCacheing(data)) {
            long lastTimestamp = HttpData.fromHeaderDatetime(modifiedSince);
            if (lastTimestamp != -1 && timestamp <= lastTimestamp) {
                do304(data, lastTimestamp, expires);
            }
        }
    }

    /**
     * Compose HTTP response 304: No changes
     *
     * @param data
     * @param timestamp
     * @param expires
     * @throws IOException
     */
    public void do304(HttpData data, Long timestamp, Long expires) throws IOException {
        HttpResponse resp = ((HttpRequest) data).getResponse();
        resp.setResponseCode(304, "No changes");
        resp.setHeader(HttpData.HH_CACHE_CONTROL,
                HttpData.HCC_PUBLIC
                + ((expires != null) ? ", max-age=" + expires / 1000 : "")
                + ", must-revalidate");
        resp.setHeader(HttpData.HH_DATE, HttpData.toHeaderDatetime(timestamp));
        resp.setHeader(HttpData.HH_LAST_MODIFIED, HttpData.toHeaderDatetime(timestamp));
        if (expires != null) {
            resp.setHeader(HttpData.HH_EXPIRES, HttpData.toHeaderDatetime(timestamp + expires));
        }
        resp.onHeaderLoaded();
        resp.onLoaded();
    }

    /**
     * Compose HTTP response 302: Redirect
     *
     * @param data
     * @param timestamp
     * @param expires
     * @throws IOException
     */
    public void do302(HttpData data, String newLocation) throws IOException {
        HttpResponse resp = ((HttpRequest) data).getResponse();
        resp.setResponseCode(302, "Found");
        resp.setHeader(HttpData.HH_LOCATION, newLocation);
        resp.setHeader(HttpData.HH_CONTENT_LENGTH, "0");
        resp.onHeaderLoaded();
        resp.onLoaded();
    }

    /**
     * Compose trivial HTTP response 200
     *
     * @param data
     * @param timestamp
     * @param expires
     * @throws IOException
     */
    public void do200(HttpData data, String contentType, byte[] body) throws IOException {
        HttpResponse resp = ((HttpRequest) data).getResponse();
        resp.setResponseCode(200, "OK");
        if (contentType != null) {
            resp.setHeader(HttpData.HH_CONTENT_TYPE, contentType);
        }
        resp.setHeader(HttpData.HH_CONTENT_LENGTH, "" + ((body != null) ? body.length : 0));
        resp.onHeaderLoaded();
        resp.getBody().add(body);
        resp.onLoaded();
    }

    /**
     * Compose trivial HTTP response 500
     *
     * @param data
     * @param timestamp
     * @param expires
     * @throws IOException
     */
    public void do500(HttpData data, String contentType, byte[] body) throws IOException {
        HttpResponse resp = ((HttpRequest) data).getResponse();
        resp.setResponseCode(500, "Server error");
        if (contentType != null) {
            resp.setHeader(HttpData.HH_CONTENT_TYPE, contentType);
        }
        resp.setHeader(HttpData.HH_CONTENT_LENGTH, "" + ((body != null) ? body.length : 0));
        resp.onHeaderLoaded();
        resp.getBody().add(body);
        resp.onLoaded();
    }

    @Override
    public void onCompleted(HttpData data) throws IOException {
    }

    @Override
    public void onHeaderSent(HttpData data) throws IOException {
    }

    @Override
    public void onPrepareSend(HttpData data) throws IOException {
    }

    @Override
    public void onSent(HttpData data) throws IOException {
    }

    @Override
    public OutputStream onMutlipartFile(HttpData data, MultipartBody.Part part, File folder, String fileName) throws IOException {
        return null;
    }

    public static String[][] initTextParameters(String[][] startends, String encoding, InputStream is) throws IOException {
        String[][] parameters = null;
        if (encoding == null) {
            encoding = "UTF-8";
        }
        byte[][][] startend = new byte[startends.length][][];
        for (int i = 0; i < startend.length; i++) {
            startend[i] = new byte[][]{
                startends[i][0].getBytes(encoding),
                startends[i][1].getBytes(encoding)
            };
        }
        byte[][][] bps = CommonTools.scanUniqueSubsequences(startend, is);
        parameters = new String[bps.length][];
        for (int j = 0; j < bps.length; j++) {
            if (bps[j] != null && bps[j].length > 0) {
                parameters[j] = new String[bps[j].length];
                for (int i = 0; i < bps[j].length; i++) {
                    parameters[j][i] = new String(bps[j][i], encoding);
                }
            } else {
                parameters[j] = new String[0];
            }
        }
        return parameters;
    }

    /**
     * @return the preventCacheing
     */
    public boolean isPreventCacheing(HttpData data) {
        return preventCacheing;
    }

    /**
     * @param preventCacheing the preventCacheing to set
     */
    public void setPreventCacheing(boolean preventCacheing) {
        this.preventCacheing = preventCacheing;
    }
}
