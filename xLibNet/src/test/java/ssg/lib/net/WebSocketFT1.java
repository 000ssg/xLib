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
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.LinkedHashMap;
import java.util.Map;
import ssg.lib.common.net.NetTools;
import ssg.lib.websocket.WebSocket;
import ssg.lib.websocket.WebSocketProcessor;
import ssg.lib.websocket.WebSocketProcessor.DumpMessageListener;
import ssg.lib.websocket.WebSocketProcessor.WebSocketMessageListener;
import ssg.lib.websocket.impl.WebSocketChannel;

/**
 *
 * @author 000ssg
 */
public class WebSocketFT1 {

    public static class WSS implements Runnable {

        ServerSocketChannel serverSocket;
        Map<Integer, Thread> handlers = new LinkedHashMap<Integer, Thread>();
        Thread listener;
        public boolean DUMP_FRAMES = false;
        public boolean DUMP_MESSAGES = false;

        public WSS(int port) throws IOException {
            serverSocket = ServerSocketChannel.open();
            serverSocket.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), port));
            listener = new Thread(this);
            listener.setDaemon(true);
            listener.start();
        }

        @Override
        public void run() {
            Thread.currentThread().setName("WSS");
            while (serverSocket != null && serverSocket.isOpen()) {
                try {
                    final SocketChannel socket = serverSocket.accept();
                    final WebSocket ws = new WebSocketChannel(socket);
                    //((WebSocketChannel)ws).DUMP=true;
                    long timeout = System.currentTimeMillis() + 10000;
                    ws.handshake();
                    while (!ws.isInitialized() && System.currentTimeMillis() < timeout) {
                        NetTools.delay(1);
                    }
                    if (ws.isInitialized()) {

                        //ws.setProcessor(new WebSocketProcessor(ws));
                        ws.getProcessor().DUMP_FRAMES = DUMP_FRAMES;
                        ws.getProcessor().addWebSocketMessageListener(
                                new WebSocketMessageListener() {
                            @Override
                            public void onMessage(WebSocket ws, String text, byte[] data) {
                                if (text != null && text.startsWith("{")) {
                                    try {
                                        ws.send("Got input as JSON: " + text);
                                    } catch (Throwable th) {
                                    }
                                }
                            }

                            @Override
                            public void onPong(WebSocket ws, byte[] pong, Long delay) {
                            }

                            @Override
                            public void onEstablished(WebSocket ws) {
                            }

                            @Override
                            public void onStopped(WebSocket ws) {
                            }
                        }
                        );
                        if (DUMP_MESSAGES) {
                            ws.getProcessor().addWebSocketMessageListener(new WebSocketProcessor.DumpMessageListener());
                        }
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                    break;
                }
            }
        }

        public void close() {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (Throwable th) {
                }
                serverSocket = null;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        WSS wss = new WSS(55555);
        wss.DUMP_FRAMES = false;
        wss.DUMP_MESSAGES = true;

        System.out.println("Started at: " + System.currentTimeMillis());

        for (int ii = 0; ii < 3; ii++) {
            SocketChannel socket = SocketChannel.open(new InetSocketAddress(InetAddress.getByName("localhost"), 55555));
            WebSocket wc = new WebSocketChannel(socket);
            wc.handshake(
                    "0.1",
                    "chat",
                    "localhost",
                    null,
                    null,
                    new String[]{"timestamp; keepOffset=true", "gzipped"},
                    0,
                    null);
            while (!wc.isInitialized()) {
                wc.fetch();
                NetTools.delay(1);
            }
            if (wc.isInitialized()) {
                wc.getProcessor().addWebSocketMessageListener(new DumpMessageListener() {
                    @Override
                    public void onMessage(WebSocket ws, String text, byte[] data) {
                        super.onMessage(ws, text, data);
                    }
                });
                //wc.getProcessor().addWebSocketMessageListener(new WebSocketProcessor.DumpMessageListener());
                for (int i = 0; i < 5; i++) {
                    String ping = "Ping-Pong" + "_" + i;
                    long pps = System.nanoTime();
                    String pong = new String(wc.ping(ping.getBytes(), false));
                    System.out.println(ping.equals(pong) + " [" + (System.nanoTime() - pps) / 1000000f + "ms.]: " + ping + " -> " + pong);
                }

                String s = "Hello, World [" + ii + "]. "
                        + "Hello, World. "
                        + "Hello, World. "
                        + "Hello, World. "
                        + "Hello, World. "
                        + "Hello, World. ";

                wc.send(s);
                wc.send(s.getBytes("UTF-8"));
                wc.send(java.util.Base64.getEncoder().encodeToString((s).getBytes()).getBytes());
                wc.send("{\"ii\": " + ii + ",\"a\": 1123.5}");

            }

            Thread.sleep(100);
            wc.close(WebSocket.CR_Going_Away, "Ha-ha-ha".getBytes());
        }
        Thread.sleep(100);
    }
}
