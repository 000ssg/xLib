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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import ssg.lib.common.TaskExecutor;
import ssg.lib.di.DI;
import ssg.lib.http.HttpService;
import ssg.lib.http.dp.HttpResourceCollection;
import ssg.lib.http.dp.HttpStaticDataProcessor;
import ssg.lib.net.CS;
import ssg.lib.net.TCPHandler;
import ssg.lib.service.DF_Service;
import ssg.lib.service.DataProcessor;
import ssg.lib.service.Repository;
import ssg.lib.websocket.WebSocket;
import ssg.lib.websocket.WebSocketProcessor.WebSocketMessageListener;
import ssg.lib.websocket.impl.HttpConnectionUpgradeWS;

/**
 * Demonstrates HTTP protocol upgrade to WS. To test open WS.html either as
 * standalone file or at http://localhost:18111/WS.html. Click on "Check status"
 * to verify connection is established (value is 1). Write message text and
 * click on "Send message" to send text to server. See server output for
 * received message .
 *
 * @author 000ssg
 */
public class DemoApp_2 {

    public static void main(String... args) throws Exception {
        int httpPort = 18111;

        // socket channel listener
        CS server = new CS();
        server.start();

        // "recognize" protocol "protocolOne" as it is used in demo WS.html page
        WebSocket.allProtocols.add("protocolOne");

        HttpService http = new HttpService()
                .configureConnectionUpgrade(-1, new HttpConnectionUpgradeWS()
                        .configure(new WebSocketMessageListener() {
                            @Override
                            public void onMessage(WebSocket ws, String text, byte[] data) {
                                try {
                                    System.out.println("WS MESSAGE: " + ((text != null)
                                            ? text
                                            : (data != null)
                                                    ? new String(data, "ISO-8859-1")
                                                    : ""));
                                    // send echo message
                                    ws.send("ECHO: " + (text != null ? text : data!=null ? new String(data, "ISO8859-1") : "<no text/data>"));
                                } catch (Throwable th) {
                                    System.err.println("WS ERROR: " + th);
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
                        }));
        if (http.getDataProcessors(null, null) == null) {
            http.setDataProcessors(new Repository<DataProcessor>());
        }
        http.getDataProcessors(null, null).addItem(new HttpStaticDataProcessor()
                .add(new HttpResourceCollection("/*", "src/test/resources/"))
                .noCacheing()
        );

        // service data processor: passes data via registered service processor(s)
        DF_Service service = new DF_Service()
                .configureExecutor(new TaskExecutor.TaskExecutorPool())
                .configureService(-1, http);

        // get data handler for DF_Service with HttpService
        DI di = service.buildDI();

        // build TCP connection data handler for any listening interface at given port to handle pre-configured HttpService...
        TCPHandler tcplHttp = new TCPHandler(
                new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), httpPort)
        )
                .defaultHandler(di);

        // start listening
        System.out.println("\n-------------------------- Starting: " + tcplHttp);
        server.add(tcplHttp);
        Thread.sleep(1000);
        System.out.println("\n-------------------------- Running : " + tcplHttp);

        Thread.sleep(1000 * 60 * 60 * 15);

        server.stop();

    }
}
