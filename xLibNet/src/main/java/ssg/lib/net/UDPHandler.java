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
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.SelectionKey;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import ssg.lib.common.net.NetTools;
import ssg.lib.di.DI;

/**
 *
 * @author 000ssg
 */
public class UDPHandler implements Handler {

    MCSSelector selector;
    //
    SocketAddress sAddr;
    NetworkInterface ni;
    InetAddress multicast;
    MembershipKey membershipKey;
    boolean reusable;
    boolean broadcast;
    int maxPacketSize = 1024 * 8;
    ByteBuffer readBuffer = ByteBuffer.allocateDirect(maxPacketSize);
    Collection<ByteBuffer> wrappedReadBuffer = Collections.singleton(readBuffer);
    transient DatagramChannel sSocket;
    transient Collection<SocketAddress> providers = new HashSet<>();
    transient Collection<DI<ByteBuffer, SocketAddress>> handlers = new HashSet<>();

    //Map<SocketAddress, ServerSocketChannel> sockets = new LinkedHashMap<>();
    private DI<ByteBuffer, SocketAddress> defaultHandler;

    public UDPHandler(
            NetworkInterface ni,
            InetSocketAddress sAddr,
            InetAddress multicast,
            boolean reusable,
            boolean broadcast,
            DI<ByteBuffer, SocketAddress> handler) {
        super();
        this.sAddr = sAddr;
        this.ni = ni;
        this.multicast = multicast;
        this.reusable = reusable;
        this.broadcast = broadcast;

        setDefaultHandler(handler);
    }

    @Override
    public boolean isRegistered() {
        return selector != null;// && selector.isOpen();
    }

