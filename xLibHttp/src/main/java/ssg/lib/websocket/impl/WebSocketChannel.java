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
package ssg.lib.websocket.impl;

import ssg.lib.http.base.Head;
import ssg.lib.websocket.WebSocket;
import ssg.lib.websocket.WebSocketProcessor;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.ssl.SSLSocketChannel;

/**
 *
 * @author 000ssg
 */
public class WebSocketChannel extends WebSocket {

    public static final long DEFAULT_HANDSHAKE_TIMEOUT = 10000; // 10s

    SocketChannel channel;
    String version = "0.1";
    public boolean DUMP = false;

    public WebSocketChannel(SocketChannel channel) {
        this.channel = channel;
    }

    public WebSocketChannel(SocketChannel channel, Head head) throws IOException {
        this.channel = channel;
        this.handshake(head);
        if (head.isHeadCompleted() && head.isConnectionUpgrade() && head.isRequest()) {

        }
    }

    public WebSocketChannel(URI uri, Map<String, String> httpHeaders) throws IOException {
        InetAddress addr = InetAddress.getByName(uri.getHost());
        int port = uri.getPort();
        this.channel = SocketChannel.open(new InetSocketAddress(addr, port));
        if (uri.getScheme().equalsIgnoreCase("wss")) {
            try {
                SSLEngine sslEngine = SSLContext.getDefault().createSSLEngine();
                sslEngine.setUseClientMode(true);
                this.channel = new SSLSocketChannel(this.channel, sslEngine);
            } catch (NoSuchAlgorithmException nsaex) {
                throw new IOException("Failed to connect via SSL: " + nsaex);
            }
        }
        handshake(version, uri, httpHeaders);
        long timeout = System.currentTimeMillis() + DEFAULT_HANDSHAKE_TIMEOUT;
        while (!isInitialized() && System.currentTimeMillis() < timeout) {
            fetch();//List<ByteBuffer> r = read();

            if (getProtocolHandler() != null) {
                WebSocketProtocolHandler wsh = getProtocolHandler();
                if (wsh.canInitialize(this.channel, this)) {
                    wsh.initialize(this.channel, this);
                }
            }
        }
    }

