/*
 * The MIT License
 *
 * Copyright 2021 sesidoro.
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
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.SSLContext;
import ssg.lib.common.CommonTools;
import ssg.lib.common.TaskExecutor.TaskExecutorSimple;
import ssg.lib.common.TaskProvider;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.common.net.NetTools;
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
import ssg.lib.net.CS;
import ssg.lib.net.CSListener.DebuggingCSListener;
import ssg.lib.net.MCS;
import ssg.lib.net.MCS.RunnerIO;
import ssg.lib.net.TCPHandler;
import ssg.lib.net.TestSSLTools;
import ssg.lib.net.WebSocketFT2;
import ssg.lib.net.stat.MCSStatistics;
import ssg.lib.net.stat.RunnerStatisticsImpl;
import ssg.lib.service.DF_Service;
import ssg.lib.service.Repository;
import ssg.lib.ssl.SSLTools;

/**
 *
 * @author 000ssg
 */
public class Test_MCS_DI {

    public static void main(String... args) throws Exception {
        //System.getProperties().put("javax.net.debug", "SSL,handshake");
        //System.getProperties().put("javax.net.debug", "all");
        //SSL_IO.DEBUG = true;
        //SSL_IO.DEBUG_UNWRAP = true;
        //SSL_IO.DEBUG_WRAP = true;
        final boolean MCS_DEBUG = false;

        System.out.println("... configure MCS");
        CS mcs = new CS(1, 1) {
            @Override
            public RunnerIO onConnected(MCS.Runner runner, SocketChannel ch, RunnerIO attachment) {
                return MCS_DEBUG ? new Test_MCS.MCSMonitor(ch, runner, super.onConnected(runner, ch, attachment)) {
                    @Override
                    public void info(String method, byte[] text) {
                        System.out.println("C:" + ch + ":" + method + ":" + (text != null ? text.length : "<none>"));
                    }
                }.configureBin(true) : attachment;
            }

            @Override
            public RunnerIO onAccepted(MCS.Runner runner, SocketChannel ch, RunnerIO attachment) {
                return MCS_DEBUG ? new Test_MCS.MCSMonitor(ch, runner, super.onConnected(runner, ch, attachment)) {
                    @Override
                    public void info(String method, byte[] text) {
                        System.out.println("S:" + ch + ":" + method + ":" + (text != null ? text.length : "<none>"));
                    }
                }.configureBin(true) : attachment;
            }
        }
                .configureName("test-cs")
                .configureStatistics(new MCSStatistics("mcs_di", new RunnerStatisticsImpl()));

        System.out.println("... start MCS");
        mcs.start();

        if (1 == 0) {
            Thread th = new Thread() {
                @Override
                public void run() {
                    try {
                        NetTools.delay(1000);
                        URL url = new URL("https://localhost:18124/sys/names");
                        InputStream is = url.openStream();
                        byte[] buf = CommonTools.loadInputStream(is);
                        is.close();
                        int a = 0;
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            };
            th.setDaemon(true);
            th.start();
        }

        mcs.addListener(new DebuggingCSListener(DebuggingCSListener.DO_ALL));
        run(mcs);

        System.out.println("... sleep main");
        NetTools.delay(1000);

        System.out.println("... stop MCS");
        mcs.stop();
        System.out.println(mcs);
    }

    public static void run(CS cs) throws Exception {
        int httpPort = 18123;
        //httpPort++;
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
                //SSLContext.setDefault(sslCtx);
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
                if (data != null && BufferTools.hasRemaining(data)) {
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

}
