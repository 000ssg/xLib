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

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.common.net.NetTools;
import ssg.lib.di.DM;
import static ssg.lib.di.DM.PN_SECURE;
import ssg.lib.ssl.SSL_IO;

/**
 *
 * @author 000ssg
 */
public class SSL_DF<P> extends BaseDF<ByteBuffer, P> {

    SSLContext sslCtx;
    private boolean autodetect;
    boolean client = true;
    private Boolean needClientAuth = null;
    Map<P, SSL_IO2<P>> ssls = new LinkedHashMap<>();

    public SSL_DF(SSLContext ctx) {
        this.sslCtx = ctx;
    }

    public SSL_DF(SSLContext ctx, boolean client) {
        this.sslCtx = ctx;
        this.client = client;
    }

    @Override
    public boolean isReady(P provider) throws IOException {
        SSL_IO2 ssl = sslIO(provider, null);
        return ssl != null && ssl.isInitialized();
    }

    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////// I/O
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public List<ByteBuffer> writeFilter(DM<P> owner, P provider, Collection<ByteBuffer>... data) throws IOException {
        List<ByteBuffer> r = null;
        SSL_IO2 ssl = sslIO(provider, BufferTools.firstNonEmpty(data));
        long c = BufferTools.getRemaining(data);

        DM<P> notifiee = (owner != null) ? owner : this;
        boolean sslInitialized = ssl.isInitialized();
        List<ByteBuffer> r1 = ssl.decode(data);
        if (ssl.isInitialized() && !sslInitialized) {
            Certificate[] certs = ssl.getRemoteCertificates();
            notifiee.onProviderEvent(provider, PN_SECURE, (certs != null) ? certs : ssl.isSecure());
        } else if (sslInitialized && !ssl.isInitialized()) {
            notifiee.onProviderEvent(provider, PN_SECURE, null);
        }

        if (!ssl.appIn.isEmpty()) {
            //System.out.println("SSL_DF.writeFilter:APP:"+BufferTools.getRemaining(ssl.appIn));
            r = new ArrayList<>();
            synchronized (ssl.appIn) {
                r.addAll(ssl.appIn);
                ssl.appIn.clear();
            }
        }

        return r;
    }

    @Override
    public List<ByteBuffer> readFilter(DM<P> owner, P provider, Collection<ByteBuffer>... data) throws IOException {
        List<ByteBuffer> r = new ArrayList<>();
        SSL_IO2 ssl = sslIO(provider, null);
        if (ssl == null) {
            return r;
        }
        DM<P> notifiee = (owner != null) ? owner : this;
        if (ssl.isInitialized()) {
//            if (BufferTools.hasRemaining(ssl.appCached)) {
//                ssl.encode(ssl.appCached);
//                ssl.appCached.clear();
//            }
            ssl.encode(data);
            if (!ssl.isInitialized()) {
                notifiee.onProviderEvent(provider, PN_SECURE, null);
            }
        } else {
            // cache app data while initializing...
//            if (BufferTools.hasRemaining(data)) {
//                BufferTools.moveBuffersTo(ssl.appCached, data);
//            }
            ssl.encode(Collections.singletonList(SSL_IO.EMPTY));
            if (ssl.isInitialized()) {
                Certificate[] certs = ssl.getRemoteCertificates();
                notifiee.onProviderEvent(provider, PN_SECURE, (certs != null) ? certs : ssl.isSecure());
            }
        }

        if (!ssl.netOut.isEmpty()) {
            synchronized (ssl.netOut) {
                r.addAll(ssl.netOut);
                ssl.netOut.clear();
            }
        }
        return r;
    }

    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////// maintenance
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public Collection<P> providers() {
        Collection<P> r = super.providers();
        if (!ssls.isEmpty()) {
            if (r == null) {
                r = ssls.keySet();
            } else {
                for (P p : ssls.keySet()) {
                    if (!r.contains(p)) {
                        r.add(p);
                    }
                }
            }
        }
        return r;
    }

