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
package ssg.lib.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.di.DI;
import ssg.lib.di.base.BaseDI;
import ssg.lib.service.EchoService.EchoDataProcessor;

/**
 *
 * @author 000ssg
 */
public class FTest_ABC_Service {

    public static class AC implements Channel {

        boolean closed = false;

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }

        @Override
        public String toString() {
            return "AC{" + "closed=" + closed + '}';
        }

    }

    public static void main(String... args) throws Exception {
        DI<ByteBuffer, Channel> server = new BaseDI<ByteBuffer, Channel>() {
            @Override
            public long size(Collection<ByteBuffer>... data) {
                return BufferTools.getRemaining(data);
            }

            @Override
            public void consume(Channel provider, Collection<ByteBuffer>... data) throws IOException {
                byte[] buf = BufferTools.toBytes(false, data);
                if (buf != null && buf.length > 0) {
                    System.out.println("DROPPED: " + BufferTools.toText("ISO-8859-1", ByteBuffer.wrap(buf)));
                }
            }

            @Override
            public List<ByteBuffer> produce(Channel provider) throws IOException {
                return null;
            }
        };
        DF_Service<Channel> svc = new DF_Service<>();
        svc.getServices().addItem(new ABC_ServiceProcessor<Channel>('A', 'B', 'C'));
        svc.getServices().addItem(new EchoService());
        svc.getDataProcessors().addItem(new EchoDataProcessor());

        server.filter(svc);

        Channel ch = new AC();

        for (String text : new String[]{"Another chance", "Be ready", "Choose the best", "No ABC. Just echo"}) {
            List<ByteBuffer> l = new ArrayList<>();
            l.add(ByteBuffer.wrap(text.getBytes()));
            server.write(ch, l);
            //server.write(ch, Collections.singletonList(ByteBuffer.wrap(text.getBytes())));
            List<ByteBuffer> resp = server.read(ch);
            String response = BufferTools.toText(null, resp);

            System.out.println(""
                    + "TEST: " + ("" + text).replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
                    + "\nRESP: " + ("" + response).replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
            );
        }
        ch.close();
    }
}
