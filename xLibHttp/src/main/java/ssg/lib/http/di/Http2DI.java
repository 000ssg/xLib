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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.Collection;
import java.util.List;
import ssg.lib.common.TaskExecutor;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.di.DI;
import ssg.lib.di.base.BaseDI;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.HttpAuthenticator;
import ssg.lib.http.HttpService;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.service.DF_Service;

/**
 * Http service helper: binds HTTP service to DF_Service while simplifying Http
 * configuration.
 *
 * Usage scenario:
 * <pre>
 * Http2DI http = new Http2DI();
 * http.addApp(new HttpApplication("App", "/app") { ... });
 * if (http.httpService.getDataProcessors(null, null) == null) {
 *    http.httpService.setDataProcessors(new Repository<DataProcessor>());
 * }
 * http.httpService.getDataProcessors(null, null).addItem(new HttpStaticDataProcessor()
 *   .add(new HttpResourceBytes(classLoader.getResourceAsStream("app/images/logo.png"), "/favicon.ico", "image/png"))
 *   .add(new HttpResourceCollection("/.well-known/acme-challenge/", "/tmp/*"))
 * );
 *
 * DF_Service<ByteBuffer, SocketChannel> dfService=...
 * DI<ByteBuffer, SocketChannel> httpDI = http.buildHandler(dfService);
 *
 * // use httpDI to process input/output over transport data channel..., e.g. as follows:
 * server.add(new TCPHandler(
 *     new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), httpPort)
 *   )
 *   .defaultHandler(httpDI)
 * ;
 * </pre>
 *
 * @author 000ssg
 */
public class Http2DI {

    HttpAuthenticator.HttpSimpleAuth httpAuth = new HttpAuthenticator.HttpSimpleAuth();
    HttpService httpService;
    public boolean DEBUG = false;

    public Http2DI() {
        init();
    }

    public Http2DI(HttpAuthenticator.HttpSimpleAuth httpAuth) {
        this.httpAuth = httpAuth;
        init();
    }

    public void dumpStat(String text) {
        StringBuilder sb = new StringBuilder();
        Runtime rt = Runtime.getRuntime();
        sb.append("[" + System.currentTimeMillis() + "]");
        sb.append(" CPUs: " + rt.availableProcessors());
        sb.append(", RAM (f/t/m, MB): " + (rt.freeMemory() / 1024 / 1024f) + "/" + (rt.totalMemory() / 1024 / 1024f) + "/" + (rt.maxMemory() / 1024 / 1024f));
        sb.append("\n  " + ("" + text).replace("\n", "\n  "));
        System.out.println(sb);
        rt.gc();
    }

    public void init() {
        httpService = new HttpService(httpAuth) {
            @Override
            public void onHttpDataCreated(Channel provider, HttpData http) {
                super.onHttpDataCreated(provider, http);
                if (http instanceof HttpRequest) {
                    HttpRequest req = (HttpRequest) http;
                    if (DEBUG) {
                        dumpStat("REQUEST CREATE   : " + req.getQuery());
                    }
                }
            }

            @Override
            public void onHttpDataCompleted(Channel provider, HttpData http) {
                super.onHttpDataCompleted(provider, http);
                if (http instanceof HttpRequest) {
                    HttpRequest req = (HttpRequest) http;
                    if (DEBUG) {
                        dumpStat("REQUEST DONE[" + req.getResponse().getHead().getProtocolInfo()[1] + "]: " + req.getQuery());
                    }
                }
            }

            @Override
            public void onServiceError(Channel provider, DI pd, Throwable error) throws IOException {
                if (pd instanceof DIHttpData) {
                    DIHttpData hdi = (DIHttpData) pd;
                    HttpData http = hdi.http(provider);
                    if (http instanceof HttpRequest) {
                        HttpRequest req = (HttpRequest) http;
                        if (DEBUG) {
                            dumpStat("REQUEST ERROR    : " + req.getQuery() + "\n  " + error);
                        }
                    }
                }
                super.onServiceError(provider, pd, error);
            }
        };
    }

    public HttpService getHttp() {
        return httpService;
    }

    public HttpAuthenticator.HttpSimpleAuth getHttpAuth() {
        return httpAuth;
    }

    public void addApp(HttpApplication app, HttpAuthenticator.HttpSimpleAuth.Domain domain) {
        if (httpAuth != null && domain != null) {
            httpAuth.addDomain(domain);
        }
        getHttp().getApplications().addItem(app);
    }

    public DI<ByteBuffer, Channel> buildHandler(DF_Service<Channel> service) {
        if (service == null) {
            service = new DF_Service<>(new TaskExecutor.TaskExecutorSimple());
        }
        service.getServices().addItem(httpService);
        DI<ByteBuffer, Channel> httpServer = new BaseDI<ByteBuffer, Channel>() {
            @Override
            public long size(Collection<ByteBuffer>... data) {
                return BufferTools.getRemaining(data);
            }

            @Override
            public void consume(Channel provider, Collection<ByteBuffer>... data) throws IOException {
                if (BufferTools.hasRemaining(data)) {
                    throw new UnsupportedOperationException("Not supported: service MUST handle (consume) all data without leaving unhandled bytes.");
                }
            }

            @Override
            public List<ByteBuffer> produce(Channel provider) throws IOException {
                return null;
            }
        };
        httpServer.filter(service);
        return httpServer;
    }
}
