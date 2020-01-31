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
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.di.base.BufferingDI;
import ssg.lib.di.base.EchoDI;

/**
 *
 * @author 000ssg
 */
public class CSDemo {

    public static void main(String... args) throws Exception {
        CS cs = new CS();
        //cs.addCSListener(new DebuggingCSListener());
        cs.start();
        long timeout = System.currentTimeMillis() + 1000 * 60 * 6;

        int port1 = 33301;
        //SocketAddress saddr1 = new InetSocketAddress(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), port1);
        InetAddress addr1 = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
        addr1 = InetAddress.getByName("localhost");
        SocketAddress saddr1 = new InetSocketAddress(addr1, port1);

        final SocketChannel[] tsc = new SocketChannel[1];

        TCPHandler tcp1 = new TCPHandler(saddr1) {
            @Override
            public SelectionKey[] onHandle(SelectionKey key) throws IOException {
//                if (tsc[0] != null) {
//                    String so= (SelectionKey.OP_ACCEPT & key.readyOps())!=0 ? "a" :"-";
//                     so+= (SelectionKey.OP_CONNECT & key.readyOps())!=0 ? "c" :"-";
//                     so+= (SelectionKey.OP_READ & key.readyOps())!=0 ? "r" :"-";
//                     so+= (SelectionKey.OP_WRITE & key.readyOps())!=0 ? "w" :"-";
//                    System.out.println(Integer.toBinaryString(key.readyOps()) +' '+so+ " : " + key.channel());
//                }
                SelectionKey[] r = super.onHandle(key);
                return r;
            }
        }.defaultHandler(new EchoDI.BufferEchoDI<ByteBuffer, SocketChannel>() {
            @Override
            public List<ByteBuffer> echo(SocketChannel provider, Collection<ByteBuffer>... data) {
                List<ByteBuffer> r = super.echo(provider, data);
                if (BufferTools.hasRemaining(r)) {
                    long c = BufferTools.getRemaining(r);
                    r.add(0, ByteBuffer.wrap((""
                            + "HTTP/1.1 200 OK"
                            + "\r\nServer: test"
                            + "\r\nContent-Type: text/plain"
                            + "\r\nContent-Length: " + c
                            + "\r\n\r\n").getBytes()));
                }
                return r;
            }
        });
        cs.add(tcp1);

        // Socket
        String test = "Test Socket";
        Socket socket = new Socket(addr1, port1);
        socket.getOutputStream().write(test.getBytes());
        socket.getOutputStream().flush();
        byte[] buf = new byte[1024 * 5];
        int c = socket.getInputStream().read(buf);
        System.out.println("\n--------\nTEST: " + test + "\nRESP: " + new String(buf, 0, c));
        socket.close();

        // SocketChannel
        test = "Test SocketChannel";
        SocketChannel sc = SocketChannel.open(new InetSocketAddress(addr1, port1));
        sc.write(ByteBuffer.wrap(test.getBytes()));
        c = sc.read(ByteBuffer.wrap(buf));
        System.out.println("\n--------\nTEST: " + test + "\nRESP: " + new String(buf, 0, c));
        sc.close();

        // URL.openStream
        URL url = new URL("http://localhost:" + port1 + "/testURL");
        c = url.openStream().read(buf);
        System.out.println("\n--------\nTEST URL: " + url + "\nRESP: " + new String(buf, 0, c));

        // SocketChannel (selectors)
        test = "Hello.";
        BufferingDI<ByteBuffer, SocketChannel> buffer = new BufferingDI<>();
        SocketChannel provider = tcp1.connect(saddr1, buffer);
        System.out.println("H: " + provider);
        tsc[0] = provider;
        buffer.push(provider, Collections.singletonList(ByteBuffer.wrap(test.getBytes())));
        List<ByteBuffer> bbs = buffer.fetch(provider);
        long maxTime = System.currentTimeMillis() + timeout;
        while (bbs == null && System.currentTimeMillis() < maxTime) {
            bbs = buffer.fetch(provider);
        }
        ByteBuffer bb = ByteBuffer.wrap(buf);
        c = 0;
        for (ByteBuffer bi : bbs) {
            if (bi != null && bi.hasRemaining()) {
                c += bi.remaining();
                bb.put(bi);
            }
        }
        System.out.println("\n--------\nTEST: " + test + "\nRESP: " + new String(buf, 0, c));

        cs.stop();
    }
}
