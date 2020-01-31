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
package ssg.lib.net.t1;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.SSLContext;
import ssg.lib.common.TaskExecutor.TaskExecutorSimple;
import ssg.lib.common.TaskProvider;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.di.DI;
import ssg.lib.di.base.BaseDI;
import ssg.lib.di.base.SSL_DF;
import ssg.lib.http.HttpDataProcessor;
import ssg.lib.http.HttpService;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.http.di.DIHttpData;
import ssg.lib.http.dp.HttpResourceBytes;
import ssg.lib.http.dp.HttpStaticDataProcessor;
import ssg.lib.http.rest.MethodsProvider;
import ssg.lib.http.rest.RESTHttpDataProcessor;
import ssg.lib.http.rest.XMethodsProvider;
import ssg.lib.http.rest.annotations.XMethod;
import ssg.lib.http.rest.annotations.XParameter;
import ssg.lib.http.rest.annotations.XType;
import ssg.lib.net.CS;
import ssg.lib.net.TCPHandler;
import ssg.lib.net.TestSSLTools;
import ssg.lib.net.WebSocketFT2;
import ssg.lib.service.DF_Service;
import ssg.lib.service.Repository;
import ssg.lib.ssl.SSLTools;

/**
 *
 * @author 000ssg
 */
@XType
public class DemoApp_1 {

    static long started = System.currentTimeMillis();
    boolean stopped = false;

    @XMethod
    public long upTime() {
        return System.currentTimeMillis() - started;
    }

    @XMethod(name = "names")
    public String[] getPropertyNames() {
        return ((Collection<String>) Collections.list(System.getProperties().propertyNames())).toArray(new String[System.getProperties().size()]);
    }

    @XMethod(name = "names")
    public String[] getPropertyNames(@XParameter(name = "mask") String mask) {
        String[] ns = getPropertyNames();
        int c = 0;
        for (int i = 0; i < ns.length; i++) {
            if (mask != null && !ns[i].contains(mask)) {
                ns[i] = null;
            } else {
                c++;
            }
        }
        if (c < ns.length) {
            if (c == 0) {
                return new String[0];
            }
            int off = 0;
            for (int i = 0; i < ns.length; i++) {
                if (ns[i] == null) {
                } else {
                    ns[off++] = ns[i];
                }
            }
        }
        if (c < ns.length) {
            return Arrays.copyOf(ns, c);
        } else {
            return ns;
        }
    }

    @XMethod(name = "property")
    public String getProperty(@XParameter(name = "name") String name) {
        return System.getProperty(name);
    }

    @XMethod(name = "properties")
    public String[][] getProperties(@XParameter(name = "mask") String mask, @XParameter(name = "valueMask", optional = true) String valueMask, @XParameter(name = "skipEmpties", optional = true) Boolean skipEmpties) {
        String[] ns = getPropertyNames(mask);
        if (skipEmpties == null) {
            skipEmpties = false;
        }
        if (ns.length > 0) {
            String[][] result = new String[ns.length][2];
            int off = 0;
            for (int i = 0; i < result.length; i++) {
                String v = System.getProperty(ns[i]);
                if (v != null && valueMask != null && !v.contains(valueMask)) {
                    v = null;
                }
                if (v == null && skipEmpties) {
                    continue;
                }
                result[off][0] = ns[i];
                result[off++][1] = v;
            }
            if (off < result.length) {
                result = Arrays.copyOf(result, off);
            }
            return result;
        } else {
            return new String[0][0];
        }
    }

    @XMethod(name = "stop")
    public void stop() {
        stopped = true;
        System.exit(0);
    }

