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
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.Collections;
import java.util.List;
import ssg.lib.di.DI;
import ssg.lib.http.HttpDataProcessor;
import ssg.lib.http.HttpMatcher;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.http.base.HttpResponse;
import ssg.lib.service.SERVICE_PROCESSING_STATE;

/**
 *
 * @param <P>
 */
public class HttpEchoDataProcessor<P extends Channel> extends HttpDataProcessor<P> {

    public HttpEchoDataProcessor() {
        super(new HttpMatcher("/"));
    }

    public HttpEchoDataProcessor(HttpMatcher matcher) {
        super(matcher);
    }

    public HttpEchoDataProcessor(URI uri, String... methods) {
        super(uri, methods);
    }

    @Override
    public SERVICE_PROCESSING_STATE check(P provider, DI<ByteBuffer, P> data) throws IOException {
        HttpData http = http(provider, data);
        if (http instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) http;
            if (req.isCompleted()) {
                try {
                    onCompleted(req);
                } catch (IOException ioex) {
                    ioex.printStackTrace();
                }
            }
        }
        SERVICE_PROCESSING_STATE pst = super.check(provider, data);
        return pst;
    }

    @Override
    public void onCompleted(HttpData data) throws IOException {
        if (data instanceof HttpRequest) {
            HttpRequest http = (HttpRequest) data;
            if (http.getResponse() != null && http.getResponse().isCompleted()) {
                // already done...
                int a = 0;
            } else {
                String text = echo(http);
                HttpResponse response = (http.getResponse() != null) ? http.getResponse() : new HttpResponse(http);
                if (!response.isHeaderLoaded()) {
                    response.setResponseCode(200, null);
                    response.setHeader("Content-Type", "text;charset=UTF-8");
                    response.getBody().add(ByteBuffer.wrap(text.getBytes("UTF-8")));
                    response.onLoaded(); // implicit onHeaderLoaded will happen if not yet...
                    // response.onLoaded(false);
                    //}http.setResponse(response);
                }
            }
        }
    }

    @Override
    public Runnable getRunnable(P provider, DI<ByteBuffer, P> data) {
        return null;
    }

    @Override
    public List<Task> getTasks(TaskPhase... pahses) {
        return Collections.emptyList();
    }

    public String echo(HttpRequest req) {
        return req.toString();
    }

}
