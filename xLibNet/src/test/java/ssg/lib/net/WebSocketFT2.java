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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.SSLContext;
import ssg.lib.common.TaskExecutor.TaskExecutorSimple;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.di.DI;
import ssg.lib.di.base.BaseDI;
import ssg.lib.di.base.SSL_DF;
import ssg.lib.http.HttpConnectionUpgrade;
import ssg.lib.http.HttpDataProcessor;
import ssg.lib.http.HttpService;
import ssg.lib.service.DF_Service;
import ssg.lib.service.Repository;
import ssg.lib.ssl.SSLTools;
import ssg.lib.websocket.WebSocket;
import ssg.lib.websocket.WebSocketProcessor.DumpMessageListener;
import ssg.lib.websocket.WebSocketProcessor.WebSocketMessageListener;
import ssg.lib.websocket.impl.DI_WS;
import ssg.lib.websocket.impl.HttpConnectionUpgradeWS;
import ssg.lib.websocket.impl.WebSocketChannel;

/**
 *
 * @author 000ssg
 */
public class WebSocketFT2 extends CS {

    public static void main(String... args) throws Exception {
        //System.getProperties().put("javax.net.debug", "all");
        //System.getProperties().put("javax.net.debug", "SSL,handshake");

//        FlowTracer.addFlowTracer(new FlowTracer.SimpleFTracer("    WebSocketFT2 | ").addScope(
//                //                FT_SCOPE.add,
//                //                FT_SCOPE.get,
//                //                FT_SCOPE.addInternal,
//                //                FT_SCOPE.getInternal,
//                FT_SCOPE.error
//        ));
        // Demo WS Client-Server  instance -> just extends CS!
        WebSocketFT2 csws = new WebSocketFT2();

        // Server end-point as service owner -> passes all data via service or raises error if not all data are processed.
        DI<ByteBuffer, SocketChannel> server = new BaseDI<ByteBuffer, SocketChannel>() {
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
        // Service filter
        DF_Service<SocketChannel> service = new DF_Service<>(new TaskExecutorSimple());
        server.filter(service);

        // Configure service: listeners, handlers...
        int wsPort = 33001;
        int wsPort2 = 33002;
        int wsPort3 = 33003;
        int wssPort = 33004;

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
        // add SSL support
        SSLContext defaultSSLCtx = SSLContext.getDefault();
        SSLContext sslCtx = SSLContext.getDefault();
        if (1 == 1) {
            sslCtx = TestSSLTools.getSSLContext();
        }
        SSL_DF ssl_df_client = new SSL_DF(sslCtx, true);
        SSL_DF ssl_df_server = new SSL_DF(sslCtx, false);
        ssl_df_server.setAutodetect(true);
        ssl_df_server.setNeedClientAuth(Boolean.TRUE);

        HttpService https = new HttpService();

        if (1 == 0) {
            service.addServiceListener(new DF_Service.DebuggingDF_ServiceListener<>()
                    .includeEvents(DF_Service.DF_ServiceListener.SERVICE_EVENT.values())
                    .excludeEvents(
                            DF_Service.DF_ServiceListener.SERVICE_EVENT.read_ext,
                            DF_Service.DF_ServiceListener.SERVICE_EVENT.write_ext
                    )
            );
        }
        service.filter(ssl_df_server);
        service.getServices().addItem(https);

//        PDI_Service service = new PDI_Service();
//        PDI_Service serviceSSL = new PDI_Service(new PDI_SSL(sslh_abc.createSSLContext("TLS", false), false), null);
//
//        service.getServices().addItem(https);
//        serviceSSL.getServices().addItem(https);
        HttpConnectionUpgrade httpcu = new HttpConnectionUpgradeWS();
        https.setConnectionUpgrades(new Repository<HttpConnectionUpgrade>());
        https.getConnectionUpgrades().addItem(httpcu);

        // WS
        TCPHandler tcplHttp = new TCPHandler(new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), wsPort))
                .defaultHandler(server);
        csws.add(tcplHttp);

        if (https.getDataProcessors(null, null) == null) {
            https.setDataProcessors(new Repository<HttpDataProcessor>());
        }

