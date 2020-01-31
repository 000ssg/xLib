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
package ssg.lib.net;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.net.URL;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import ssg.lib.common.TaskExecutor.TaskExecutorSimple;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.common.net.NetTools;
import ssg.lib.di.DI;
import ssg.lib.di.base.BaseDI;
import ssg.lib.di.base.BufferingDI;
import ssg.lib.di.base.EchoDI;
import ssg.lib.di.base.SSL_DF;
import ssg.lib.http.HttpDataProcessor;
import ssg.lib.http.HttpService;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.http.base.HttpResponse;
import ssg.lib.http.rest.MethodsProvider;
import ssg.lib.http.rest.RESTHttpDataProcessor;
import ssg.lib.http.rest.XMethodsProvider;
import ssg.lib.http.rest.annotations.XMethod;
import ssg.lib.http.rest.annotations.XParameter;
import ssg.lib.http.rest.annotations.XType;
import ssg.lib.service.DF_Service;
import ssg.lib.service.DF_Service.DF_ServiceListener.SERVICE_EVENT;
import ssg.lib.service.Repository;
import ssg.lib.ssl.SSL_IO;

/**
 *
 * @author 000ssg
 */
public class CSHttpDemo {

    public static void log(String title, HttpData http) throws IOException {
        System.out.println(title + ": " + http.toString().replace("\r\n", "\\r\\n").replace("\n", "\\n"));
        if (!http.getBody().isEmpty()) {
            System.out.println("    | " + http.getBody().toString().replace("\n", "\n    | "));
        }
    }

    public static class IDI extends BaseDI<ByteBuffer, SocketChannel> {

        public static final long DO_READ = 0x0001;
        public static final long DO_WRITE = 0x0002;
        public static final long DO_PRODUCE = 0x0004;
        public static final long DO_CONSUME = 0x0008;
        public static final long DO_CALL = 0x0010;
        public static final long DO_ALL = DO_READ | DO_WRITE | DO_PRODUCE | DO_CONSUME | DO_CALL;
        public static final long DO_NONE = 0x0;

        Map<SocketChannel, HttpRequest> calls = new LinkedHashMap<>();
        long DUMP = DO_NONE;//DO_ALL ^ DO_CONSUME;

        public HttpResponse call(SocketChannel sc, HttpRequest req) throws IOException {
            log("call", req);
            HttpRequest reqOld = calls.get(sc);
            if (reqOld != null) {
                log("call -> OLD", reqOld);
            }

            if (reqOld == null || reqOld.isDone()) {
                calls.put(sc, req);
                log("call -> RESP", req.getResponse());
                return req.getResponse();
            } else {
                if (reqOld == req) {
                    return null;
                } else {
                    throw new IOException("Previous HTTP request/response is in progress.");
                }
            }
        }

        public boolean isBusy(SocketChannel sc) {
            HttpRequest reqOld = calls.get(sc);
            return !(reqOld == null || reqOld.isDone());
        }

        @Override
        public List<ByteBuffer> read(SocketChannel provider) throws IOException {
            List<ByteBuffer> r = super.read(provider);
            if ((DO_READ & DUMP) != 0 && BufferTools.hasRemaining(r)) {
                System.out.println("IDI.READ [" + BufferTools.getRemaining(r) + "]: ");// + BufferTools.toText("ISO-8859-1", r).replace("\n", "\n  "));
            }
            return r;
        }

        @Override
        public long write(SocketChannel provider, Collection<ByteBuffer>... data) throws IOException {
            if ((DO_WRITE & DUMP) != 0 && BufferTools.hasRemaining(data)) {
                System.out.println("IDI.WRITE[" + BufferTools.getRemaining(data) + "]: ");// + BufferTools.toText("ISO-8859-1", data).replace("\n", "\n  "));
            }
            long r = super.write(provider, data);
            return r;
        }

        @Override
        public long size(Collection<ByteBuffer>... data) {
            return BufferTools.getRemaining(data);
        }

        @Override
        public List<ByteBuffer> produce(SocketChannel sc) throws IOException {
            HttpRequest req = calls.get(sc);
            if (req != null && !req.isSent()) {
                if ((DO_PRODUCE & DUMP) != 0) {
                    System.out.println("produce: " + sc);
                }
                List<ByteBuffer> data = req.get();
                if ((DO_PRODUCE & DUMP) != 0 && data != null && !data.isEmpty()) {
                    System.out.println("IDI.PRODUCE[" + BufferTools.getRemaining(data) + "]: " + BufferTools.toText("ISO-8859-1", data));
                }
                return data;
            }
            return null;
        }

