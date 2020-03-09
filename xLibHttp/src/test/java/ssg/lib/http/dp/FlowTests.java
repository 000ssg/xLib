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

import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import ssg.lib.common.TaskExecutor.TaskExecutorSimple;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.di.DI;
import ssg.lib.di.DM;
import ssg.lib.di.base.BaseDI;
import ssg.lib.http.HttpApplication;
import ssg.lib.http.HttpService;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.http.rest.MethodsProvider;
import ssg.lib.http.rest.RESTHttpDataProcessor;
import ssg.lib.http.rest.XMethodsProvider;
import ssg.lib.http.rest.annotations.XMethod;
import ssg.lib.http.rest.annotations.XParameter;
import ssg.lib.http.rest.annotations.XType;
import ssg.lib.service.DF_Service;

/**
 *
 * @author 000ssg
 */
public class FlowTests {

    public static class AChannel implements Channel, ByteChannel {

        static AtomicInteger nextId = new AtomicInteger(1);

        int id = nextId.getAndIncrement();
        boolean closed = false;

        List<ByteBuffer> input = new ArrayList<>();
        List<ByteBuffer> output = new ArrayList<>();

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            if (!isOpen()) {
                throw new IOException("Cannot read from closed channel: " + this);
            }
            if (input != null && !input.isEmpty()) {
                int c = 0;
                Iterator<ByteBuffer> it = input.iterator();
                while (it.hasNext() && dst.hasRemaining()) {
                    ByteBuffer bb = it.next();
                    while (dst.hasRemaining() && bb.hasRemaining()) {
                        dst.put(bb.get());
                        c++;
                    }
                    if (!bb.hasRemaining()) {
                        it.remove();
                    }
                }
                return c;
            } else if (closed) {
                return -1;
            } else {
                return 0;
            }
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            if (!isOpen()) {
                throw new IOException("Cannot write to closed channel: " + this);
            }
            if (src != null && src.hasRemaining()) {
                ByteBuffer bb = ByteBuffer.allocate(src.remaining());
                int c = 0;
                while (src.hasRemaining()) {
                    bb.put(src.get());
                    c++;
                }
                ((Buffer) bb).flip();
                output.add(bb);
                return c;
            } else {
                return 0;
            }
        }