        // WS-PDI
        csws.add(new TCPHandler(new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), wsPort2))
                .defaultHandler(new DI_WS() {
                    byte[] REPLY_TO = "Reply to: ".getBytes();

                    @Override
                    public boolean consumeMessage(Channel provider, WebSocket ws, Object message) throws IOException {
                        if (message == null) {
                        } else if (message instanceof byte[]) {
                            byte[] buf = new byte[((byte[]) message).length + REPLY_TO.length];
                            System.arraycopy(REPLY_TO, 0, buf, 0, REPLY_TO.length);
                            System.arraycopy((byte[]) message, 0, buf, REPLY_TO.length, ((byte[]) ((byte[]) message)).length);
                            System.out.println("  [" + Thread.currentThread().getName() + "]consumeMessage:\n    bin :" + new String((byte[]) message) + "\n    rep : " + new String(buf).replace("\n", "\n      "));
                            //ws.send(buf);
                        } else {
                            //ws.send(new String(REPLY_TO) + message);
                            System.out.println("  [" + Thread.currentThread().getName() + "]consumeMessage:\n    text:" + message + "\n    rep : " + (new String(REPLY_TO) + message).replace("\n", "\n      "));
                        }
                        return true;
                    }

                    @Override
                    public boolean checkMessage(WebSocket ws, Object message) throws IOException {
                        return message instanceof String || message instanceof byte[];
                    }
                })
        );

        // WS (PDI-short-circuited)
        csws.add(new TCPHandler(new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), wsPort3))
                .defaultHandler(
                        new DI_WS() {
                    byte[] REPLY_TO = "Reply to: ".getBytes();

//                    @Override
//                    public long toInternal(Channel provider, List to, List... from) throws IOException {
//                        long c = super.toInternal(provider, to, from);
//                        return c;
//                    }
                    @Override
                    public boolean checkMessage(WebSocket ws, Object message) throws IOException {
                        boolean b = super.checkMessage(ws, message);
                        if (message == null) {
                        } else if (message instanceof byte[]) {
                            byte[] buf = new byte[((byte[]) message).length + REPLY_TO.length];
                            System.arraycopy(REPLY_TO, 0, buf, 0, REPLY_TO.length);
                            System.arraycopy((byte[]) message, 0, buf, REPLY_TO.length, ((byte[]) ((byte[]) message)).length);
                            //System.out.println("[" + Thread.currentThread().getName() + "]checkMessage:\n  bin :" + new String((byte[]) message) + "\n  rep : " + new String(buf));
                            ws.send(buf);
                        } else {
                            ws.send(new String(REPLY_TO) + message);
                            //System.out.println("[" + Thread.currentThread().getName() + "]checkMessage:\n  text:" + message + "\n  rep : " + (new String(REPLY_TO) + message));
                        }
                        return b;
                    }

                    @Override
                    public void onInitializedWS(Channel provider, WebSocket ws) {
                        super.onInitializedWS(provider, ws);
                        //ws.getProcessor().DUMP_FRAMES = true;
                        //ws.getProcessor().addWebSocketMessageListener(new WebSocketProcessor.DumpMessageListener());
                    }

                })
        );

        // WSS
        TCPHandler tcplHttps = new TCPHandler(new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), wssPort))
                .defaultHandler(server);
        csws.add(tcplHttps);

