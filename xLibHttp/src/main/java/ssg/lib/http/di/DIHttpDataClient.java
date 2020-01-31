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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author 000ssg
 */
public class DIHttpDataClient<P extends Channel> extends DIHttpData<P> {

    Map<P, List<HttpData>> requests = new LinkedHashMap<>();

    public boolean isClosed(P provider) {
        return provider == null || !provider.isOpen();
    }

    @Override
    public List<ByteBuffer> produce(P provider) throws IOException {
        HttpData http = http(provider);
        if (http == null || (http instanceof HttpRequest && ((HttpRequest) http).isDone())) {
            unregisterHttp(provider);
        }
        return super.read(provider);
    }

    @Override
    public HttpData addHttp(P provider, HttpData request) {
        if (provider != null) {
            //provider = ensureProvider(provider);
        }

        if (provider != null && httpData.get(provider) == null) {
            httpData.put(provider, request);
            isNew.add(provider);
            return request;
        } else if (isClosed(provider)) {
            // cannot add to closed provider...I
        } else {
            {//synchronized (requests) {
                List<HttpData> lst = requests.get(provider);
                if (lst == null) {
                    lst = new ArrayList<>();
                    requests.put(provider, lst);
                }
                lst.add(request);
                return request;
            }
        }
        return null;
    }

    @Override
    public void unregisterHttp(P provider) {
//        HttpData http = http(provider);
//        if (http != null) {
//            System.out.println("Will unregister " + (((HttpRequest) http).isDone() ? "done" : "undone") + " request for " + ((HttpRequest) http).getQuery());
//        }
        super.unregisterHttp(provider);
        List<HttpData> lst = requests.get(provider);
        if (lst != null && !lst.isEmpty()) {
//            System.out.println("Will add request from quesue of " + lst.size() + " requests...");
            HttpData req = lst.remove(0);
            httpData.put(provider, req);
            isNew.add(provider);
        }
    }

    @Override
    public void delete(P provider) throws IOException {
        if (provider != null) {
            if (requests.containsKey(provider)) {
                requests.remove(provider);
            }
        }
        super.delete(provider);
    }

    /**
     *
     * @param oldProvider
     * @param newProvider
     */
    public void toProvider(P oldProvider, P newProvider) {
        if (oldProvider != null) {
            //oldProvider = ensureProvider(oldProvider);
        }
        if (newProvider != null) {
            //newProvider = ensureProvider(newProvider);
        }
        if (oldProvider != newProvider) {
            // move null-based requests to default provider requests queueu
            synchronized (requests) {
                List<HttpData> lst = requests.remove(oldProvider);
                if (lst != null) {
                    List<HttpData> lst2 = requests.get(newProvider);
                    if (lst2 != null) {
                        lst2.addAll(lst);
                    } else {
                        requests.put(newProvider, lst);
                    }
                }
            }
        }
    }

}