    @Override
    public void delete(P provider) throws IOException {
        if (provider != null) {
            if (ssls.containsKey(provider)) {
                SSL_IO2 ssl = ssls.remove(provider);
            }
        }
        super.delete(provider);
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////// tools
    ////////////////////////////////////////////////////////////////////////////
    public Certificate[] getRemoteCertificates(P provider) throws IOException {
        SSL_IO2 ssl = sslIO(provider, null);
        if (ssl != null) {
            return ssl.getRemoteCertificates();
        }
        return null;
    }

    public SSL_IO2 sslIO(P provider, ByteBuffer bb) throws IOException {
        SSL_IO2 ssl = ssls.get(provider);
        if (ssl == null) {
            if (isAutodetect() && isServer(provider)) {
                if (!BufferTools.hasRemaining(bb)) {
                    return ssl;
                }
                if (SSL_IO.isSSLHandshake(bb)) {
                    ssl = createSSL_IO2(provider, false);
                } else {
                    ssl = new SSL_IO2(null, provider);
                }
            } else {
                ssl = createSSL_IO2(provider, !isServer(provider));
            }
            ssls.put(provider, ssl);
        }
        return ssl;
    }

    public boolean isServer(P provider) {
        return !client;
    }

    public boolean isInitialized(P provider) {
        SSL_IO2 ssl = ssls.get(provider);
        return ssl != null && ssl.isInitialized();
    }

    public SSL_IO2 createSSL_IO2(P provider, boolean client) throws IOException {
        SSLEngine eng = null;
        if (sslCtx != null) {
            eng = createSSLEngine(provider, client);// sslCtx.createSSLEngine();
            eng.setUseClientMode(client);
            if (!client && getNeedClientAuth() != null) {
                if (getNeedClientAuth()) {
                    eng.setNeedClientAuth(true);
                } else {
                    eng.setWantClientAuth(true);
                }
            }
        }
        return new SSL_IO2(eng, provider);
    }

    public SSLEngine createSSLEngine(P provider, boolean client) throws IOException {
        if (sslCtx == null) {
            return null;
        }
        if (client) {
            return sslCtx.createSSLEngine();
        }
        SocketAddress sa = null;
        if (provider instanceof SocketAddress) {
            sa = (SocketAddress) provider;
        } else if (provider instanceof SocketChannel) {
            sa = ((SocketChannel) provider).getRemoteAddress();
        }
        if (sa != null) {
            return sslCtx.createSSLEngine(NetTools.getAddress(sa), NetTools.getPort(sa));
        } else {
            return sslCtx.createSSLEngine();
        }
    }

    /**
     * @return the needClientAuth
     */
    public Boolean getNeedClientAuth() {
        return needClientAuth;
    }

    /**
     * @param needClientAuth the needClientAuth to set
     */
    public void setNeedClientAuth(Boolean needClientAuth) {
        this.needClientAuth = needClientAuth;
    }

    /**
     * SSL I/O
     *
     * @param <P>
     */
    public static class SSL_IO2<P> extends SSL_IO<P> {

        //List<ByteBuffer> appCached = new ArrayList<>(); // cached app data for external source
        List<ByteBuffer> appIn = new ArrayList<>(); // app data from external source
        List<ByteBuffer> netOut = new ArrayList<>(); // net data for external source

        public SSL_IO2(SSLEngine ssl, P provider) throws SSLException {
            super(ssl, provider);
        }

        @Override
        public List<ByteBuffer> encode(Collection<ByteBuffer>... bufs) throws IOException {
            synchronized (netOut) {
                boolean initialized = this.isInitialized();
                List<ByteBuffer> r = super.encode(bufs);
                if (r != null && !r.isEmpty()) {
                    netOut.addAll(r);
                    r.clear();
                }
                if (!initialized && isInitialized() && hasUnwrappedData()) {
                    // ensure initial application data are available (if any)
                    synchronized (appIn) {
                        List<ByteBuffer> ru = unwrap(EMPTY);
                        if (ru != null && !ru.isEmpty()) {
                            appIn.addAll(ru);
                            ru.clear();
                        }
                    }
                }
            }
            return netOut;
        }

        @Override
        public List<ByteBuffer> decode(Collection<ByteBuffer>... bufs) throws IOException {
            synchronized (appIn) {
                List<ByteBuffer> r = super.decode(bufs);
                if (r != null && !r.isEmpty()) {
                    appIn.addAll(r);
                    r.clear();
                }
            }
            return appIn;
        }

        @Override
        public String toString() {
            return "SSL_IO2{" + this.getHandshakeStatus()
                    //                    + ", appCached=" + BufferTools.getRemaining(appCached) 
                    + ", appIn=" + BufferTools.getRemaining(appIn)
                    + ", netOut=" + BufferTools.getRemaining(netOut)
                    + '}';
        }

    }

    /**
     * @return the autodetect
     */
    public boolean isAutodetect() {
        return autodetect;
    }

    /**
     * @param autodetect the autodetect to set
     */
    public void setAutodetect(boolean autodetect) {
        this.autodetect = autodetect;
    }

}
