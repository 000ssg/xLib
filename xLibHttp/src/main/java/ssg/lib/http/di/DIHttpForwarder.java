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
package ssg.lib.http.di;

import ssg.lib.http.base.HttpForwarder;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import ssg.lib.common.buffers.BufferTools;
import ssg.lib.common.net.Forwardable;
import ssg.lib.di.DI;
import ssg.lib.di.base.BaseDI;
import ssg.lib.di.base.SSL_DF;
import ssg.lib.http.base.HttpData;
import ssg.lib.http.base.HttpForwarder.HttpForwarderListener;
import ssg.lib.http.base.HttpRequest;

/**
 *
 * @author 000ssg
 */
public class DIHttpForwarder<P extends Channel> extends BaseDI<ByteBuffer, P> implements Forwardable<P> {

    public static enum LINK_PROBE_STATE {
        waiting,
        created,
        failed
    }

    // config
    int packetSize = 4096;
    Map<String, URI> forwardings = new HashMap<String, URI>();
    SSL_DF ssl_df_client;
    HttpForwarderListener fwdListener;
    TCPConnector remoteConnector;

    // runtime
    Collection secure = new HashSet<>();
    Map<P, Link> links = new HashMap<>();

    public boolean TRACE = true;

    public DIHttpForwarder<P> configureForward(String path, URI target) {
        if (path != null) {
            if (target != null) {
                forwardings.put(path, target);
            } else {
                if (forwardings.containsKey(path)) {
                    forwardings.remove(path);
                }
            }
        }
        return this;
    }

    public DIHttpForwarder<P> configureForwardListener(HttpForwarderListener listener) {
        fwdListener = listener;
        return this;
    }

    public DIHttpForwarder<P> configureTCPConnector(TCPConnector connector) {
        this.remoteConnector = connector;
        return this;
    }

    public DIHttpForwarder<P> configureForwardSSL(SSL_DF ssl_df_client) {
        this.ssl_df_client = ssl_df_client;
        return this;
    }

    @Override
    public void onProviderEvent(P provider, String event, Object... params) {
        //System.out.println("PE: " + event + "  " + provider);
        if (DI.PN_SECURE.equals(event)) {
            secure.add(provider);
        } else if (DI.PN_INPUT_CLOSED.equals(event)) {
            try {
                Link l = link(provider);
                delete(provider);
                if (l != null) {
                    try {
                        l.getRemote().close();
                    } catch (IOException ioex) {
                        int a = 0;
                    }
                    try {
                        l.getLocal().close();
                    } catch (IOException ioex) {
                        int a = 0;
                    }
                } else {
                    ((Channel) provider).close();
                }
            } catch (IOException ioex) {
                ioex.printStackTrace();
            }
        }
        super.onProviderEvent(provider, event, params);
    }

    @Override
    public void delete(P provider) throws IOException {
        Link l = link(provider);
        if (l != null) {
            if (l.local.equals(provider)) {
                delete(l.remote);
            }
            links.remove(l.local);
            links.remove(l.remote);
            secure.remove(l.local);
            secure.remove(l.remote);
        }
        super.delete(provider);
    }

    @Override
    public long size(Collection<ByteBuffer>... data) {
        return BufferTools.getRemaining(data);
    }

    @Override
    public void onFilterReady(P provider) throws IOException {
        super.onFilterReady(provider);
        write(provider, null);
    }

    @Override
    public List<ByteBuffer> produce(P provider) throws IOException {
        List<ByteBuffer> r = null;
        Link l = links.get(provider);
        if (l == null) {
            switch (probe(provider, null)) {
                case waiting:
                    return null;
                case created:
                    l = links.get(provider);
                    break;
                case failed:
            }
        }
        synchronized (l) {
            if (l.local == provider) {
                ByteBuffer[] bbs = l.fwd.toLocal();
                if (BufferTools.hasRemaining(bbs)) {
                    r = BufferTools.aggregate(packetSize, true, bbs);
                }
            } else if (l.remote == provider) {
                ByteBuffer[] bbs = l.fwd.toRemote();
                if (BufferTools.hasRemaining(bbs)) {
                    r = BufferTools.aggregate(packetSize, true, bbs);
                }
            } else {
                throw new IOException("Unknown forwarder channel in 'produce': " + provider);
            }
        }
        if (TRACE && BufferTools.getRemaining(r) > 0) {
            System.out.println("||||||||||||| produce: " + BufferTools.getRemaining(r) + "  " + provider + "\n|||||  " + BufferTools.toText(null, r).replace("\n", "\n|||||  "));
        }

        return r;
    }

    @Override
    public void consume(P provider, Collection<ByteBuffer>... data) throws IOException {
        if (TRACE && BufferTools.getRemaining(data) > 0) {
            System.out.println("||||||||||||| consume: " + BufferTools.getRemaining(data) + "  " + provider + "\n|||||  " + BufferTools.toText(null, data).replace("\n", "\n|||||  "));
        }
        Link l = links.get(provider);
        if (l == null) {
            switch (probe(provider, data)) {
                case waiting:
                    return;
                case created:
                    l = links.get(provider);
                    break;
                case failed:
            }
        }

        synchronized (l) {
            if (l.local == provider) {
                l.fwd.fromLocal(data);
            } else if (l.remote == provider) {
                l.fwd.fromRemote(data);
            } else {
                throw new IOException("Unknown forwarder channel in 'consume': " + provider);
            }
        }
    }

