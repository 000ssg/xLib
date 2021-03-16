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
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import ssg.lib.common.TaskExecutor;
import ssg.lib.cs.ws.WebSocket_CSGroup;
import ssg.lib.di.DI;
import ssg.lib.http.HttpConnectionUpgrade;
import ssg.lib.http.HttpService;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.base.HttpRequest;
import ssg.lib.http.base.HttpResponse;
import ssg.lib.http.dp.HttpResourceBytes;
import ssg.lib.http.dp.HttpResourceCollection;
import ssg.lib.http.dp.HttpStaticDataProcessor;
import ssg.lib.http.rest.MethodsProvider;
import ssg.lib.http.rest.RESTHttpDataProcessor;
import ssg.lib.http.rest.ReflectiveMethodsProvider;
import ssg.lib.net.CS;
import ssg.lib.net.TCPHandler;
import ssg.lib.service.DF_Service;
import ssg.lib.service.Repository;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.cs.WAMPClient_WSProtocol;
import ssg.lib.wamp.cs.WAMPRouter_WSProtocol;
import ssg.lib.wamp.cs.WSCSCounters;
import ssg.lib.stat.Statistics;

/**
 *
 * @author 000ssg
 */
public class WAMP_CS_UI extends CS {

    // WAMP WebSocket-based router support
    WAMPRouter_WSProtocol router = new WAMPRouter_WSProtocol();

    // WAMP WebSocket-based clients support
    WAMPClient_WSProtocol clients = new WAMPClient_WSProtocol();

    // WebSockets CS group: router+client
    WebSocket_CSGroup wsGroup = new WebSocket_CSGroup(router, clients);

    // counters
    WSCSCounters counters = new WSCSCounters(router, clients);
    WSCSCountersREST countersREST = new WSCSCountersREST();

    // HTTP support
    int[] httpPorts;
    DI httpDI;
    HttpService httpService;