        @Override
        public void consume(SocketChannel sc, Collection<ByteBuffer>... data) throws IOException {
            if ((DO_CONSUME & DUMP) != 0) {
                System.out.println("IDI.CONSUME: " + sc + "  " + BufferTools.getRemaining(data));
            }
            if (BufferTools.hasRemaining(data)) {
                if ((DO_CONSUME & DUMP) != 0) {
                    System.out.println("  | " + BufferTools.toText("ISO-8859-1", data).replace("\n", "\n  | "));
                }
            } else {
                return;
            }
            long c = 0;
            HttpRequest req = calls.get(sc);
            for (Collection<ByteBuffer> bbs : data) {
                if (bbs == null || bbs.isEmpty()) {
                    continue;
                }
                if (req != null && req.isDone()) {
                    break;
                }
                for (ByteBuffer bb : bbs) {
                    if (bb == null || !bb.hasRemaining()) {
                        continue;
                    }
                    c += bb.remaining();
                    if ((DO_CONSUME & DUMP) != 0 && bb.hasRemaining()) {
                        System.out.println("IDI.CONSUME[" + BufferTools.getRemaining(bb) + "]: " + BufferTools.toText("ISO-8859-1", bb));
                    }
                    req.add(bb);
                    if (req != null && req.isSent() && req.isDone()) {
                        break;
                    }
                }
            }
        }
    }

    @XType
    public static class RESTObj {

        static RESTObj instance = new RESTObj();

        static long started = System.currentTimeMillis();

        @XMethod
        public long upTime() {
            return System.currentTimeMillis() - started;
        }

        @XMethod(name = "names")
        public String[] getPropertyNames() {
            return ((Collection<String>) Collections.list(System.getProperties().propertyNames())).toArray(new String[System.getProperties().size()]);
        }

        @XMethod(name = "property")
        public String getProperty(@XParameter(name = "name") String name) {
            return System.getProperty(name);
        }
    }