    public WebSocketChannel(
            URL url,
            SSLEngine sslEngine,
            String[] proposedProtocols,
            Map<String, String> httpHeaders
    ) throws IOException {
        InetAddress addr = InetAddress.getByName(url.getHost());
        int port = (url.getPort() <= 0) ? url.getDefaultPort() : url.getPort();
        this.channel = SocketChannel.open(new InetSocketAddress(addr, port));
        if (url.getProtocol().equalsIgnoreCase("https") || sslEngine != null) {
            sslEngine.setUseClientMode(true);
            this.channel = new SSLSocketChannel(this.channel, sslEngine);
        }
        handshake(
                version,
                url.toString().replace("http", "ws"), //url.getPath(),
                url.getHost() + ':' + (url.getPort() > 0 ? url.getPort() : url.getDefaultPort()),
                null,
                proposedProtocols,
                new String[]{"timestamp; keepOffset=true", "gzipped"},
                0,
                httpHeaders);

        long timeout = System.currentTimeMillis() + DEFAULT_HANDSHAKE_TIMEOUT;
        while (!isInitialized() && System.currentTimeMillis() < timeout) {
            fetch();//List<ByteBuffer> r = read();

            if (getProtocolHandler() != null) {
                WebSocketProtocolHandler wsh = getProtocolHandler();
                if (wsh.canInitialize(this.channel, this)) {
                    wsh.initialize(this.channel, this);
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////// constructors with addons
    ////////////////////////////////////////////////////////////////////////////
    public WebSocketChannel(WebSocketAddons addOns, URI uri, Map<String, String> httpHeaders, SSLEngine sslEngine) throws IOException {
        super(uri, addOns);
        InetAddress addr = InetAddress.getByName(uri.getHost());
        int port = uri.getPort();
        this.channel = SocketChannel.open(new InetSocketAddress(addr, port));
        if (uri.getScheme().equalsIgnoreCase("wss") || sslEngine != null) {
            sslEngine.setUseClientMode(true);
            this.channel = new SSLSocketChannel(this.channel, sslEngine);
        }
        handshake(version, uri, httpHeaders);
        long timeout = System.currentTimeMillis() + DEFAULT_HANDSHAKE_TIMEOUT;
        while (!isInitialized() && System.currentTimeMillis() < timeout) {
            fetch();

            if (getProtocolHandler() != null) {
                WebSocketProtocolHandler wsh = getProtocolHandler();
                if (wsh.canInitialize(this.channel, this)) {
                    wsh.initialize(this.channel, this);
                }
            }
        }
    }

    public WebSocketChannel(WebSocketAddons addOns, SocketChannel channel) {
        super(addOns);
        this.channel = channel;
    }

    public WebSocketChannel(WebSocketAddons addOns, SocketChannel channel, Head head) throws IOException {
        super(addOns);
        this.channel = channel;
        this.handshake(head);
//        if (head.isHeadCompleted() && head.isConnectionUpgrade() && head.isRequest()) {
//            int a=0;
//        }
    }

    public WebSocketChannel(WebSocketAddons addOns, URI uri, Map<String, String> httpHeaders) throws IOException {
        super(uri, addOns);
        InetAddress addr = InetAddress.getByName(uri.getHost());
        int port = uri.getPort();
        this.channel = SocketChannel.open(new InetSocketAddress(addr, port));
        if (uri.getScheme().equalsIgnoreCase("wss")) {
            try {
                SSLEngine sslEngine = SSLContext.getDefault().createSSLEngine();
                sslEngine.setUseClientMode(true);
                this.channel = new SSLSocketChannel(this.channel, sslEngine);
            } catch (NoSuchAlgorithmException nsaex) {
                throw new IOException("Failed to connect via SSL: " + nsaex);
            }
        }
        handshake(version, uri, httpHeaders);
        long timeout = System.currentTimeMillis() + DEFAULT_HANDSHAKE_TIMEOUT;
        while (!isInitialized() && System.currentTimeMillis() < timeout) {
            fetch();//List<ByteBuffer> r = read();

            if (getProtocolHandler() != null) {
                WebSocketProtocolHandler wsh = getProtocolHandler();
                if (wsh.canInitialize(this.channel, this)) {
                    wsh.initialize(this.channel, this);
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////// methods
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void closeInput() throws IOException {
        closeConnection();
    }

    @Override
    public void closeOutput() throws IOException {
        closeConnection();
    }

    @Override
    public boolean isConnected() {
        return channel != null && channel.isConnected();
    }

    @Override
    public <T> T getConnection() {
        return (T) channel;
    }

    @Override
    public void closeConnection() throws IOException {
        if (isConnected()) {
            for (WebSocketLifecycleListener l : getWSListeners()) {
                try {
                    l.onClosed(this, channel);
                } catch (Throwable th) {
                }
            }
            channel.close();
        }
    }

    Boolean _async;

    boolean isAsync() {
        if (_async == null && channel != null) {
            _async = channel.isRegistered();
        }
        return (_async != null) ? _async : false;
    }

    @Override
    public List<ByteBuffer> get() throws IOException {
        if (isAsync() && getProcessor() != null) {
            if (isInitialized()) {
                getProcessor().processCycle();
            }
        }
        return super.get();
    }

    @Override
    public long add(Collection<ByteBuffer>... bbs) throws IOException {
        long c = super.add(bbs);
        if (isAsync() && getProcessor() != null) {
            if (isInitialized()) {
                getProcessor().processCycle();
            }
        }
        return c;
    }

    @Override
    public long write(Collection<ByteBuffer>... bufs) throws IOException {
        long c = super.write(bufs);
        if (!isAsync()) {
            List<ByteBuffer> out = get();
            if (BufferTools.getRemaining(out) > 0) {
                if (DUMP) {
                    System.out.println("[" + Thread.currentThread().getName() + "][WebSocketChannel.write[" + BufferTools.getRemaining(out) + "]: " + BufferTools.toText(null, out).replace("\n", "\n  "));
                }
                ByteBuffer[] outs = BufferTools.asByteBuffersArray(out);
                long c0 = channel.write(outs);
                while (c0 < c) {
                    long c1 = channel.write(outs);
                    if (c1 < 0) {
                        break;
                    } else if (c1 > 0) {
                        c0 += c1;
                    }
                }
            }
        }
        return c;
    }

    @Override
    public void fetch() throws IOException {
        if (!isAsync()) {
            ByteBuffer bb = ByteBuffer.allocateDirect(1024 * 4);
            int c = channel.read(bb);
            if (c > 0) {
                ((Buffer) bb).flip();
                if (DUMP) {
                    System.out.println("[" + Thread.currentThread().getName() + "][WebSocketChannel.read[" + bb.remaining() + "]: " + BufferTools.toText(null, bb).replace("\n", "\n  "));
                    int a = 0;
                }
                add(Collections.singletonList(bb));
            }
        }
    }

    @Override
    public void handshake(String version, String path, String host, String origin, String[] proposedProtocols, String[] proposedExtensions, Integer wsVersion, Map<String, String> httpHeaders) throws IOException {
        super.handshake(version, path, host, origin, proposedProtocols, proposedExtensions, wsVersion, httpHeaders);
        if (!isAsync()) {
            write(null);
        }
    }

    /**
     * "Short" handshake...
     *
     * @param version
     * @param path
     * @throws IOException
     */
    public void handshake(String version, URI uri, Map<String, String> httpHeaders) throws IOException {
        String[] proposedProtocols = getAddOns() != null ? getAddOns().getProposedProtocols() : null;
        String[] proposedExtensions = getAddOns() != null ? getAddOns().getProposedExtensions() : new String[]{"timestamp; keepOffset=true", "gzipped"};
        handshake(
                version,
                uri.getPath(),
                uri.getHost() + ":" + uri.getPort(),
                null,
                proposedProtocols,
                proposedExtensions,
                0,
                httpHeaders);
    }

    @Override
    public WebSocketProcessor createProcessor() {
        if (DUMP) {
            System.out.println("[" + Thread.currentThread().getName() + "][WebSocketChannel.createProcessor(async=" + isAsync() + ")");
        }
        WebSocketProcessor wsp = new WebSocketProcessor(this, isAsync());
        return wsp;
    }

    public String channelInfo() {
        return "" + channel;
    }

    @Override
    public String toString() {
        return ((getClass().isAnonymousClass()) ? getClass().getName() : getClass().getSimpleName()) + "{" + "channel=" + channel + ", version=" + version + ", _async=" + _async + '}';
    }
}