    public boolean isStopped() {
        return stopped;
    }

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////// HTTP runner
    ////////////////////////////////////////////////////////////////////////////
    public static void run(CS cs) throws Exception {
        int httpPort = 18123;
        TCPHandler tcpl = new TCPHandler();

        // add SSL support
        SSLContext sslCtx = SSLContext.getDefault();
        if (1 == 1) {
            try {
                String ksResource = "ks/localhost__abc.p12";
                String tsResource = "ks/localhost__abc_ts.p12";
                ksResource = "keystore.p12";
                tsResource = "keystore.p12";
                SSLTools.SSLHelper sslh_abc = SSLTools.createSSLHelper(
                        WebSocketFT2.class.getClassLoader().getResource(ksResource),
                        "passw0rd",
                        "passw0rd",
                        WebSocketFT2.class.getClassLoader().getResource(tsResource),
                        "passw0rd"
                );
                sslCtx = sslh_abc.createSSLContext("TLS", true);
            } catch (Throwable th) {
                sslCtx = TestSSLTools.getSSLContext();
            }

        }
        SSL_DF ssl_df_client = new SSL_DF(sslCtx, true);
        SSL_DF ssl_df_server = new SSL_DF(sslCtx, false);
        ssl_df_server.setNeedClientAuth(Boolean.FALSE);
        ssl_df_server.setAutodetect(true);

        HttpService httpService = new HttpService() {
            @Override
            public void onHttpDataCreated(Channel provider, HttpData http) {
                super.onHttpDataCreated(provider, http);
                if (http instanceof HttpRequest) {
                    HttpRequest req = (HttpRequest) http;
                    System.out.println("REQUEST CREATE   : " + req.getQuery());
                }
            }

            @Override
            public void onHttpDataCompleted(Channel provider, HttpData http) {
                super.onHttpDataCompleted(provider, http);
                if (http instanceof HttpRequest) {
                    HttpRequest req = (HttpRequest) http;
                    System.out.println("REQUEST DONE[" + req.getResponse().getHead().getProtocolInfo()[1] + "]: " + req.getQuery());
                }
            }

            @Override
            public void onServiceError(Channel provider, DI pd, Throwable error) throws IOException {
                if (pd instanceof DIHttpData) {
                    DIHttpData hdi = (DIHttpData) pd;
                    HttpData http = hdi.http(provider);
                    if (http instanceof HttpRequest) {
                        HttpRequest req = (HttpRequest) http;
                        System.out.println("REQUEST ERROR    : " + req.getQuery() + "\n  " + error);
                    }
                }
                super.onServiceError(provider, pd, error);
            }
        };
        
        DF_Service<SocketChannel> service = new DF_Service<>(new TaskExecutorSimple());

        service.filter(ssl_df_server);
        service.getServices().addItem(httpService);
        DI<ByteBuffer, SocketChannel> httpServer = new BaseDI<ByteBuffer, SocketChannel>() {
            @Override
            public long size(Collection<ByteBuffer>... data) {
                return BufferTools.getRemaining(data);
            }

            @Override
            public void consume(SocketChannel provider, Collection<ByteBuffer>... data) throws IOException {
                if (BufferTools.hasRemaining(data)) {
                    throw new UnsupportedOperationException("Not supported: service MUST handle all data without producing unhandled bytes.");
                }
            }

            @Override
            public List<ByteBuffer> produce(SocketChannel provider) throws IOException {
                return null;
            }
        };
        httpServer.filter(service);
        TCPHandler tcplHttp = new TCPHandler(new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), httpPort)).defaultHandler(httpServer);
        cs.add(tcplHttp);

        if (httpService.getDataProcessors(null, null) == null) {
            httpService.setDataProcessors(new Repository<HttpDataProcessor>());
        }

        DemoApp_1 app = new DemoApp_1();

        httpService.getDataProcessors(null, null).addItem(0, new RESTHttpDataProcessor("/sys", new MethodsProvider[]{new XMethodsProvider()}, app));
        httpService.getDataProcessors(null, null).addItem(1, new RESTHttpDataProcessor("/system", new MethodsProvider[]{new XMethodsProvider()}, app));
        httpService.getDataProcessors(null, null).addItem(2, new HttpStaticDataProcessor()
                .add(new HttpResourceBytes(DemoApp_1.class.getClassLoader().getResourceAsStream("di.png"), "/favicon.ico", "image/png"))
        );
        httpService.getDataProcessors(null, null).addItem(new HttpDataProcessor() {
            @Override
            public Runnable getRunnable(Object provider, DI data) {
                return null;
            }

            @Override
            public List<TaskProvider.Task> getTasks(TaskProvider.TaskPhase... phases) {
                return Collections.emptyList();
            }
        });

        while (!app.isStopped()) {
            Thread.sleep(10);
        }
    }

    public static void main(String... args) throws Exception {
        //System.getProperties().put("javax.net.debug", "SSL,handshake");

        CS cs = new CS();
        cs.start();

        run(cs);

        cs.stop();
    }

}
