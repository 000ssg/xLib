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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import ssg.lib.common.buffers.BufferTools;

/**
 * Wraps SSLEngine (or none) functionality.
 *
 * @author 000ssg
 */
public class SSL_IO<P> {

    public static final ByteBuffer EMPTY = ByteBuffer.allocate(0);
    public static boolean DEBUG = false;
    public static boolean DEBUG_WRAP = false;
    public static boolean DEBUG_UNWRAP = false;
    public static boolean DEBUG_INITIALIZED = false;

    P provider;
    SSLEngine ssl;
    int netPacketSize = 1024 * 16;
    int appPacketSize = 1024 * 16;
    boolean initialized = false;
    ByteBuffer netIn;
    ByteBuffer netOut;

    ByteBuffer wrapCache;
    ByteBuffer unwrapCache;

    public SSL_IO(SSLEngine ssl, P provider) throws SSLException {
        this.ssl = ssl;
        this.provider = provider;
        if (ssl != null && ssl.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING) {
            netPacketSize = ssl.getSession().getPacketBufferSize();
            appPacketSize = ssl.getSession().getApplicationBufferSize();
            netIn = ByteBuffer.allocateDirect(appPacketSize);
            netOut = ByteBuffer.allocateDirect(netPacketSize);
            unwrapCache = ByteBuffer.allocateDirect(appPacketSize);
            wrapCache = ByteBuffer.allocateDirect(netPacketSize);
            ssl.beginHandshake();
        }
    }

    /**
     * Probe if 12s byte may indicate TLS HANDSHAKE packet (0x16 -> 22)
     *
     * @param bb
     * @return
     */
    public static boolean isSSLHandshake(ByteBuffer bb) {
        int check = -1;
        if (bb != null && bb.remaining() > 0) {
            int pos = bb.position();
            check = bb.get();
            bb.position(pos);
        }
        return check == 22;
    }

    /**
     * Probe if 1st byte may indicate TLS DATA packet (0x17 -> 23)
     *
     * @param bb
     * @return
     */
    public static boolean isSSLData(ByteBuffer bb) {
        int check = -1;
        if (bb != null && bb.remaining() > 0) {
            int pos = bb.position();
            check = bb.get();
            bb.position(pos);
        }
        return check == 23;
    }

    public HandshakeStatus getHandshakeStatus() {
        return ssl != null ? ssl.getHandshakeStatus() : null;
    }

    public Certificate[] getRemoteCertificates() throws IOException {
        if (isInitialized() && ssl != null) {
            try {
                return ssl.getSession().getPeerCertificates();
            } catch (SSLPeerUnverifiedException ex) {
            } catch (Throwable th) {
            }
        }
        return null;
    }

    public int getPreferredBufferSize(boolean internal) {
        return (ssl != null)
                ? (internal) ? ssl.getSession().getApplicationBufferSize() : ssl.getSession().getPacketBufferSize()
                : 1024;
    }

    public boolean isInitialized() {
        return initialized || ssl == null;
    }

    public void setInitialized(boolean initialized) {
        if (initialized && DEBUG_INITIALIZED) {
            System.out.println("[" + System.currentTimeMillis() + ", " + Thread.currentThread().getName() + "] ssl initialized (" + this.initialized + " -> " + initialized + "): " + ssl.getPeerHost() + ":" + ssl.getPeerPort() + "  " + provider
                    + "\n  net   in/out=" + BufferTools.getRemaining(netIn) + "/" + BufferTools.getRemaining(netOut)
                    + "\n  cache in/out=" + BufferTools.getRemaining(this.unwrapCache) + "/" + BufferTools.getRemaining(wrapCache)
                    + "\n" + stackTrace(2));
        }
        this.initialized = initialized;
    }

    public boolean isSecure() {
        return initialized && ssl != null && ssl.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING;
    }

    public boolean isClient() {
        return ssl != null && ssl.getUseClientMode();
    }

    public List<ByteBuffer> decode(Collection<ByteBuffer>... bufs) throws IOException {
        List<ByteBuffer> r = new ArrayList<>();
        if (!initialized) {
            if (ssl == null) {
                initialized = true;
                List<ByteBuffer> rr = BufferTools.aggregate(appPacketSize, false, bufs);
                if (rr != null && !rr.isEmpty()) {
                    r.addAll(rr);
                }
            } else {
                for (ByteBuffer bb : BufferTools.toList(true, bufs)) {
                    if (bb != null && bb.hasRemaining()) {
                        List<ByteBuffer> bs = unwrap(bb);
                        if (bs != null) {
                            r.addAll(bs);
                        }
                    }
                }
            }
        } else {
            if (ssl != null) {
                synchronized (this) {
                    for (ByteBuffer bb : BufferTools.toList(true, bufs)) {
                        if (bb != null && bb.hasRemaining()) {
                            List<ByteBuffer> bs = unwrap(bb);
                            if (bs != null) {
                                r.addAll(bs);
                            }
                        }
                    }
                }
            } else {
                synchronized (this) {
                    List<ByteBuffer> rr = BufferTools.aggregate(appPacketSize, false, bufs);
                    if (rr != null && !rr.isEmpty()) {
                        r.addAll(rr);
                    }
                }
            }
        }
        return r;
    }