    @Override
    public void register(MCSSelector selector) throws IOException {
        this.selector = selector;
        try {
            try {
                NetworkInterface ni = this.ni;
                if (ni == null) {
                    ni = NetTools.getDefaultNetworkInterface();
                    if (ni == null) {
                        List<NetworkInterface> nis = NetTools.getSupportedNetworkInterfaces(true, false, multicast != null && multicast.isMulticastAddress(), false, false);
                        if (!nis.isEmpty()) {
                            for (NetworkInterface nni : nis) {
                                if (!nni.getDisplayName().contains("Virtual")) {
                                    ni = nni;
                                    break;
                                }
                            }
                        }
                    }
                }
                DatagramChannel ds = null;
                if (multicast == null || !multicast.isMulticastAddress()) {
                    ds = DatagramChannel.open()
                            .setOption(StandardSocketOptions.SO_REUSEADDR, reusable)
                            .setOption(StandardSocketOptions.SO_BROADCAST, broadcast)
                            .bind(sAddr);
                } else {
                    ds = DatagramChannel.open((multicast.getAddress().length == 4) ? StandardProtocolFamily.INET : StandardProtocolFamily.INET6)
                            .setOption(StandardSocketOptions.SO_REUSEADDR, reusable)
                            .bind(new InetSocketAddress(NetTools.getPort(sAddr)))
                            .setOption(StandardSocketOptions.IP_MULTICAST_IF, ni)
                            .setOption(StandardSocketOptions.IP_MULTICAST_TTL, 4);
                    membershipKey = ds.join(multicast, ni);
                }
                sSocket = ds;
                sSocket.configureBlocking(false);
            } catch (IOException ioex) {
                ioex.printStackTrace();
            } catch (Throwable th) {
                th.printStackTrace();
            }

            if (selector != null) {
                sSocket.register(selector.selector(SelectionKey.OP_READ | SelectionKey.OP_WRITE), SelectionKey.OP_READ | SelectionKey.OP_WRITE, this);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    @Override
    public void unregister(MCSSelector selector) throws IOException {
        if (this.selector == null) {
            return;
        }
        selector = null;
        if (providers.isEmpty() && !handlers.isEmpty()) {
            synchronized (providers) {
                SocketAddress[] ps = providers.toArray(new SocketAddress[providers.size()]);
                providers.clear();
                for (DI<ByteBuffer, SocketAddress> h : handlers) {
                    try {
                        for (SocketAddress psi : ps) {
                            h.delete(psi);
                        }
                    } catch (Throwable th) {
                        onError("Failed to unregister " + this, th);
                    }
                }
            }
        }
        providers.clear();
        handlers.clear();
    }

    @Override
    public SelectionKey[] onHandle(SelectionKey key) throws IOException {
        DatagramChannel dc = (DatagramChannel) key.channel();
        DI<ByteBuffer, SocketAddress> di = this.dataHandlerFor(dc, reusable);
        if (!handlers.contains(di)) {
            handlers.add(di);
        }
        if (key.isAcceptable()) {
            throw new IOException("Cannot 'accept' UDP datagram.");
        } else if (key.isConnectable()) {
            throw new IOException("Cannot 'connect' UDP datagram.");
        } else if (key.isReadable()) {
            onRead((DatagramChannel) key.channel(), di);
        } else if (key.isWritable()) {
            onWrite((DatagramChannel) key.channel(), di);
        }
        return null;
    }

    public synchronized SocketAddress getAddress() {
        return sAddr;
    }

    public void onRead(DatagramChannel ch, DI<ByteBuffer, SocketAddress> di) throws IOException {
        //System.out.println("onRead UDP: " + key);

        SocketAddress provider = ch.receive(readBuffer);
        if (provider != null) {
            if (!providers.contains(provider)) {
                providers.add(provider);
            }
            if (readBuffer.position() > 0) {
                ((Buffer) readBuffer).flip();
                di.write(provider, wrappedReadBuffer);
                ((Buffer) readBuffer).clear();
            }
        }
    }

    public void onWrite(DatagramChannel ch, DI<ByteBuffer, SocketAddress> di) throws IOException {

        Collection<SocketAddress> providers = di.providers();
        if (providers != null) {
            for (SocketAddress provider : providers) {
                if (!providers.contains(provider)) {
                    providers.add(provider);
                }
                List<ByteBuffer> bbs = di.read(provider);
                if (bbs != null && !bbs.isEmpty()) {
                    for (ByteBuffer bb : bbs) {
                        ch.send(bb, provider);
                    }
                }
            }
        }
    }

    public void onError(String message, Throwable th) {
    }

//    @Override
//    public boolean onClose(SelectionKey key, Throwable reason) throws IOException {
//        FlowTracer.trace(FT_SCOPE.close, this, ServerConnectionUDP.class, key.channel(), null, reason);
//        ServerConnectionUDP cd = (ServerConnectionUDP) key.attachment();
//        if (cd.getHandler() != null) {
//            Collection<SocketAddress> providers = cd.getHandler().providers();
//            if (providers != null) {
//                for (SocketAddress sa : providers) {
//                    noTouch(sa);
//                }
//            }
//        }
//        FlowTracer.trace(FT_SCOPE.closed, this, ServerConnectionUDP.class, key.channel(), null, null);
//        return false;
//    }
    public DI<ByteBuffer, SocketAddress> dataHandlerFor(DatagramChannel sc, boolean asClient) throws IOException {
        return getDefaultHandler();
    }

    public DI<ByteBuffer, SocketAddress> getDefaultHandler() {
        return this.defaultHandler;
    }

    public void setDefaultHandler(DI<ByteBuffer, SocketAddress> handler) {
        this.defaultHandler = handler;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName() + "{");
        sb.append("\n  selector=" + selector);
        sb.append("\n  sAddr=" + sAddr);
        sb.append("\n  ni=" + ni);
        sb.append("\n  multicast=" + multicast);
        sb.append("\n  membershipKey=" + membershipKey);
        sb.append("\n  reusable=" + reusable);
        sb.append("\n  broadcast=" + broadcast);
        sb.append("\n  maxPacketSize=" + maxPacketSize);
        sb.append("\n  readBuffer=" + readBuffer);
        sb.append("\n  wrappedReadBuffer=" + wrappedReadBuffer);
        sb.append("\n  sSocket=" + sSocket);
        sb.append("\n  providers=" + providers);
        sb.append("\n  handlers=" + handlers);
        sb.append("\n  defaultHandler=" + defaultHandler);
        sb.append("\n}");
        return sb.toString();

    }

}