    @Override
    public void onBindForwarding(P local, boolean localIsSecure, P remote, boolean remoteIsSecure) {
        Link l = new Link(local, localIsSecure, remote, remoteIsSecure);
        links.put(local, l);
        links.put(remote, l);
    }

    @Override
    public void onUnbindForwarding(P provider) {
        Link l = links.get(provider);
        if (l != null) {
            links.remove(l.local);
            links.remove(l.remote);
        }
    }

    public Link link(P provider) {
        return links.get(provider);
    }

    public LINK_PROBE_STATE probe(P provider, Collection<ByteBuffer>... data) throws IOException {
        if (BufferTools.hasRemaining(data)) {
            try {
                ByteBuffer probe = BufferTools.probe(ByteBuffer.allocate(1024), data);
                HttpData h = new HttpRequest(probe);
                //System.out.println("PROBE: " + h);
                String[] pi = h.getHead().getProtocolInfo();
                if (pi != null && pi.length > 2) {
                    if (pi[2] != null && pi[2].toUpperCase().startsWith("HTTP/")) {
                        String last = null;
                        for (String ke : forwardings.keySet()) {
                            if (pi[1].startsWith(ke)) {
                                if (last == null || last.length() < ke.length()) {
                                    last = ke;
                                }
                            }
                        }
                        if (last != null) {
                            URI uri = forwardings.get(last);
                            uri = new URI(uri.toString() + pi[2].substring(last.length()));

                            String lh = h.getHead().getHeader1(HttpData.HH_SERVER);

                            SocketAddress rAddr = new InetSocketAddress(InetAddress.getByName(uri.getHost()), uri.getPort());
                            boolean rSecure = "https".equalsIgnoreCase(uri.getScheme());

                            P rCh = connectToRemote(rAddr, new DIForwarded().configure(rSecure ? ssl_df_client : null));

                            onBindForwarding(provider, secure.contains(provider), rCh, rSecure);
                            Link l = link(provider);
                            HttpForwarderListener fl = getHttpForwarderListener(provider, h, l);
                            if (fl != null) {
                                l.listen(fl);
                            }

                            //System.out.println("FORWARD: " + last + " -> " + uri + "\n  " + h.toString().replace("\n", "\n  |  "));
                            return DIHttpForwarder.LINK_PROBE_STATE.created;
                        }
                    }
                }
                return DIHttpForwarder.LINK_PROBE_STATE.failed;
            } catch (Throwable th) {
                th.printStackTrace();
                return DIHttpForwarder.LINK_PROBE_STATE.failed;
            }
        } else {
            return DIHttpForwarder.LINK_PROBE_STATE.waiting;
        }
    }

    public HttpForwarderListener getHttpForwarderListener(P provider, HttpData data, Link link) throws IOException {
        return this.fwdListener;
    }

    public P connectToRemote(SocketAddress address, DI handler) throws IOException {
        return (P) remoteConnector.connectTo(address, handler);
    }

    public class Link {

        P local;
        P remote;
        boolean localIsSecure = false;
        boolean remoteIsSecure = false;

        HttpForwarder fwd;

        public Link(P local, boolean localIsSecure, P remote, boolean remoteIsSecure) {
            this.local = local;
            this.remote = remote;
            this.localIsSecure = localIsSecure;
            this.remoteIsSecure = remoteIsSecure;
            init();
        }

        void init() {
            fwd = new HttpForwarder((Channel) local, localIsSecure, (Channel) remote, remoteIsSecure);
        }

        public void listen(HttpForwarderListener listener) {
            if (fwd != null) {
                fwd.setListener(listener);
            }
        }

        public boolean isLocal(P provider) {
            return local != null && local.equals(provider);
        }

        public boolean isRemote(P provider) {
            return remote != null && remote.equals(provider);
        }

        public boolean isLocalSecure() {
            return local != null && localIsSecure;
        }

        public boolean isRemoteSecure() {
            return remote != null && remoteIsSecure;
        }

        public P getRemote() {
            return remote;
        }

        public P getLocal() {
            return local;
        }

        @Override
        public String toString() {
            return "Link{"
                    + "\n  local=" + local
                    + "\n  remote=" + remote
                    + "\n  localIsSecure=" + localIsSecure
                    + "\n  remoteIsSecure=" + remoteIsSecure
                    + "\n  fwd=" + fwd
                    + "\n"
                    + '}';
        }

    }

    public class DIForwarded extends BaseDI<ByteBuffer, P> {

        @Override
        public long size(Collection<ByteBuffer>... data) {
            return BufferTools.getRemaining(data);
        }

        @Override
        public void consume(P provider, Collection<ByteBuffer>... data) throws IOException {
            //System.out.println("consume("+provider+")\n  "+BufferTools.dump(data).replace("\n", "\n   "));
            DIHttpForwarder.this.consume(provider, data);
        }

        @Override
        public List<ByteBuffer> produce(P provider) throws IOException {
            List<ByteBuffer> r = DIHttpForwarder.this.produce(provider);
            //System.out.println("produce("+provider+")\n  "+BufferTools.dump(r).replace("\n", "\n   "));
            return r;
        }
    }

    public static interface TCPConnector<P extends Channel> {

        P connectTo(SocketAddress address, DI handler) throws IOException;
    }
}
