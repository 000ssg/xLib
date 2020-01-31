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
package ssg.lib.di.base;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import javax.net.ssl.SSLContext;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.ssl.SSLTools;

/**
 *
 * @author 000ssg
 */
public class Test_SSL {

    static SSLContext context;

    public static SSLContext getSSLContext() {
        if (context == null) {
            try {
                SSLTools.SSLHelper sa = SSLTools.createSSLHelper(
                        Test_SSL.class.getClassLoader().getResource("keystore.p12"),
                        "passw0rd",
                        "passw0rd",
                        Test_SSL.class.getClassLoader().getResource("keystore.p12"),
                        "passw0rd");
                System.out.println("SA: " + sa);
                context = sa.createSSLContext("TLS", false);
            } catch (Throwable th) {
            }
        }
        return context;
    }

    public static void main(String... args) throws Exception {
        SSLContext ctx = getSSLContext();
        SSL_DF sdi = new SSL_DF(ctx, false);
        SSL_DF cdi = new SSL_DF(ctx, true);

        Object provider = "";

        System.out.println("Server cert: " + sdi.getRemoteCertificates(provider));
        System.out.println("Client cert: " + cdi.getRemoteCertificates(provider));

        Collection<ByteBuffer> bufs = null;
        boolean done = sdi.isInitialized(provider) && cdi.isInitialized(provider);
        // handshake
        System.out.println("\n=========================\n=== Handshake\n=========================");
        while (!done) {
            bufs = sdi.writeFilter(null, provider, bufs);
            bufs = sdi.readFilter(null, provider, bufs);
            done = sdi.isInitialized(provider) && cdi.isInitialized(provider);
            bufs = cdi.writeFilter(null, provider, bufs);
            bufs = cdi.readFilter(null, provider, bufs);
            done = sdi.isInitialized(provider) && cdi.isInitialized(provider);
        }
        System.out.println("Server cert: " + sdi.getRemoteCertificates(provider));
        System.out.println("Client cert: " + cdi.getRemoteCertificates(provider));

        // send client msg and respond with server data
        String cm = "Hello from Client.";
        String sm = "Hi from Server.";
        boolean dumpBinary = false;

        System.out.println("\n=========================\n=== Client -> Server -> Client\n=========================");
        bufs = cdi.readFilter(null, provider, Collections.singletonList(ByteBuffer.wrap(cm.getBytes())));
        if (dumpBinary) {
            System.out.println("CW: " + BufferTools.toText(null, bufs));
        }
        bufs = sdi.writeFilter(null, provider, bufs);
        System.out.println("SR: " + BufferTools.toText(null, bufs));
        bufs = sdi.readFilter(null, provider, Collections.singletonList(ByteBuffer.wrap((sm + " as response to: " + BufferTools.toText(null, bufs)).getBytes())));
        if (dumpBinary) {
            System.out.println("SW: " + BufferTools.toText(null, bufs));
        }
        bufs = cdi.writeFilter(null, provider, bufs);
        System.out.println("CR: " + BufferTools.toText(null, bufs));
        bufs.clear();

        System.out.println("\n=========================\n=== Server -> Client -> Server\n=========================");
        bufs = sdi.readFilter(null, provider, Collections.singletonList(ByteBuffer.wrap(sm.getBytes())));
        if (dumpBinary) {
            System.out.println("SW: " + BufferTools.toText(null, bufs));
        }
        bufs = cdi.writeFilter(null, provider, bufs);
        System.out.println("CR: " + BufferTools.toText(null, bufs));
        bufs = cdi.readFilter(null, provider, Collections.singletonList(ByteBuffer.wrap((cm + " as response to: " + BufferTools.toText(null, bufs)).getBytes())));
        if (dumpBinary) {
            System.out.println("CW: " + BufferTools.toText(null, bufs));
        }
        bufs = sdi.writeFilter(null, provider, bufs);
        System.out.println("SR: " + BufferTools.toText(null, bufs));
        bufs.clear();

        int a = 0;
    }

}
