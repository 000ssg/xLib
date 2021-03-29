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
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.di.DI;
import ssg.lib.di.DM;

/**
 *
 * @author 000ssg
 */
public class TCPHandler implements Handler {

    Selector selector;
    Map<SocketAddress, ServerSocketChannel> sockets = new LinkedHashMap<>();
    private DI<ByteBuffer, SocketChannel> defaultHandler;
    private Map<SocketChannel, DI<ByteBuffer, SocketChannel>> clientHandlers = new HashMap<>();

    public TCPHandler() {
    }

    public TCPHandler(SocketAddress... sas) throws IOException {
        if (sas != null) {
            for (SocketAddress sa : sas) {
                if (sa != null) {
                    this.listen(sa);
                }
            }
        }
    }

    public TCPHandler defaultHandler(DI<ByteBuffer, SocketChannel> dh) {
        setDefaultHandler(dh);
        return this;
    }

    @Override
    public boolean isRegistered() {
        return selector != null && selector.isOpen();
    }

    @Override
    public void register(Selector selector) throws IOException {
        if (selector != null && this.selector == null) {
            this.selector = selector;
        }
        if (this.selector == null) {
            return; //throw new IOException("Valid selector is needed at least once.");
        }
        Iterator<SocketAddress> sait = sockets.keySet().iterator();
        while (sait.hasNext()) {
            SocketAddress sa = sait.next();
            ServerSocketChannel ssc = sockets.get(sa);
            if (ssc == null || !ssc.isOpen()) {
                ssc = ServerSocketChannel.open();
                ssc.bind(sa);
                ssc.configureBlocking(false);
                sockets.put(sa, ssc);
                ssc.register(selector, SelectionKey.OP_ACCEPT, this);
            } else if (ssc.isOpen() && !ssc.isRegistered()) {
                ssc.register(selector, SelectionKey.OP_ACCEPT, this);
            }
        }
    }

    @Override
    public void unregister(Selector selector) throws IOException {
        if (this.selector == null) {
            return; //throw new IOException("Valid selector is needed at least once.");
        }
        Iterator<Entry<SocketAddress, ServerSocketChannel>> sait = sockets.entrySet().iterator();
        while (sait.hasNext()) {
            Entry<SocketAddress, ServerSocketChannel> entry = sait.next();
            if (entry.getValue() != null && entry.getValue().isOpen()) {
                entry.getValue().close();
                sait.remove();
            }
        }
    }

    @Override
    public SelectionKey[] onHandle(SelectionKey key) throws IOException {
        SelectionKey[] r = null;
        if (key.isAcceptable()) {
            SocketChannel sc = ((ServerSocketChannel) key.channel()).accept();
            if (sc == null) {
                return r;
            }
            sc.configureBlocking(false);
            DI h = accept(sc);
            if (h != null) {
                r = new SelectionKey[]{sc.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, h)};
            }
        } else if (key.isConnectable()) {
            DI h = connect((SocketChannel) key.channel());
            if (h != null) {
                SocketChannel sc = (SocketChannel) key.channel();
                if (sc.isConnectionPending()) {
                    sc.finishConnect();
                }
                r = new SelectionKey[]{((SocketChannel) key.channel()).register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, h)};
            }
        } else if (key.isReadable()) {
            onRead((SocketChannel) key.channel());
        } else if (key.isWritable()) {
            onWrite((SocketChannel) key.channel());
        }
        return r;
    }

    public synchronized SocketAddress[] getAddresses() {
        return sockets.keySet().toArray(new SocketAddress[sockets.size()]);
    }

    public void listen(SocketAddress sAddr, Object... params) throws IOException {
        if (!sockets.containsKey(sAddr)) {
            sockets.put(sAddr, null);
            if (selector != null) {
                register(null);
            }
        }
        if (params != null && params.length > 0) {
            associate(sAddr, params);
        }
    }

    /**
     * server accept
     *
     * @param sc
     * @return
     * @throws IOException
     */
    public DI accept(SocketChannel sc) throws IOException {
        DI h = dataHandlerFor(sc, false);
        if (h != null) {
            h.onProviderEvent(sc, DM.PN_OPENED);
        }
        return h;
    }

    /**
     * client init
     *
     * @param sc
     * @return
     * @throws IOException
     */
    public DI<ByteBuffer, SocketChannel> connect(SocketChannel sc) throws IOException {
        DI<ByteBuffer, SocketChannel> h = dataHandlerFor(sc, true);
        if (h != null) {
            h.onProviderEvent(sc, DM.PN_OPENED, System.currentTimeMillis());
        }
        return h;
    }

    public void onRead(SocketChannel sc) throws IOException {
    }

    public void onWrite(SocketChannel sc) throws IOException {
    }

    public void associate(SocketAddress sAddr, Object... params) {
    }

    public DI<ByteBuffer, SocketChannel> dataHandlerFor(SocketChannel sc, boolean asClient) throws IOException {
        DI<ByteBuffer, SocketChannel> r = null;
        synchronized (clientHandlers) {
            r = clientHandlers.remove(sc);
        }
        return r != null ? r : getDefaultHandler();
    }

    ////////////////////////////////////////////////////////////////////////////
    public SocketChannel connect(SocketAddress saddr, DI dl) throws IOException {
        SocketChannel sc = SocketChannel.open();
        sc.configureBlocking(false);
        synchronized (clientHandlers) {
            clientHandlers.put(sc, dl);
        }
        SelectionKey key = sc.register(selector, SelectionKey.OP_CONNECT, this);// SelectionKey.OP_READ | SelectionKey.OP_WRITE, dl);
        sc.connect(saddr);
        return sc;//connect(sc, dl);
    }

//    public SocketChannel connect(SocketChannel sc, DI dl) throws IOException {
//        sc.configureBlocking(false);
//        clientHandlers.put(sc, dl);
//        SelectionKey key=sc.register(selector, SelectionKey.OP_CONNECT, this);// SelectionKey.OP_READ | SelectionKey.OP_WRITE, dl);
//        return sc;
//    }
    /**
     * @return the defaultHandler
     */
    public DI<ByteBuffer, SocketChannel> getDefaultHandler() {
        return defaultHandler;
    }

    /**
     * @param defaultHandler the defaultHandler to set
     */
    public void setDefaultHandler(DI<ByteBuffer, SocketChannel> defaultHandler) {
        if (this.defaultHandler instanceof DataHandlerListener) {
            ((DataHandlerListener) this.defaultHandler).onUnassociated(this);
        }
        this.defaultHandler = defaultHandler;
        if (defaultHandler instanceof DataHandlerListener) {
            ((DataHandlerListener) defaultHandler).onAssociated(this);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName() + "{");
        sb.append("\n  selector=" + selector);
        sb.append("\n  sockets=" + sockets.size());
        for (Entry<SocketAddress, ServerSocketChannel> ent : sockets.entrySet()) {
            sb.append("\n    " + ent.getKey() + " -> " + ent.getValue());
        }
        sb.append("\n  defaultHandler=" + defaultHandler);
        sb.append("\n}");
        return sb.toString();
    }

}
