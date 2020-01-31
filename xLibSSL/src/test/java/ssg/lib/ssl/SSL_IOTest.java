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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.ssl.SSLTools.SSLHelper;

/**
 *
 * @author 000ssg
 */
public class SSL_IOTest {

    public SSL_IOTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of isSSLHandshake method, of class SSL_IO.
     */
    @Test
    public void testIsSSLHandshake() {
        System.out.println("isSSLHandshake");
        assertEquals(false, SSL_IO.isSSLHandshake(null));
        assertEquals(false, SSL_IO.isSSLHandshake(ByteBuffer.wrap(new byte[]{})));
        assertEquals(true, SSL_IO.isSSLHandshake(ByteBuffer.wrap(new byte[]{22})));
        assertEquals(true, SSL_IO.isSSLHandshake(ByteBuffer.wrap(new byte[]{22, 1})));
        assertEquals(false, SSL_IO.isSSLHandshake(ByteBuffer.wrap(new byte[]{21})));
        assertEquals(false, SSL_IO.isSSLHandshake(ByteBuffer.wrap(new byte[]{21, 2})));
    }

    /**
     * Test of isSSLData method, of class SSL_IO.
     */
    @Test
    public void testIsSSLData() {
        System.out.println("isSSLData");
        assertEquals(false, SSL_IO.isSSLData(null));
        assertEquals(false, SSL_IO.isSSLData(ByteBuffer.wrap(new byte[]{})));
        assertEquals(true, SSL_IO.isSSLData(ByteBuffer.wrap(new byte[]{23})));
        assertEquals(true, SSL_IO.isSSLData(ByteBuffer.wrap(new byte[]{23, 1})));
        assertEquals(false, SSL_IO.isSSLData(ByteBuffer.wrap(new byte[]{21})));
        assertEquals(false, SSL_IO.isSSLData(ByteBuffer.wrap(new byte[]{21, 2})));
    }

    /**
     * Test of isInitialized method, of class SSL_IO.
     */
    @Test
    public void testIsInitialized() throws Exception {
        System.out.println("isInitialized");
        SSL_IO instance = createSSL(false, false);
        assertEquals(true, instance.isInitialized());
    }

    /**
     * Test of isSecure method, of class SSL_IO.
     */
    @Test
    public void testIsSecure() throws Exception {
        System.out.println("isSecure");
        SSL_IO instance = createSSL(false, false);
        assertEquals(false, instance.isSecure());
    }

    /**
     * Test of decode method, of class SSL_IO.
     */
    @Test
    public void testDecode() throws Exception {
        System.out.println("decode");

        String[] test = new String[]{
            "Line 1",
            "Line # 2",
            "Line is 3",
            "Line is last"
        };

        Collection<ByteBuffer> bufs = createTestLs(test);
        long bc = BufferTools.getRemaining(bufs);
        SSL_IO instance = createSSL(false, false);
        List<ByteBuffer> result = instance.decode(bufs);
        assertEquals(0L, BufferTools.getRemaining(bufs));
        assertEquals(1, result.size());
        assertEquals(countBytes(test), result.get(0).remaining());
    }

    /**
     * Test of encode method, of class SSL_IO.
     */
    @Test
    public void testEncode() throws Exception {
        System.out.println("encode");
        String[] test = new String[]{
            "Line 1",
            "Line # 2",
            "Line is 3",
            "Line is last"
        };

        Collection<ByteBuffer> bufs = createTestLs(test);
        long bc = BufferTools.getRemaining(bufs);
        SSL_IO instance = createSSL(false, false);
        List<ByteBuffer> result = instance.encode(bufs);
        assertEquals(0L, BufferTools.getRemaining(bufs));
        assertEquals(1, result.size());
        assertEquals(countBytes(test), result.get(0).remaining());
    }

    /**
     * Test of encode method, of class SSL_IO.
     */
    @Test
    public void testHandshake() throws Exception {
        System.out.println("test handshake");

        String[] test = new String[]{
            "Line 1",
            "Line # 2",
            "Line is 3",
            "Line is last"
        };
        List<ByteBuffer> tbufs = createTestLs(test);

        SSL_IO server = createSSL(true, false);
        SSL_IO client = createSSL(true, true);
        client.ssl.setNeedClientAuth(true);

        boolean done = server.isInitialized() && client.isInitialized();

        List<ByteBuffer> bufs = null;
        while (!done) {
            done = server.isInitialized() && client.isInitialized();
            System.out.println("SD[0]: " + BufferTools.getRemaining(bufs));
            bufs = server.decode(bufs);
            System.out.println("SD[1]: " + BufferTools.getRemaining(bufs) + (done ? "  " + BufferTools.toText(null, bufs).replace("\n", "\n  ") : ""));
            bufs = server.encode(bufs);
            System.out.println("CD[0]: " + BufferTools.getRemaining(bufs));
            bufs = client.decode(bufs);
            //System.out.println("CD[1]: "+BufferTools.getRemaining(bufs));
            System.out.println("CD[1]: " + BufferTools.getRemaining(bufs) + (done ? "  " + BufferTools.toText(null, bufs).replace("\n", "\n  ") : ""));
            bufs = client.encode(bufs);
            done = server.isInitialized() && client.isInitialized();
            if (done && !tbufs.isEmpty()) {
                done = false;
                bufs = client.encode(tbufs);
                tbufs.clear();
            }
        }

        int a = 0;
    }

    public static SSL_IO createSSL(boolean secure, boolean client) throws SSLException {
        if (secure) {
            SSLContext ctx = getSSLContext();
            SSLEngine ssl = ctx.createSSLEngine();
            ssl.setUseClientMode(client);
            return new SSL_IO(ssl, null);
        } else {
            return new SSL_IO(null, null);
        }
    }

    static SSLContext context;

    public static SSLContext getSSLContext() {
        if (context == null) {
            try {
                SSLHelper sa = SSLTools.createSSLHelper(
                        SSL_IOTest.class.getClassLoader().getResource("keystore.p12"),
                        "passw0rd",
                        "passw0rd",
                        SSL_IOTest.class.getClassLoader().getResource("keystore.p12"),
                        "passw0rd");
                System.out.println("SA: " + sa);
                context = sa.createSSLContext("TLS", false);
            } catch (Throwable th) {
            }
        }
        return context;
    }

    public static int countBytes(String... texts) {
        int tc = 0;
        for (String s : texts) {
            if (s != null) {
                tc += s.getBytes().length;
            }
        }
        return tc;
    }

    public static ByteBuffer[] createTestBBs(String... texts) {
        ByteBuffer[] r = new ByteBuffer[(texts != null) ? texts.length : 0];
        if (texts != null) {
            for (int i = 0; i < texts.length; i++) {
                if (texts[i] != null) {
                    r[i] = ByteBuffer.wrap(texts[i].getBytes());
                }
            }
        }
        return r;
    }

    public static List<ByteBuffer> createTestLs(String... texts) {
        List<ByteBuffer> r = new ArrayList<>();
        if (texts != null) {
            for (int i = 0; i < texts.length; i++) {
                if (texts[i] != null) {
                    r.add(ByteBuffer.wrap(texts[i].getBytes()));
                }
            }
        }
        return r;
    }
}