    public List<ByteBuffer> encode(Collection<ByteBuffer>... bufs) throws IOException {
        List<ByteBuffer> r = new ArrayList<>();
        if (!initialized) {
            if (ssl == null) {
                initialized = true;
                List<ByteBuffer> rr = BufferTools.aggregate(appPacketSize, false, bufs);
                if (rr != null && !rr.isEmpty()) {
                    r.addAll(rr);
                }
            } else {
                synchronized (this) {
                    long c = BufferTools.getRemaining(bufs);
                    if (c > 0) {
                        for (ByteBuffer bb : BufferTools.toList(true, bufs)) {
                            ByteBuffer b = wrap(bb);
                            if (b != null && b.hasRemaining()) {
                                r.add(b);
                            }
                        }
                    } else {
                        ByteBuffer b = wrap(EMPTY);
                        if (b != null && b.hasRemaining()) {
                            r.add(b);
                        }
                    }
                }
            }
        } else {
            if (ssl != null) {
                synchronized (this) {
                    for (ByteBuffer bb : BufferTools.toList(true, bufs)) {
                        ByteBuffer b = wrap(bb);
                        if (b != null && b.hasRemaining()) {
                            r.add(b);
                        }
                    }
                }
            } else {
                synchronized (this) {
                    List<ByteBuffer> rr = BufferTools.aggregate(appPacketSize, false, bufs);
                    if (rr != null && !rr.isEmpty()) {
                        r.addAll(rr);
                    }
                }
            }
        }
        return r;
    }

    public ByteBuffer wrap(ByteBuffer bb) throws IOException {
        ByteBuffer r = null;

        switch (ssl.getHandshakeStatus()) {
            case NOT_HANDSHAKING:
            case NEED_WRAP:
                SSLEngineResult er = null;
                synchronized (netOut) {
                    er = ssl.wrap(bb, netOut);
                    if (DEBUG_WRAP) {
                        System.out.println(System.currentTimeMillis() + ": wrap  [left=" + BufferTools.getRemaining(bb) + "]  : " + er.toString().replace("\n", "\\n ") + "  " + provider
                                //+ (er.bytesProduced() == 379 ? "\n" + stackTrace(2) : "")
                        );
                    }
                    if (er.bytesProduced() > 0) {
                        r = ByteBuffer.allocate(er.bytesProduced());
                        ((Buffer) netOut).flip();
                        r.put(netOut);
                        ((Buffer) r).flip();
                        ((Buffer) netOut).clear();
                    } else if (Status.BUFFER_OVERFLOW == er.getStatus()) {
                        int a = 0;
                    }
                }
                runDelegatedTask(er);
                break;
            case NEED_UNWRAP:
                break;
            case FINISHED:
                setInitialized(true);
                break;
        }
        return r;
    }

    public String getSSLPacketInfo(ByteBuffer data) {
        if (data == null) {
            return "<no data>";
        }
        StringBuilder sb = new StringBuilder();
        data.mark();
        try {
            while (data.remaining() > 4) {
                int pPos = data.position();
                int pType = 0xFF & data.get();
                int pVer = 0xFFFF & data.getShort();
                int pLen = 0xFFFF & data.getShort();
                sb.append("\n    pos=" + pPos + ", type=" + pType + ", ver=" + Integer.toHexString(0xFFFF & pVer) + ", len=" + pLen);
                if (pType == 23 && !initialized) {
                    int a = 0;
                }
                switch (pType) {
                    case 20:
                    case 21:
                    case 22:
                    case 23:
                        if (data.remaining() == pLen) {
                            data.position(data.limit());
                        } else if (data.remaining() > pLen) {
                            data.position(data.position() + pLen);
                        } else {
                            int rem = pLen - data.remaining();
                            if (rem > 0) {
                                sb.append(", missing=" + rem);
                                data.position(data.limit());
                            }
                        }
                        break;
                    default:
                        data.position(data.limit());
                        break;
                }
            }
        } finally {
            data.reset();
        }
        return sb.toString();
    }