    public static void testTCP(CS cs) throws Exception {
        int httpPort = 18123;

        TCPHandler tcpl = new TCPHandler();
        IDI idp = new IDI();
        cs.add(tcpl);

        System.out.println(""
                + "\n========================================================"
                + "\n======= TCP tests"
                + "\n========================================================"
        );

        // add SSL support
        SSLContext sslCtx = SSLContext.getDefault();
        if (1 == 1) {
            sslCtx = TestSSLTools.getSSLContext();
        }
        SSL_DF ssl_df_client = new SSL_DF(sslCtx, true);
        SSL_DF ssl_df_server = new SSL_DF(sslCtx, false);
        ssl_df_server.setNeedClientAuth(Boolean.TRUE);

        HttpService httpService = new HttpService();
        DF_Service<SocketChannel> service = new DF_Service<>(new TaskExecutorSimple());
        if (1 == 0) {
            service.addServiceListener(new DF_Service.DebuggingDF_ServiceListener<>()
                    .includeEvents(SERVICE_EVENT.values())
                    .excludeEvents(
                            SERVICE_EVENT.read_ext,
                            SERVICE_EVENT.write_ext
                    )
            );
        }
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
        httpService.getDataProcessors(null, null).addItem(0, new RESTHttpDataProcessor("/sys", new MethodsProvider[]{new XMethodsProvider()}, new RESTObj()));

        long timeout = 1000 * 60 * 60 * 15;

        URL url = new URL("https://www.instagram.com/mariahwyckoff/");
        //url = new URL("https://netbeans.org/");
        //url = new URL("https://www.microsoft.com/en-us/");
        url = new URL("https://127.0.0.1:" + httpPort + "/sys/names");
        url = new URL("https://127.0.0.1:" + httpPort + "/sys/property?name=os.name");
        //url = new URL("https://127.0.0.1:" + httpPort + "/sys/upTime");
        url = new URL("https://127.0.0.1:" + httpPort + "/sys/update");

        if (url.getProtocol().endsWith("s")) {
            idp.filter(ssl_df_client);
        } else {
            idp.filter(null);
        }

        if (1 == 0) {
            try {
                File file = new File("test_chunked_http.bin");
                InputStream is = new FileInputStream(file);
                byte[] buf = new byte[(int) file.length()];
                is.read(buf);
                is.close();

                ByteBuffer bb = ByteBuffer.wrap(buf);
                HttpResponse res = new HttpResponse(null);
                List<ByteBuffer> bbs = BufferTools.aggregate(1386, true, bb);
                //res.add(Collections.singletonList(bb));
                res.add(bbs);
                int a = 0;

                bb = ByteBuffer.wrap(buf);
                res = new HttpResponse(null);
                res.add(Collections.singletonList(bb));
                a = 0;
            } catch (Throwable th) {
                int a = 0;
            }

        }
        if (1 == 0) {
            BufferingDI di = new BufferingDI();
            if (url.getProtocol().endsWith("s")) {
                di.filter(ssl_df_client);
            } else {
                di.filter(null);
            }
            SocketChannel sc = tcpl.connect(new InetSocketAddress(InetAddress.getByName(url.getHost()), url.getPort() > 0 ? url.getPort() : url.getDefaultPort()), di);
            HttpRequest req = new HttpRequest(true).append(ByteBuffer.wrap((""
                    + "GET " + url.getPath() + ((url.getQuery() != null) ? "?" + url.getQuery() : "") + " HTTP/1.1\r\n"
                    + "Host: " + url.getHost() + "\r\n"
                    + "User-Agent: Mozilla/4.0 (Windows NT 10.0; Win64; x64;)\r\n"
                    + "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n"
                    + "Accept-Language: en-US,en;q=0.5\r\n"
                    //+ "Accept-Encoding: gzip\r\n"
                    + "Connection: keep-alive\r\n"
                    + "\r\n").getBytes()));

            di.push(sc, req.getAll());
            Thread.sleep(50);

            long done = System.currentTimeMillis() + 1000 * 17;

            while (!req.isDone() && sc.isOpen() && System.currentTimeMillis() < done) {
                Thread.sleep(5);
            }
            List<ByteBuffer> data = di.fetch(sc);
            System.out.println("RESP[" + BufferTools.getRemaining(data) + "]:\n----------------------\n" + BufferTools.toText("ISO-8859-1", data) + "\n--------------\n");
            OutputStream os = new FileOutputStream("test_chunked_http.bin");
            os.write(BufferTools.toBytes(true, data));
            os.close();
            sc.close();

            try {
                HttpResponse res = new HttpResponse(null);
                res.add(data);
                int a = 0;
            } catch (Throwable th) {
                int a = 0;
            }
        }

        for (URL turl : new URL[]{
            new URL("https://127.0.0.1:" + httpPort + "/sys/names"),
            new URL("https://127.0.0.1:" + httpPort + "/sys/property?name=os.name"),
            new URL("https://127.0.0.1:" + httpPort + "/sys/upTime"),
            new URL("https://127.0.0.1:" + httpPort + "/sys/update")
        }) {
            System.out.println(""
                    + "\n------------------------"
                    + "\n----- " + turl
                    + "\n------------------------"
            );
            SocketChannel sc = tcpl.connect(new InetSocketAddress(InetAddress.getByName(turl.getHost()), turl.getPort() > 0 ? turl.getPort() : url.getDefaultPort()), idp);
            HttpRequest req = new HttpRequest(true).append(ByteBuffer.wrap((""
                    + "GET " + turl.getPath() + ((turl.getQuery() != null) ? "?" + turl.getQuery() : "") + " HTTP/1.1\r\n"
                    + "Host: " + turl.getHost() + "\r\n"
                    + "User-Agent: Mozilla/4.0 (Windows NT 10.0; Win64; x64;)\r\n"
                    + "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n"
                    + "Accept-Language: en-US,en;q=0.5\r\n"
                    + "Accept-Encoding: gzip\r\n"
                    + "Connection: keep-alive\r\n"
                    + "\r\n").getBytes()));
            HttpResponse resp = idp.call(sc, req);
            Thread.sleep(50);

            long done = System.currentTimeMillis() + timeout;

            while (!req.isDone() && sc.isOpen() && System.currentTimeMillis() < done) {
                Thread.sleep(5);

            }
            System.out.println("RESP: " + resp);
        }
    }

    public static void testUDP(CS cs) throws Exception {
        System.out.println(""
                + "\n========================================================"
                + "\n======= UDP tests"
                + "\n========================================================"
        );
        InetAddress udpAddr = InetAddress.getByAddress(new byte[]{0, 0, 0, 0});

        List<NetworkInterface> nis = NetTools.getSupportedNetworkInterfaces(
                true,
                false,
                true,
                false,
                false);
        NetworkInterface ni = nis.get(2);

        System.out.println("NI: " + ni);
        for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
            //System.out.println("  ia: "+ia);
            InetAddress a = ia.getAddress();
            if (a.getAddress().length == 4) {
                udpAddr = a;
                break;
            }
        }
        System.out.println("NI IP: " + udpAddr);

