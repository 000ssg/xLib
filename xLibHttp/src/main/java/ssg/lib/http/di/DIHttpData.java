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
package ssg.lib.http.di;

import ssg.lib.http.base.HttpData;
import ssg.lib.http.base.HttpRequest;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.di.base.BaseDI;

/**
 * Provides DI for HttpService operations. Keeps track of providers with "new"
 * requests.
 *
 * Requests for "new" providers are checked by HttpDataProcessor to ensure
 * request-response life-cycle events.
 *
 * @author 000ssg
 */
public class DIHttpData<P extends Channel> extends BaseDI<ByteBuffer, P> {

    Map<P, HttpData> httpData = new HashMap<>();
    Collection<P> isNew = new HashSet<>();

    public DIHttpData() {
    }

    public HttpData addHttp(P provider, HttpData request) {
        if (httpData.get(provider) == null) {
            httpData.put(provider, request);
            isNew.add(provider);
            return request;
        }
        return null;
    }

    public void registerHttp(P provider, HttpData request) {
        httpData.put(provider, request);
        isNew.add(provider);
    }

    public void replaceHttp(P provider, HttpData request) {
        httpData.put(provider, request);
        isNew.add(provider);
    }

    public void unregisterHttp(P provider) {
        if (httpData.containsKey(provider)) {
            onCompleted(provider, httpData.remove(provider));
        }
        isNew.remove(provider);
    }

    public HttpData http(P provider) {
        return httpData.get(provider);
    }

    public boolean isNew(P provider) {
        return isNew.contains(provider);
    }

    public void notNew(P provider) {
        isNew.remove(provider);
    }

    public void onCompleted(P provider, HttpData data) {
    }

    @Override
    public void delete(P provider) throws IOException {
        if (provider != null) {
            HttpData http = http(provider);
            if (http != null) {
                http.cancel();
                if (http instanceof HttpRequest) {
                    HttpRequest req = (HttpRequest) http;
                    if (req.getResponse() != null && !req.getResponse().isCancelled()) {
                        req.getResponse().cancel();
                    }
                }
            }
        }
        super.delete(provider);

    }

    @Override
    public long size(Collection<ByteBuffer>... data) {
        return BufferTools.getRemaining(data);
    }

    @Override
    public void consume(P provider, Collection<ByteBuffer>... data) throws IOException {
        HttpData http = http(provider);

        if (http == null) {
            return;
        }

        http.add(data);

        if (http instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) http;
            if (req.isDone()) {
                unregisterHttp(provider);
            }
        }
    }

    @Override
    public List<ByteBuffer> produce(P provider) throws IOException {
        List<ByteBuffer> r = null;
        HttpData http = http(provider);
        if (http == null) {
        } else {
            r = http.get();

            if (http instanceof HttpRequest) {
                HttpRequest req = (HttpRequest) http;
                if (req.isDone()) {
                    unregisterHttp(provider);
                }
            }
        }
        return r;
    }
}
