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
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_TASK;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_WRAP;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
import ssg.lib.ssl.SSLTools.SSLHelper;

/**
 *
 * @author 000ssg
 */
public class SSLCertificatesTests {

    public static String verify(SSLHelper srv, SSLHelper cln, boolean needClientAuth, boolean trace) {
        StringBuilder sb = new StringBuilder();

        sb.append("--- Server SSL: " + srv.toString().replace("\n", "\n  "));
        sb.append("\n--- Client SSL: " + cln.toString().replace("\n", "\n  "));

        try {

            int port = 11111;
            SSLEngine ss = srv.createSSLContext("TLS", false).createSSLEngine("a", port);
            ss.setUseClientMode(false);
            ss.setWantClientAuth(true);
            if (needClientAuth) {
                ss.setNeedClientAuth(true);
            }
            ss.setEnableSessionCreation(true);

            SSLEngine sc = cln.createSSLContext("TLS", false).createSSLEngine();
            sc.setUseClientMode(true);

            ss.beginHandshake();
            sc.beginHandshake();

            int MAX_BUF_SZ = Math.max(sc.getSession().getApplicationBufferSize() + 32, sc.getSession().getPacketBufferSize() + 32);

            List<ByteBuffer> s2c = new ArrayList<>();
            List<ByteBuffer> c2s = new ArrayList<>();

            ByteBuffer empty = ByteBuffer.allocate(0);
            byte[] buf = new byte[1024 * 64];
            while (!(ss.getHandshakeStatus() == HandshakeStatus.FINISHED || ss.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING)
                    || !(sc.getHandshakeStatus() == HandshakeStatus.FINISHED || sc.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING)) {

                Thread.currentThread().setName("CLIENT-THREAD");

                SSLEngineResult r = null;
                if (trace) {
                    sb.append("\nCLIENT: " + sc.getHandshakeStatus());
                }
                switch (sc.getHandshakeStatus()) {
                    case NEED_WRAP: {
                        ByteBuffer bb = ByteBuffer.allocate(MAX_BUF_SZ);
                        r = sc.wrap(empty, bb);
                        if (r.bytesProduced() > 0) {
                            c2s.add((ByteBuffer) bb.flip());
                        }
                    }
                    break;
                    case NEED_UNWRAP:
                        if (!s2c.isEmpty()) {
                            r = sc.unwrap(s2c.remove(0), ByteBuffer.wrap(buf));
                        }
                        break;
                    case NEED_TASK: {
                        Runnable run = sc.getDelegatedTask();
                        while (run != null) {
                            run.run();
                            run = sc.getDelegatedTask();
                        }
                    }
                    r = null;
                    if (sc.getHandshakeStatus() == HandshakeStatus.NEED_WRAP) {
                        {
                            ByteBuffer bb = ByteBuffer.allocate(MAX_BUF_SZ);
                            r = sc.wrap(empty, bb);
                            if (r.bytesProduced() > 0) {
                                c2s.add((ByteBuffer) bb.flip());
                            }
                        }
                    }
                    break;
                    case NOT_HANDSHAKING:
                        break;
                    case FINISHED:
                        break;
                }
                if (trace) {
                    sb.append("\nCLIENT: -> " + sc.getHandshakeStatus() + "  " + ("" + r).replace("\n", "\\n"));
                }

                Thread.currentThread().setName("SERVER-THREAD");
                r = null;
                if (trace) {
                    sb.append("\nSERVER: " + ss.getHandshakeStatus());
                }
                switch (ss.getHandshakeStatus()) {
                    case NEED_WRAP: {
                        ByteBuffer bb = ByteBuffer.allocate(MAX_BUF_SZ);
                        r = ss.wrap(empty, bb);
                        if (r.bytesProduced() > 0) {
                            s2c.add((ByteBuffer) bb.flip());
                        }
                    }
                    break;
                    case NEED_UNWRAP:
                        if (!c2s.isEmpty()) {
                            r = ss.unwrap(c2s.remove(0), ByteBuffer.wrap(buf));
                        }
                        break;
                    case NEED_TASK: {
                        Runnable run = ss.getDelegatedTask();
                        while (run != null) {
                            run.run();
                            run = ss.getDelegatedTask();
                        }
                    }
                    r = null;
                    if (ss.getHandshakeStatus() == HandshakeStatus.NEED_WRAP) {
                        ByteBuffer bb = ByteBuffer.allocate(MAX_BUF_SZ);
                        r = ss.wrap(empty, bb);
                        if (r.bytesProduced() > 0) {
                            s2c.add((ByteBuffer) bb.flip());
                        }
                    }
                    break;
                    case NOT_HANDSHAKING:
                        break;
                    case FINISHED:
                        break;
                }

                if (trace) {
                    sb.append("\nSERVER: -> " + ss.getHandshakeStatus() + "  " + ("" + r).replace("\n", "\\n"));
                }
                if (trace) {
                    sb.append('\n');
                }
                int a = 0;
            }

            try {
                sb.append("\nServer side: peer " + ss.getSession().getPeerHost() + ":" + ss.getSession().getPeerPort());
                sb.append("\n   DN: " + ((X509Certificate) ss.getSession().getPeerCertificates()[0]).getIssuerDN());
            } catch (Throwable th) {
                sb.append("\n   error: " + th);
            }

            try {
                sb.append("\nClient side: peer " + sc.getSession().getPeerHost() + ":" + sc.getSession().getPeerPort());
                sb.append("\n   DN: " + ((X509Certificate) sc.getSession().getPeerCertificates()[0]).getIssuerDN());
            } catch (Throwable th) {
                sb.append("\n   error: " + th);
            }

        } catch (Throwable th) {
            sb.append("\n   severe: " + th);
        }
        return sb.toString();
    }