//        csws.addManaged(new ServerConnectionTCP(
//                "WSS",
//                serviceSSL,
//                new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), wssPort)
//        ));
        csws.start();

        WebSocket ws = new WebSocketChannel(new URI("ws://localhost:" + wsPort + "/chat"));
        byte[] ping = "Hello, WS-PDI".getBytes();
        byte[] pong = ws.ping(ping, false);
        System.out.println("ping-pong:" + "\n  -> " + new String(ping) + "\n  <- " + new String(pong));

        WebSocket ws2 = new WebSocketChannel(new URI("ws://localhost:" + wsPort2 + "/chat"));
        ws2.getProcessor().addWebSocketMessageListener(new DumpMessageListener());
        byte[] ping2 = "Hello, WS-PDI-SC".getBytes();
        byte[] pong2 = ws2.ping(ping2, false);
        System.out.println("ping-pong:" + "\n  -> " + new String(ping2) + "\n  <- " + new String(pong2));
        ws2.send("Hello, as text (WS_PDI-SC)");
        ws2.send("Hello, as binary (WS-PDI-SC)".getBytes());

        WebSocket ws3 = new WebSocketChannel(new URI("ws://localhost:" + wsPort3 + "/chat"));
        byte[] ping3 = "Hello, WS".getBytes();
        byte[] pong3 = ws3.ping(ping3, false);
        System.out.println("ping-pong:" + "\n  -> " + new String(ping3) + "\n  <- " + new String(pong3));
        ws3.send("Hello, as text (WS)");
        ws3.send("Hello, as binary (WS)".getBytes());

        WebSocket wss = new WebSocketChannel(null, new URI("wss://localhost:" + wssPort + "/chat"), sslh_abc.createSSLContext("TLS", false).createSSLEngine());
        byte[] pings = "Hello, WSS".getBytes();
        byte[] pongs = wss.ping(pings, false);
        System.out.println("ping-pong:" + "\n  -> " + new String(pings) + "\n  <- " + new String(pongs));
        wss.send("Hello, as text (SSL)");
        wss.send("Hello, as binary (SSL)".getBytes());

        try {
            wss = new WebSocketChannel(new URI("wss://localhost:" + wssPort + "/chat"));
            pings = "Hello, WSS-2".getBytes();
            pongs = wss.ping(pings, false);
            System.out.println("ping-pong:" + "\n  -> " + new String(pings) + "\n  <- " + new String(pongs));
        } catch (Throwable th) {
            System.out.println("Failed to use custom PPK-based SSL server via default SSLContext:\n  " + BufferTools.dummpErrorMessages(th).replace("\n", "\n  "));
        }

        if (defaultSSLCtx != sslCtx) {
            try {
                SSLContext.setDefault(sslCtx);
                wss = new WebSocketChannel(new URI("wss://localhost:" + wssPort + "/chat"));
                pings = "Hello, WSS-3".getBytes();
                pongs = wss.ping(pings, false);
                System.out.println("ping-pong:" + "\n  -> " + new String(pings) + "\n  <- " + new String(pongs));
            } catch (Throwable th) {
                System.out.println("Failed to use custom PPK-based SSL server via default SSLContext:\n  " + BufferTools.dummpErrorMessages(th).replace("\n", "\n  "));
            } finally {
                SSLContext.setDefault(defaultSSLCtx);
            }
        }

        // stress -> send multiple var size messages...
        ws = new WebSocketChannel(new URI("ws://localhost:" + wsPort3 + "/chat"));
        System.out.println("Stress test at " + ws);
        final String[] wsText = new String[1];
        final byte[][] wsBin = new byte[1][];
        String REPLY_TO_TEXT = "Reply to: ";
        byte[] REPLY_TO_BIN = REPLY_TO_TEXT.getBytes();
        ws.getProcessor().addWebSocketMessageListener(new WebSocketMessageListener() {
            @Override
            public void onEstablished(WebSocket ws) {
            }

            @Override
            public void onStopped(WebSocket ws) {
            }

            @Override
            public void onMessage(WebSocket ws, String text, byte[] data) {
                if (text != null) {
                    wsText[0] = text;
                    wsBin[0] = null;
                } else {
                    wsText[0] = null;
                    wsBin[0] = data;
                }
            }

            @Override
            public void onPong(WebSocket ws, byte[] pong, Long delay) {
            }
        });
        ping = "Hello, WS-PDI-SC (stress test)".getBytes();
        pong = ws.ping(ping, false);
        System.out.println("ping-pong:" + "\n  -> " + new String(ping) + "\n  <- " + new String(pong));
        for (int i = 0; i < 100; i += 20) {
            StringBuilder sb = new StringBuilder();
            sb.append("Hello, as text (WS-PDI-SC-stress): " + i + ": ");
            for (int j = 0; j < i * 100; j++) {
                sb.append((char) (32 + ((Math.random() * 127) % (127 - 32))));
            }
            ws.send(sb.toString());
            Thread.sleep(200);
            if (wsText[0] != null) {
                if (wsText[0].length() == (sb.length() + REPLY_TO_TEXT.length())) {
                    System.out.println("   OK text reply corresponds to resuest");
                } else {
                    System.out.println("   ERROR text reply: need " + (sb.length() + REPLY_TO_TEXT.length()) + ", got " + wsText[0].length());
                }
            }
            ws.send(sb.toString().getBytes());
            Thread.sleep(200);
            if (wsBin[0] != null) {
                if (wsBin[0].length == (sb.toString().getBytes().length + REPLY_TO_BIN.length)) {
                    System.out.println("   OK bin reply corresponds to resuest");
                } else {
                    System.out.println("   ERROR bin reply: need " + (sb.toString().getBytes().length + REPLY_TO_BIN.length) + ", got " + wsBin[0].length);
                }
            }
        }

        csws.stop();
    }
}