    public WAMP_CS_UI(int httpPort, int wsPort) throws IOException {
        router.getRouter()
                .configureAgent("WAMP_CS")
                .configure(WAMPFeature.registration_meta_api, WAMPFeature.shared_registration);
        router.getRouter().setMaxPendingMessagesQueue(0);

        initHttp(httpPort);
        if (wsPort > 0) {
            if (wsPort != httpPort) {
                wsGroup.addListenerAt(this, InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), wsPort);
            }
        }
        this.addCSGroup(wsGroup);
    }

    public void initHttp(int... httpPorts) {
        if (httpDI == null) {
            // pre-load favicon image
            byte[] buf = new byte[1024 * 32];
            try {
                InputStream is = getClass().getClassLoader().getResourceAsStream("icon.png");
                int c = is.read(buf);
                buf = Arrays.copyOf(buf, c);
            } catch (Throwable th) {
                String[] ss = (""
                        + "89 50 4E 47 0D 0A 1A 0A 00 00 00 0D 49 48 44 52"
                        + " 00 00 00 5A 00 00 00 5A 08 04 00 00 00 92 A1 89"
                        + " 89 00 00 00 02 62 4B 47 44 00 FF 87 8F CC BF 00"
                        + " 00 01 61 49 44 41 54 78 DA ED 9B B1 2E 44 41 18"
                        + " 46 CF 0D 41 14 12 89 46 A2 97 8D 52 43 A1 96 E0"
                        + " 15 A8 3C 83 F5 00 B6 C2 AE 27 50 51 A9 29 BC 81"
                        + " 46 A7 51 68 48 48 76 37 2E 85 90 D8 5C 95 C4 26"
                        + " 7E F5 F7 5F DF 99 17 38 C5 DC 39 33 93 B9 60 8C"
                        + " 31 C6 88 30 4E 9B 1E D5 8F 71 CF 1E A3 DA D2 ED"
                        + " 21 E1 EF 71 A2 2D DD FB 55 BA A2 99 51 7A C0 9A"
                        + " AE 74 27 90 AE 28 69 A8 4A 4F 70 15 6A DF 31 A3"
                        + " AA 3D CB 43 A8 7D A9 BB 8E 2C F2 16 6A 1F EA CE"
                        + " EC CD 50 BA 62 3B DB 7A 5D 51 F1 C1 8A AA F4 08"
                        + " E7 A1 F6 23 73 AA DA 53 DC 84 DA D7 4C AA 6A CF"
                        + " F3 1C 6A 9F 51 A8 6A AF F2 19 6A 0B 87 BD 19 4A"
                        + " 0F D8 D0 D5 3E 0E B5 5F 59 70 D8 1D 76 87 DD 61"
                        + " 77 D8 1D 76 87 DD 61 77 D8 6B 15 F6 25 DE 1D 76"
                        + " 87 BD 86 61 9F E6 36 63 D8 1B 94 A1 F6 BE EE CC"
                        + " 8E C3 DE CD 28 FD E4 E9 F1 9F 3F C4 94 4B 5E 27"
                        + " 5F 5C B6 F2 65 7C 39 DF 86 29 E1 D6 34 E1 21 A0"
                        + " E0 34 DF 71 6B 37 DF C1 F6 AF 2B 84 1D D5 CB 9A"
                        + " 32 DB 65 4D CA 68 5F 38 DA 8E B6 A3 ED 68 3B DA"
                        + " 8E B6 A3 ED 68 3B DA 8E B6 A3 5D B7 68 C3 51 A8"
                        + " FC A2 FB 14 B9 1F 46 7B 1D 59 52 3E AF 8F 7E 64"
                        + " 28 94 A5 C7 38 A0 3B 24 DC A7 A5 FE CB 88 31 C6"
                        + " 18 13 F2 05 BA 9D C6 92 6A 5E C1 6A 00 00 00 00"
                        + " 49 45 4E 44 AE 42 60 82").split(" ");
                buf = new byte[ss.length];
                for (int i = 0; i < buf.length; i++) {
                    buf[i] = (byte) Integer.parseInt(ss[i], 16);
                }
            }

            // Http service configuration, used to build data handler for given DF_SErvice/HttpService
            httpService = new HttpService()
                    .configureDataProcessor(-1, new HttpStaticDataProcessor()
                            .add(new HttpResourceBytes(buf, "/favicon.ico", "image/png")));

            httpDI = new DF_Service()
                    .configureExecutor(new TaskExecutor.TaskExecutorPool())
                    .configureService(-1, httpService)
                    .buildDI();
        }

        // register HTTP listeners
        for (int port : httpPorts) {
            if (!hasTCPHandler(port)) {
                try {
                    // build TCP connection data handler for any listening interface at given port to handle pre-configured HttpService...
                    TCPHandler tcplHttp = new TCPHandler(
                            new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), port)
                    )
                            .defaultHandler(httpDI);
                    add(tcplHttp);
                } catch (IOException ioex) {
                    ioex.printStackTrace();
                }
            }
        }
    }

    public int addWSListener(int wsPort) throws IOException {
        if (wsPort > 0 && !hasTCPHandler(wsPort)) {
            wsGroup.addListenerAt(this, InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), wsPort);
            return wsPort;
        }
        return -1;
    }

    public String addWSListener(final String path) throws IOException {
        if (path != null && path.startsWith("/") && httpService != null) {
            if (httpService.getConnectionUpgrades() == null) {
                httpService.setConnectionUpgrades(new Repository<HttpConnectionUpgrade>());
            }
            httpService.getConnectionUpgrades().addItem(wsGroup.createWebSocketConnectionUpgrade(path, null));
            return path;
        }
        return null;
    }

    public HttpService getHttpService() {
        return httpService;
    }

    public class WSCSCountersREST {

        public Map getStructure(Boolean activeOnly) {
            long timestamp = System.currentTimeMillis();
            Map r = counters.getTree((activeOnly != null) ? activeOnly : false);
            stat2rest(r, false);
            r.put("timestamp", timestamp);
            return r;
        }

        public Map getModified() {
            Map r = counters.getLastModified();
            stat2rest(r, false);
            return r;
        }

        public Map getUpdates(Long after, Boolean activeOnly, Boolean compact) {
            long timestamp = System.currentTimeMillis();
            Map r = counters.getUpdates((after != null) ? after : 0, (activeOnly != null) ? activeOnly : false, (compact != null) ? compact : true);
            stat2rest(r, (compact != null) ? compact : true);
            r.put("timestamp", timestamp);
            return r;
        }

        <T> T stat2rest(Object object, boolean compact) {
            if (object instanceof Map) {
                Map map = (Map) object;
                for (Object key : map.keySet()) {
                    Object v = map.get(key);
                    Object v2 = stat2rest(v, compact);
                    if (v2 != v) {
                        map.put(key, v2);
                    }
                }
            } else if (object instanceof Statistics) {
                return (T) ((Statistics) object).dumpStatistics(compact);
            } else if (object instanceof Collection) {
                Map changed = null;
                Iterator it = ((Collection) object).iterator();
                while (it.hasNext()) {
                    Object o = it.next();
                    Object o2 = stat2rest(o, compact);
                    if (o2 != o) {
                        if (changed == null) {
                            changed = new HashMap();
                        }
                        changed.put(o, o2);
                    }
                }
                if (changed != null) {
                    List l = new ArrayList();
                    for (Object o : ((Collection) object)) {
                        if (changed.containsKey(o)) {
                            l.add(changed.get(o));
                        } else {
                            l.add(o);
                        }
                    }
                    object = l;
                }
            } else if (object != null && object.getClass().isArray() && !object.getClass().getComponentType().isPrimitive()) {
                Object[] r = new Object[Array.getLength(object)];
                boolean changed = false;
                for (int i = 0; i < r.length; i++) {
                    Object o = Array.get(object, i);
                    Object o2 = stat2rest(o, compact);
                    r[i] = o2;
                    if (o2 != o) {
                        changed = true;
                    }
                }
                if (changed) {
                    object = r;
                }
            }
            return (T) object;
        }
    }

    public static void main(String... args) throws Exception {
        int httpPort = 30011;
        int wsPort = 30020;
        // channel socket service
        WAMP_CS_UI cs = new WAMP_CS_UI(httpPort, wsPort);
        System.out.println("Added http ws path: " + cs.addWSListener("/extended/ws"));

        RESTHttpDataProcessor rest = new RESTHttpDataProcessor("/rest") {
            @Override
            public void onPrepareSend(HttpData data) throws IOException {
                super.onPrepareSend(data);
                HttpResponse resp = (data instanceof HttpRequest) ? ((HttpRequest) data).getResponse() : (data instanceof HttpResponse) ? (HttpResponse) data : null;
                if (resp != null) {
                    resp.addHeader(resp.HH_ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                }
            }
        };
        rest.registerProviders(new MethodsProvider[]{new ReflectiveMethodsProvider()}, cs.countersREST);
        cs.getHttpService().getDataProcessors(null, null).addItem(rest);
        cs.getHttpService().getDataProcessors(null, null).addItem(new HttpStaticDataProcessor()
                .add(new HttpResourceCollection("/*", "src/test/resources/ui"))
                //.add(new HttpResourceCollection("/*", "resource:ui"))
                .noCacheing()
        );

        cs.start();
        System.out.println("------------------------------ CS:\n--    " + cs.toString().replace("\n", "\n--    ") + "\n-----------------...");
        System.out.println("--    WS port: " + wsPort + "\n--    HTTP port: " + httpPort + "\n-----------------------");
        Thread.sleep(1000 * 60 * 60);
        cs.stop();
    }
}
