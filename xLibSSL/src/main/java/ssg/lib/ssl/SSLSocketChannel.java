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
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import ssg.lib.common.net.SocketChannelWrapper;
import ssg.lib.common.buffers.BufferTools;

/**
 *
 * @author sesidoro
 */
public class SSLSocketChannel extends SocketChannelWrapper {

    SSL_IO ssl;
    ByteBuffer netIn;

    public SSLSocketChannel(SocketChannel base, SSL_IO ssl) throws IOException {
        super(base);
        this.ssl = ssl;
        netIn = ByteBuffer.allocate(ssl.getPreferredBufferSize(false));
        handshake();
    }

    public SSLSocketChannel(SocketChannel base, SSLEngine ssl) throws IOException {
        super(base);
        this.ssl = new SSL_IO(ssl, base);
        netIn = ByteBuffer.allocate(this.ssl.getPreferredBufferSize(false));
        handshake();
    }

    public SSLSocketChannel(SocketChannel base, SSLContext sslCtx) throws IOException {
        super(base);
        SSLEngine ssle = sslCtx.createSSLEngine();
        ssle.setUseClientMode(true);
        this.ssl = new SSL_IO(ssle, base);
        netIn = ByteBuffer.allocate(this.ssl.getPreferredBufferSize(false));
        handshake();
    }

    public SSLSocketChannel(SocketChannel base, SSLContext sslCtx, String host, int port) throws IOException {
        super(base);
        SSLEngine ssle = (host != null) ? sslCtx.createSSLEngine(host, port) : sslCtx.createSSLEngine();
        ssle.setUseClientMode(host == null);
        this.ssl = new SSL_IO(ssle, base);
        netIn = ByteBuffer.allocate(this.ssl.getPreferredBufferSize(false));
        handshake();
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if (!ssl.isInitialized()) {
            handshake();
        }

        //List<ByteBuffer> bbs = ssl.encode(Arrays.asList(BufferTools.toList(true, srcs, offset, length)));
        List<ByteBuffer> bbs = ssl.encode(BufferTools.toList(true, srcs, offset, length));
        return (int) super.write(bbs.toArray(new ByteBuffer[bbs.size()]), 0, bbs.size());
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return (int) write(new ByteBuffer[]{src}, 0, 1);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
        long r = 0;
        for (int i = 0; i < length; i++) {
            int ri = read(dsts[i + offset]);
            if (ri < 0) {
                if (r == 0) {
                    r = ri;
                }
                break;
            } else {
                r += ri;
            }
        }
        return r;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (!ssl.isInitialized()) {
            handshake();
        }
        int c = super.read(netIn);
        int rc = 0;
        if (c > 0) {
            ((Buffer) netIn).flip();
            List<ByteBuffer> bbs = ssl.decode(Collections.singletonList(netIn));
            for (ByteBuffer bb : bbs) {
                while (dst.hasRemaining() && bb.hasRemaining()) {
                    dst.put(bb.get());
                    rc++;
                }
                if (!dst.hasRemaining()) {
                    break;
                }
            }
            netIn.compact();
        }
        return rc;
    }

    void handshake() throws IOException {
        if (ssl.ssl.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING) {
            ssl.ssl.beginHandshake();
        }
        List<ByteBuffer> bufs = null;
        while (!ssl.isInitialized()) {
            switch (ssl.ssl.getHandshakeStatus()) {
                case NEED_WRAP:
                    List<ByteBuffer> r = ssl.decode(bufs);
                    if (r == null || r.isEmpty()) {
                        r = ssl.encode(null);
                    }
                    if (r != null && !r.isEmpty()) {
                        super.write(r.toArray(new ByteBuffer[r.size()]), 0, r.size());
                    }
                    break;
                case NOT_HANDSHAKING:
                    break;
                case NEED_UNWRAP:
                default:
                    int c = super.read(netIn);
                    if (c > 0) {
                        ((Buffer) netIn).flip();
                        ssl.decode(Collections.singletonList(netIn));
                        netIn.compact();
                    } else if (c == -1) {
                        throw new IOException("Remote peer closed connection while handshaking.");
                    }
                    break;
            }
        }
    }
}