    public static void main(String... args) throws Exception {
        SSLHelper sa = SSLTools.createSSLHelper(
                SSLCertificatesTests.class.getClassLoader().getResource("keystore.p12"),
                "passw0rd",
                "passw0rd",
                SSLCertificatesTests.class.getClassLoader().getResource("keystore.p12"),
                "passw0rd");
        System.out.println("SA: " + sa);

        SSLHelper s1 = SSLTools.createSSLHelper(
                SSLCertificatesTests.class.getClassLoader().getResource("ks/localhost__abc.p12"),
                "passw0rd",
                "passw0rd",
                SSLCertificatesTests.class.getClassLoader().getResource("ks/localhost__abc_ts.p12"),
                "passw0rd");
        System.out.println("\n\nS1: " + s1);

        SSLHelper s2 = SSLTools.createSSLHelper(
                SSLCertificatesTests.class.getClassLoader().getResource("ks2/localhost__efg.p12"),
                "passw0rd",
                "passw0rd",
                SSLCertificatesTests.class.getClassLoader().getResource("ks2/localhost__efg_ts.p12"),
                "passw0rd");
        System.out.println("\n\nS2: " + s2);

        SSLHelper s3 = SSLTools.createSSLHelper(
                SSLCertificatesTests.class.getClassLoader().getResource("ks_ts/localhost__abc.p12"),
                "passw0rd",
                "passw0rd",
                SSLCertificatesTests.class.getClassLoader().getResource("ks_ts/localhost__abc_ts.p12"),
                "passw0rd");
        System.out.println("\n\nS3: " + s3);

        SSLHelper tmp = s1.copy();
        System.out.println("\n\nTmp: " + tmp);
        List<String> copied = SSLTools.copyCertificates(s2.getTs(), tmp.getTs(), "key_e", "key_f");
        System.out.println("Copied certs: " + copied);
        System.out.println("Tmp: " + tmp);

        SSLHelper tmp2 = s2.copy();
        System.out.println("\n\nTmp2: " + tmp2);
        List<String> copied2 = SSLTools.copyCertificates(s1.getTs(), tmp2.getTs(), "key_s");
        System.out.println("Copied certs: " + copied2);
        System.out.println("Tmp2: " + tmp2);

        int a = 0;

        System.out.println("VERIFY: key_a\n  " + verify(tmp.limitTo("key_s"), tmp.limitTo("key_a"), false, false).replace("\n", "\n  "));
        System.out.println("VERIFY: key_b\n  " + verify(tmp.limitTo("key_s"), tmp.limitTo("key_b"), false, false).replace("\n", "\n  "));
        System.out.println("VERIFY: key_c\n  " + verify(tmp.limitTo("key_s"), tmp.limitTo("key_c"), false, false).replace("\n", "\n  "));
        System.out.println("VERIFY: key_d\n  " + verify(tmp.limitTo("key_s"), tmp.limitTo("key_d"), false, false).replace("\n", "\n  "));
        System.out.println("VERIFY: key_e\n  " + verify(tmp.limitTo("key_s"), tmp2.limitTo("key_e"), false, false).replace("\n", "\n  "));
        System.out.println("VERIFY: key_f\n  " + verify(tmp.limitTo("key_s"), tmp2.limitTo("key_f"), false, false).replace("\n", "\n  "));

    }
}
