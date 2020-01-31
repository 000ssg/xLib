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
import ssg.lib.http.HttpConnectionUpgrade;
import ssg.lib.http.HttpMatcher;
import ssg.lib.http.di.Http2DI;
import ssg.lib.http.dp.HttpWSDataProcessor;
import ssg.lib.net.CS;
import ssg.lib.net.TCPHandler;
import ssg.lib.service.DF_Service;
import ssg.lib.service.DataProcessor;
import ssg.lib.service.Repository;
import ssg.lib.websocket.WebSocket;
import ssg.lib.websocket.WebSocketProcessor.WebSocketMessageListener;
import ssg.lib.websocket.impl.HttpConnectionUpgradeWS;

/**
 *
 * @author 000ssg
 */
public class DemoApp_2 {

    public static void main(String... args) throws Exception {
        int httpPort = 18111;

        // socket channel listener
        CS server = new CS();
        server.start();

        // service data processor: passes data via registered service processor(s)
        DF_Service service = new DF_Service(new TaskExecutor.TaskExecutorPool());

        // Http service configuration, used to build data handler for given DF_SErvice/HttpService
        Http2DI http = new Http2DI();

        // web-socket support for HttpService
        HttpConnectionUpgrade httpcu = new HttpConnectionUpgradeWS();
        if (http.getHttp().getConnectionUpgrades() == null) {
            http.getHttp().setConnectionUpgrades(new Repository<HttpConnectionUpgrade>());
        }
        http.getHttp().getConnectionUpgrades().addItem(httpcu);
        // configure for test
        WebSocket.allProtocols.add("protocolOne");

        if (http.getHttp().getDataProcessors(null, null) == null) {
            http.getHttp().setDataProcessors(new Repository<DataProcessor>());
        }

        http.getHttp().getDataProcessors(null, null).addItem(new HttpWSDataProcessor(
                new HttpMatcher("/*") {
            @Override
            public float match(HttpMatcher rm) {
                return super.match(rm); //To change body of generated methods, choose Tools | Templates.
            }
        },
                new WebSocketMessageListener() {
            @Override
            public void onMessage(WebSocket ws, String text, byte[] data) {
                try {
                    System.out.println("WS MESSAGE: " + ((text != null)
                            ? text
                            : (data != null)
                                    ? new String(data, "ISO-8859-1")
                                    : ""));
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
        }
        )
        );

        // get data handler for DF_Service with HttpService
        DI di = http.buildHandler(service);

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