        int udpPort = 33001;
        InetAddress udpA = udpAddr;
        InetSocketAddress udpSA = new InetSocketAddress(udpA, udpPort);

        Handler h = new UDPHandler(
                ni,
                new InetSocketAddress(udpAddr, udpPort),
                null,
                true,
                false,
                new EchoDI<ByteBuffer, SocketAddress>() {
            int idx = 0;

            @Override
            public List<ByteBuffer> echo(SocketAddress provider, Collection<ByteBuffer>... data) {
                List<ByteBuffer> r = BufferTools.toList(true, data);
                String msg = BufferTools.toText("ISO-8859-1", r);
                System.out.println(Thread.currentThread().getName() + ": UDP accept[" + provider + "]: " + msg);
                r.clear();
                //r.add(ByteBuffer.wrap(("\nECHO[" + idx++ + "]: ").getBytes()));
                r.add(ByteBuffer.wrap(("\nECHO[" + idx++ + "]: " + msg.replace("\n", "\\n")).replace("\r", "\\r").getBytes()));
                return r;
            }
        }
        );

        cs.add(h);

        if (1 == 1) {
            //NetTools.delay(1000);
            System.out.println("send UDPs");
            for (int k = 0; k < 2; k++) {
                for (int i = 0; i < 3; i++) {
                    NetTools.sendUDP(udpA, udpPort, ("Hello, " + k + "-" + i + " UDP.").getBytes("ISO-8859-1"));
                    NetTools.sendUDP(udpA, udpPort, ("Hello, " + k + "-" + i + " UDP, again.").getBytes("ISO-8859-1"));
                }
                //NetTools.delay(1000);
            }
        }

        //NetTools.delay(1000 * 5);
        System.out.println("Open UDP channel and send UDPs");
        DatagramChannel dc = DatagramChannel.open()
                .setOption(StandardSocketOptions.SO_REUSEADDR, true)
                .bind(null);
        for (int k = 0; k < 2; k++) {
            for (int i = 0; i < 3; i++) {
                dc.send(ByteBuffer.wrap(("DC Hello, " + k + "-" + i + " UDP.").getBytes("ISO-8859-1")), udpSA);
                dc.send(ByteBuffer.wrap(("DC Hello, " + k + "-" + i + " UDP, again.").getBytes("ISO-8859-1")), udpSA);
            }
            //NetTools.delay(1000);
        }

        dc.connect(udpSA);
        for (int k = 0; k < 2; k++) {
            for (int i = 0; i < 3; i++) {
                dc.send(ByteBuffer.wrap(("DC(C) Hello, " + k + "-" + i + " UDP.").getBytes("ISO-8859-1")), udpSA);
                dc.send(ByteBuffer.wrap(("DC(C) Hello, " + k + "-" + i + " UDP, again.").getBytes("ISO-8859-1")), udpSA);
                dc.write(ByteBuffer.wrap(("DC(C,W) Hello, " + k + "-" + i + " UDP.").getBytes("ISO-8859-1")));
                dc.write(ByteBuffer.wrap(("DC(C,W) Hello, " + k + "-" + i + " UDP, again.").getBytes("ISO-8859-1")));
            }
            //NetTools.delay(1000);
        }

        // get response...
        if (dc.isConnected()) {
            ByteBuffer buf = ByteBuffer.allocateDirect(1024 * 64);
            try {
                System.out.println(Thread.currentThread().getName() + ": RESP DC");
                int len = dc.read(buf);
                while (len > 0) {
                    ((Buffer) buf).flip();
                    String msg = BufferTools.toText("ISO-8859-1", buf);
                    System.out.println(Thread.currentThread().getName() + ": " + msg.replace("\n", "\\n"));
                    ((Buffer) buf).clear();
                    if (!msg.contains("DC(C,W) Hello, 1-2 UDP, again")) {
                        len = dc.read(buf);
                    } else {
                        len = -1;
                    }
                }
            } catch (Throwable th) {
            }
        }

        dc.close();
    }

    public static void main(String... args) throws Exception {
        //System.getProperties().put("javax.net.debug", "all");
        //System.getProperties().put("javax.net.debug", "SSL,handshake");
        SSL_IO.DEBUG = false;

        CS cs = new CS();
        cs.start();

        testTCP(cs);
        testUDP(cs);

        cs.stop();
    }
}