    public boolean hasCompleteSSLPacket(ByteBuffer data) {
        if (data == null || data.remaining() < 5) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        data.mark();
        boolean ok = false;
        int pLen = Short.MAX_VALUE;
        try {
            int pPos = data.position();
            int pType = 0xFF & data.get();
            int pVer = 0XFFFF & data.getShort();
            pLen = 0xFFFF & data.getShort();
            ok = (data.remaining() - pLen) >= 0;
            sb.append(", pos=" + pPos + ", type=" + pType + ", ver=" + Integer.toHexString(pVer) + ", len=" + pLen);
            switch (pType) {
                case 20:
                case 21:
                case 22:
                case 23:
                    if (data.remaining() >= pLen) {
                    } else {
                        sb.append(", missing=" + (pLen - data.remaining()));
                    }
                    break;
                default:
                    break;
            }
            if (DEBUG_UNWRAP || DEBUG_WRAP) {
                System.out.println("hasCompleteSSLPacket[" + data.remaining() + "]: " + ok + "" + sb);
            }
        } finally {
            data.reset();
        }
        return ok;
    }

    public boolean hasSSLApplicationPacket(ByteBuffer data) {
        if (data == null) {
            return false;
        }
        data.mark();
        try {
            while (data.remaining() > 4) {
                int pPos = data.position();
                int pType = 0xFF & data.get();
                int pVer = 0xFFFF & data.getShort();
                int pLen = 0xFFFF & data.getShort();
                if (pType == 23) {
                    return true;
                }
                switch (pType) {
                    case 20:
                    case 21:
                    case 22:
                    case 23:
                        if (data.remaining() == pLen) {
                            data.position(data.limit());
                        } else if (data.remaining() > pLen) {
                            data.position(data.position() + pLen);
                        } else {
                            int rem = pLen - data.remaining();
                            if (rem > 0) {
                                data.position(data.limit());
                            }
                        }
                        break;
                    default:
                        data.position(data.limit());
                        break;
                }
            }
        } finally {
            data.reset();
        }
        return false;
    }

    public boolean hasUnwrappedData() {
        return unwrapCache != null && unwrapCache.position() > 0;
    }

