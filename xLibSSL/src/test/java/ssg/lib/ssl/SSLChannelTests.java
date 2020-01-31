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
package ssg.lib.ssl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import ssg.lib.common.buffers.BufferTools;

/**
 *
 * @author sesidoro
 */
public class SSLChannelTests {

    public static boolean DEBUG = false;

    public static class CL {

        SocketChannel sc;

        public CL(SocketAddress addr, boolean secure) throws IOException {
            sc = SocketChannel.open(addr);
            if (secure) {
                SSL_IO sslIO = SSL_IOTest.createSSL(true, true);
                sc = new SSLSocketChannel(sc, sslIO);
            }
        }

        public CL(SocketChannel sc) throws IOException {
            this.sc = sc;
        }

        public long write(ByteBuffer... data) throws IOException {
            if (sc == null) {
                return -1;
            }
            if (DEBUG) {
                System.out.println(Thread.currentThread().getName() + ".write: " + BufferTools.getRemaining(data));
            }
            return sc.write(data);
        }

        public ByteBuffer read() throws IOException {
            if (sc == null) {
                return null;
            }
            ByteBuffer bb = ByteBuffer.allocate(1024 * 10);
            int c = sc.read(bb);
            if (c > 0) {
                bb.flip();
                if (DEBUG) {
                    System.out.println(Thread.currentThread().getName() + ".read: " + BufferTools.getRemaining(bb));
                }
                return bb;
            }
            return null;
        }

        public void close() throws IOException {
            if (sc != null) {
                try {
                    sc.close();
                } finally {
                    sc = null;
                }
            }
        }
    }

    public static void doTest(SocketAddress saddr, boolean secure) throws IOException {
        System.out.println("\ndoTest: secure=" + secure + ", address=" + saddr);
        final ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.bind(saddr);

        Thread serverProcess = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SocketChannel sc = ssc.accept();
                    if (secure) {
                        SSL_IO sslIO = SSL_IOTest.createSSL(true, false);
                        sc = new SSLSocketChannel(sc, sslIO);
                    }
                    CL cl = new CL(sc);
                    ByteBuffer bb = cl.read();
                    while (bb != null) {
                        System.out.println("  SERVER R: " + BufferTools.toText("ISO-8859-1", bb).replace("\n", "\n    "));
                        cl.write(ByteBuffer.wrap("Echo response to client:\n".getBytes()), bb);
                        bb = cl.read();
                    }
                    cl.close();
                } catch (Throwable th) {
                    th.printStackTrace();
                }
                try {
                    ssc.close();
                } catch (Throwable th) {
                    th.printStackTrace();
                } finally {
                    System.out.println("  SERVER DONE");
                }
            }
        });

        serverProcess.setDaemon(true);
        serverProcess.start();

        try {
            CL client = new CL(saddr, secure);
            client.write(ByteBuffer.wrap("Client test message".getBytes()));
            ByteBuffer bb = client.read();
            while (bb == null) {
                bb = client.read();
            }
            System.out.println("  CLIENT R: " + BufferTools.toText("ISO-8859-1", bb).replace("\n", "\n    "));
            client.close();
        } finally {
            System.out.println("  CLIENT DONE");
        }

        try {
            Thread.sleep(200);
            if (ssc.isOpen()) {
                System.out.println("  FORCE SERVER DONE...");
                ssc.close();
            }
        } catch (Throwable th) {
        }

    }

    public static void main(String... args) throws Exception {
        //System.getProperties().put("javax.net.debug", "all");
        //System.getProperties().put("javax.net.debug", "SSL,handshake");

        int testPort = 11222;
        SocketAddress saddr = new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), testPort);
        doTest(saddr, false);
        doTest(saddr, true);
    }
}