        @Override
        public String toString() {
            return "AChannel{id=" + id + ", closed=" + closed + ", input=" + BufferTools.getRemaining(input) + ", output=" + BufferTools.getRemaining(output) + '}';
        }

    }

    @XType
    public static class ARest {

        @XMethod
        public void a() {
        }

        @XMethod
        public String b(@XParameter(name = "text") String s) {
            return "REPLY to '" + s + "'";
        }

        @XMethod
        public float d(@XParameter(name = "delay") long delay) {
            long st = System.nanoTime();
            try {
                Thread.sleep(Math.min(1000, delay));
            } catch (Throwable th) {
            }
            return (System.nanoTime() - st) / 1000000f;
        }
    }

    public static Object call(DI<ByteBuffer, Channel> transport, Channel ch, File folder, Object[] oo, boolean dump) {
        Object r = null;
        try {
            File f = new File(folder, ((String) oo[0]).substring(1));
            System.out.println("------ " + f.length() + "\t" + f.getName() + "  (" + oo[0] + ")");
            HttpRequest req = new HttpRequest("GET", (String) oo[0]);
            if (oo.length > 1) {
                for (int i = 1; i < oo.length; i++) {
                    if (oo[i] instanceof String) {
                        String s = (String) oo[i];
                        int idx = s.indexOf(":");
                        if (idx != -1) {
                            req.getHead().addHeader(s.substring(0, idx), s.substring(idx + 1).trim());
                        }
                    }
                }
            }
            req.onLoaded();
            transport.write(ch, req.getAll());
            System.out.println(req);
            long bc = 0;
            while (!req.getResponse().isCompleted()) {
                List<ByteBuffer> respData = transport.read(ch);
                bc += BufferTools.getRemaining(respData);
                req.getResponse().add(respData);
            }
            System.out.println("Transport bytes: " + bc + ((dump) ? "\n" + req.getResponse() : ""));
            try {
                r = req.getResponse().getBody().asBytes();
            } catch (Throwable th) {
            }
            int a = 0;
        } catch (IOException ioex) {
            ioex.printStackTrace();
        }
        return r;
    }

    public static void main(String... args) throws Exception {
        final List<ByteBuffer> transportIn = new ArrayList<>();
        final List<ByteBuffer> transportOut = new ArrayList<>();
        DI<ByteBuffer, Channel> transport = new BaseDI<ByteBuffer, Channel>() {
            @Override
            public long size(Collection<ByteBuffer>... data) {
                return BufferTools.getRemaining(data);
            }

            @Override
            public void consume(Channel provider, Collection<ByteBuffer>... data) throws IOException {
                if (data != null) {
                    for (Collection<ByteBuffer> bbs : data) {
                        if (bbs != null) {
                            for (ByteBuffer bb : bbs) {
                                if (bb != null) {
                                    ByteBuffer b = ByteBuffer.allocate(bb.remaining());
                                    b.put(bb);
                                    ((Buffer) b).flip();
                                    transportIn.add(b);
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public List<ByteBuffer> produce(Channel provider) throws IOException {
                List<ByteBuffer> r = new ArrayList<>(transportOut.size());
                r.addAll(transportOut);
                transportOut.clear();
                return r;
            }
        };

        ARest rest = new ARest();

        DF_Service service = new DF_Service()
                .configureExecutor(new TaskExecutorSimple())
                .configureService(-1,
                        new HttpService()
                                .configureApplication(-1,
                                        new HttpApplication()
                                                .configureRoot("/")
                                                .configureDataProcessor(-1,
                                                        new HttpStaticDataProcessor()
                                                                .add(new HttpResourceCollection("/aaa/*", "resource:aaa")),
                                                        new RESTHttpDataProcessor(
                                                                "/api",
                                                                new MethodsProvider[]{new XMethodsProvider()},
                                                                rest)
                                                )
                                )
                );
        transport.filter(service);

//        DF_Service service = new DF_Service(new TaskExecutorSimple());
//        transport.filter(service);
//        HttpService https = new HttpService();
//        service.getServices().addItem(https);
//
//        HttpApplication app = new HttpApplication();
//        https.getApplications().addItem(app);
//
//        HttpStaticDataProcessor httpsdp = new HttpStaticDataProcessor();
//        app.configureDataProcessor(-1, httpsdp);
//
//        HttpResourceCollection httprc = new HttpResourceCollection("/aaa/*", "resource:aaa");
//        httpsdp.add(httprc);
//
//        DataProcessor restdp = new RESTHttpDataProcessor(
//                "/api",
//                new MethodsProvider[]{new XMethodsProvider()},
//                rest);
//        app.configureDataProcessor(-1, restdp);
        // open pseudo-channel
        AChannel ch = new AChannel();
        // inform service of new data channel
        service.onProviderEvent(ch, DM.PN_OPENED, System.currentTimeMillis());
        File folder = new File("./src/test/resources");
        Map resp = new LinkedHashMap();
        for (Object[] oo : new Object[][]{
            {"/aaa/1.html", "Accept-Encoding: gzip, deflate, br"},
            {"/aaa/1.html"},
            {"/aaa/1.png", "Accept-Encoding: gzip, deflate, br"},
            {"/aaa/1.png"},
            {"/aaa/1.png?w=300", "Accept-Encoding: gzip, deflate, br"},
            {"/aaa/1.png?h=300", "Accept-Encoding: gzip, deflate, br"},
            {"/aaa/1.png?w=399&f=JPEG", "Accept-Encoding: gzip, deflate, br"},
            {"/aaa/jquery.js", "Accept-Encoding: gzip, deflate, br"},
            {"/aaa/jquery.js"},
            {"/api/a"},
            {"/api/b?text=abcdefghij"},
            {"/api/d?delay=10"},
            {"/api/d?delay=100"},
            {"/api/d?delay=200"},
            {"/api/d?delay=300"},
            {"/api/d?delay=500"}
        }) {
            Object o = call(transport, ch, folder, oo, false);
            if (o != null) {
                resp.put(oo[0], o);
                ByteBuffer bb = ByteBuffer.wrap((byte[]) o);
                if (bb.limit() > 1000) {
                    bb.limit(1000);
                }
                System.out.println("  | " + BufferTools.toText("ISO-8859-1", bb).replace("\n", "\n  | "));
            }
        }

        // close pseudo-channel
        ch.close();
        service.delete(ch);
        System.out.println("--------------- channel closed: no requests should be handled from now");

        call(transport, ch, folder, new Object[]{"/aaa/1.html"}, false);
        call(transport, ch, folder, new Object[]{"/aaa/1.html"}, false);
        
        System.out.println("--------------- stat");
        Map sts = service.getStatistics();
        for (Object key : sts.keySet()) {
            Object obj = sts.get(key);
            boolean isCollection = obj instanceof Collection;
            boolean isMap = obj instanceof Map;
            System.out.println("  ---------------------------------------------------- "
                    + key
                    + (!(isCollection || isMap)
                            ? (": " + obj).replace("\n", "\n    ")
                            : isCollection
                                    ? ": size=" + ((Collection) obj).size()
                                    : isMap
                                            ? ": size=" + ((Map) obj).size()
                                            : ""));
            if (obj instanceof Collection) {
                for (Object st : (Collection) obj) {
                    System.out.println("  ---------------------------------------------\n    " + ("" + st).replace("\n", "\n    "));
                }
            } else if (obj instanceof Map) {
                for (Object key2 : ((Map) obj).keySet()) {
                    System.out.println("  ---- " + key2 + ": " + ("" + ((Map) obj).get(key2)).replace("\n", "\n    "));
                }
            }
        }
    }
}