    public List<ByteBuffer> unwrap(ByteBuffer bb) throws IOException {
        List<ByteBuffer> r = null;
        // choose proper buffer and if cached -> fill with data from "data".
        ByteBuffer data = bb;
        if (DEBUG_UNWRAP && bb.remaining() > 4) {
            System.out.println(System.currentTimeMillis() + ": unwrap: SSL packet[" + bb.remaining() + "]: " + getSSLPacketInfo(bb) + "  " + provider);
        }
        if (unwrapCache.position() > 0 && unwrapCache.hasRemaining()) {
            if (bb.hasRemaining()) {
                if (DEBUG_UNWRAP) {
                    System.out.println(System.currentTimeMillis() + ": unwrap: cached SSL packet[" + unwrapCache.position() + "] " + unwrapCache + "  " + provider);
                }
                while (unwrapCache.hasRemaining() && bb.hasRemaining()) {
                    unwrapCache.put(bb.get());
                }
                if (DEBUG_UNWRAP) {
                    System.out.println(System.currentTimeMillis() + ": unwrap: merged SSL packet[" + unwrapCache.position() + "] " + unwrapCache + "  " + provider);
                }
            }
            ((Buffer) unwrapCache).flip();
            data = unwrapCache;
        }
        while (ssl.getHandshakeStatus() == NEED_UNWRAP || ssl.getHandshakeStatus() == NOT_HANDSHAKING) {
            boolean done = false;
            switch (ssl.getHandshakeStatus()) {
                case NEED_WRAP:
                    break;
                case NOT_HANDSHAKING:
                case NEED_UNWRAP:
                    if (DEBUG_UNWRAP && data.remaining() > 4) {
                        System.out.println(System.currentTimeMillis() + ": unwrap:   [" + data.remaining() + "]: " + getSSLPacketInfo(data) + "  " + provider);
                    }
                    SSLEngineResult er = null;
                    Throwable th = null;
                    try {
                        er = ssl.unwrap(data, netIn);
                    } catch (IOException ioex) {
                        th = ioex;
                        throw ioex;
                    } finally {
                        if (DEBUG_UNWRAP) {
                            System.out.println(System.currentTimeMillis() + ": unwrap[left=" + BufferTools.getRemaining(data) + " ,netIn=" + netIn + "]: "
                                    + provider
                                    + (er != null ? er.toString().replace("\n", "\\n ") : "<no result>" + (th != null ? " <error>" : ""))
                                    + (th != null ? "\n  " + th.toString().replace("\n", "\n  ") : "")
                            );
                        }
                    }
                    runDelegatedTask(er);

                    if (Status.BUFFER_UNDERFLOW == er.getStatus()) {
                        if (data == unwrapCache) {
                            // if internal cache - > compact it first
                            unwrapCache.compact();
                        }

                        // add unused input data to internal cache if any
                        if (bb.hasRemaining()) {
                            while (unwrapCache.hasRemaining() && bb.hasRemaining()) {
                                unwrapCache.put(bb.get());
                            }
                            if (hasCompleteSSLPacket((ByteBuffer) ((Buffer) unwrapCache.duplicate()).flip())) {
                                ((Buffer) unwrapCache).flip();
                                done = false;
                            } else {
                                done = true;
                            }
                        } else {
                            done = true;
                        }
                    } else if (Status.BUFFER_OVERFLOW == er.getStatus()) {
                        int a = 0;
                    } else if (Status.CLOSED == er.getStatus()) {
                        int a = 0;
                        done = true;
                    } else {
                        if (er.bytesProduced() > 0) {
                            if (r == null) {
                                r = new ArrayList<>();
                            }
                            ByteBuffer rb = ByteBuffer.allocate(er.bytesProduced());
                            ((Buffer) netIn).flip();
                            rb.put(netIn);
                            ((Buffer) rb).flip();
                            r.add(rb);
                            ((Buffer) netIn).clear();
                        }
                        if (!hasCompleteSSLPacket(data)) {
                            done = true;
                            if (data == unwrapCache) {
                                data.compact();
                            }
                            if (bb.hasRemaining()) {
                                while (unwrapCache.hasRemaining() && bb.hasRemaining()) {
                                    unwrapCache.put(bb.get());
                                }
                                if (hasCompleteSSLPacket((ByteBuffer) ((Buffer) unwrapCache.duplicate()).flip())) {
                                    ((Buffer) unwrapCache).flip();
                                    done = false;
                                } else {
                                    done = true;
                                }
                            } else {
                                done = true;
                            }
                        } else {
//                            if (data == unwrapCache) {
//                                if (data.hasRemaining()) {
//                                    data.compact();
//                                } else {
//                                    data.clear();
//                                }
//                            }
                        }
                    }
                    if (er.getHandshakeStatus() == HandshakeStatus.FINISHED) {
                        if (!initialized || DEBUG_INITIALIZED) {
                            setInitialized(true);
                        }
                    }
                    break;
                case FINISHED:
                    setInitialized(true);
                    break;
            }
            if (done) {
                break;
            }
        }
        if (!initialized && data.hasRemaining() && hasSSLApplicationPacket(data)) {
            // keep unread application data if not initialized yet...
            if (data == unwrapCache) {
                data.compact();
            }
            while (unwrapCache.hasRemaining() && bb.hasRemaining()) {
                unwrapCache.put(bb.get());
            }

            int a = 0;
        }
        return r;
    }

    /**
     * If the result indicates that we have outstanding tasks to do, go ahead
     * and run them in this thread.
     */
    void runDelegatedTask(SSLEngineResult result) throws IOException {
        if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NEED_TASK) {
            Runnable runnable;
            while ((runnable = ssl.getDelegatedTask()) != null) {
                runnable.run();
            }
            SSLEngineResult.HandshakeStatus st = ssl.getHandshakeStatus();
            if (st == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                throw new IOException("Detected unexpected duplicate NEED_TASK in SSL handshake.");
            } else if (st == SSLEngineResult.HandshakeStatus.FINISHED) {
                setInitialized(true);
            }
            if (DEBUG) {
                System.out.println("*** " + Thread.currentThread().getName() + ": delegatedTask -> " + st);
            }
        } else if (result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.FINISHED) {
            if (!initialized || DEBUG_INITIALIZED) {
                setInitialized(true);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append('{');
        //+ "provider=" + provider 
        sb.append("ssl=" + (ssl != null ? ssl.getHandshakeStatus() : "none"));
        sb.append(", netPacketSize=" + netPacketSize);
        sb.append(", appPacketSize=" + appPacketSize);
        sb.append(", initialized=" + initialized);
        sb.append(", netIn=" + BufferTools.getRemaining(netIn));
        sb.append(", netOut=" + BufferTools.getRemaining(netOut));
        sb.append(", wrapCache=" + BufferTools.getRemaining(wrapCache));
        sb.append(", unwrapCache=" + BufferTools.getRemaining(unwrapCache));
        sb.append('}');
        return sb.toString();
    }

    static String stackTrace(int indent) {
        try (StringWriter sw = new StringWriter();) {
            new Exception("").printStackTrace(new PrintWriter(sw));
            return sw.toString().indent(indent);
        } catch (IOException ioex) {
            return "";
        }
    }
}
