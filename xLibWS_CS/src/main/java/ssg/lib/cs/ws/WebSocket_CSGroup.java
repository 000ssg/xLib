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
package ssg.lib.cs.ws;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.common.net.NetTools;
import ssg.lib.http.base.Head;
import ssg.lib.http.base.HttpData;
import ssg.lib.net.CS;
import ssg.lib.net.CSGroup;
import ssg.lib.net.Handler;
import ssg.lib.net.Handler.DataHandlerListener;
import ssg.lib.net.TCPHandler;
import ssg.lib.websocket.WebSocket;
import ssg.lib.websocket.WebSocketProcessor.BufferingMessageListener;
import ssg.lib.websocket.impl.DI_WS;
import ssg.lib.websocket.impl.HttpConnectionUpgradeWS;
import ssg.lib.websocket.impl.HttpWS;
import ssg.lib.websocket.impl.WebSocketProtocolHandler;
import ssg.lib.websocket.impl.WebSocketChannel;

/**
 * WebSocket CSGroup allows unified configuration and utilization of
 * WebSocket-based connections both in stand-alone and HTTP-service embedded
 * modes.
 *
 *
 *
 * @author 000ssg
 */
public class WebSocket_CSGroup implements CSGroup {

    public boolean WS_DUMP = false;
    private boolean running = false;

    Map<SocketAddress, TCPHandler> listeners = Collections.synchronizedMap(new LinkedHashMap<>());
    Map<String, HttpConnectionUpgradeWS> httpListeners = Collections.synchronizedMap(new LinkedHashMap<>());
    Map<TCPHandler, CS> handlers = Collections.synchronizedMap(new LinkedHashMap<>());

    DI_WS diWS = new DI_WS_CS();

    public WebSocket_CSGroup() {
    }

    public WebSocket_CSGroup(WebSocketProtocolHandler... wsProtocolHandlers) {
        if (wsProtocolHandlers != null) {
            diWS.addWebSocketProtocolHandler(wsProtocolHandlers);
            for (WebSocketProtocolHandler wsh : wsProtocolHandlers) {
                if (running) {
                    wsh.onStarted(handlers.values().iterator().next());
                }
            }
        }
    }

    public DI_WS getDI() {
        return diWS;
    }

    public WebSocket_CSGroup addWebSocketProtocolHandler(WebSocketProtocolHandler... protocolHandlers) {
        diWS.addWebSocketProtocolHandler(protocolHandlers);
        return this;
    }

    public void removeWebSocketProtocolHandler(WebSocketProtocolHandler... protocolHandlers) {
        diWS.removeWebSocketProtocolHandler(protocolHandlers);
    }

    public WebSocketProtocolHandler[] getWebSocketProtocolHandler() {
        return diWS.getWebSocketProtocolHandler();
    }

    public boolean hasListenerAt(int port) {
        if (port <= 0 || port > 0xFFFF) {
            return false;
        }
        for (SocketAddress sa : listeners.keySet()) {
            if (NetTools.getPort(sa) == port) {
                return true;
            }
        }
        return false;
    }

    public void addListenerAt(CS cs, InetAddress iaddr, int... ports) throws IOException {
        if (ports != null) {
            if (iaddr == null) {
                iaddr = InetAddress.getByAddress(new byte[]{0, 0, 0, 0});
            }
            for (int port : ports) {
                if (port <= 0 || port > 0xFFFF) {
                    continue;
                }
                SocketAddress saddr = new InetSocketAddress(iaddr, port);
                if (!listeners.containsKey(saddr)) {
                    TCPHandler h = new TCPHandler(saddr).defaultHandler(diWS);
                    listeners.put(saddr, h);
                    if (cs != null) {
                        cs.add(h);
                    }
                }
            }
        }
    }

    public Collection<SocketAddress> getListeningAt() {
        return listeners.keySet();
    }

    public URI[] getListeningURIs(boolean activeOnly, int... ports) {
        Collection<SocketAddress> sas = getListeningAt();

        URI[] r = new URI[sas.size()];

        int off = 0;
        for (SocketAddress sa : sas) {
            Handler h = listeners.get(sa);
            if (!(h instanceof TCPHandler) || activeOnly && !h.isRegistered()) {
                continue;
            }

            String address = NetTools.getAddress(sa);
            int port = NetTools.getPort(sa);
            try {
                boolean portOk = false;
                if (ports != null && ports.length > 0) {
                    for (int pi : ports) {
                        if (pi > 0 && pi == port) {
                            portOk = true;
                            break;
                        }
                    }
                } else {
                    portOk = true;
                }
                if (portOk) {
                    String[] addresses = new String[]{address};
                    if (address.contains("0.0.0.0")) {
                        InetAddress[] addrs = NetTools.getAllLocalAddresses();
                        addresses = new String[addrs.length];
                        int aoff = 0;
                        for (InetAddress addr : addrs) {
                            try {
                                String addrS = addr.getHostAddress();
                                if (addrS.contains(":")) {
                                    addrS = "[" + addrS + "]";
                                }
                                addresses[aoff++] = addrS;
                            } catch (Throwable th) {
                            }
                        }
                        if (aoff < addresses.length) {
                            addresses = Arrays.copyOf(addresses, aoff);
                        }
                    }
                    for (String a : addresses) {
                        if (r.length == off) {
                            r = Arrays.copyOf(r, r.length + 5);
                        }
                        r[off++] = new URI("ws://" + a + ":" + port);
                    }
                }
            } catch (URISyntaxException usex) {
                usex.printStackTrace();
            }
        }

        if (off < r.length) {
            r = Arrays.copyOf(r, off);
        }

        return r;
    }

    public void stopListenerAt(SocketAddress... saddrs) throws IOException {
        if (saddrs != null) {
            for (SocketAddress saddr : saddrs) {
                if (listeners.containsKey(saddr)) {
                    TCPHandler h = listeners.remove(saddr);
                    CS cs = handlers.get(h);
                    if (cs != null) {
                        try {
                            cs.remove(h);
                        } catch (IOException ioex) {
                            ioex.printStackTrace();
                        } finally {
                            handlers.remove(h);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onStarted(final CS cs) {
        if (cs != null) {
            running = true;
            for (TCPHandler h : listeners.values()) {
                if (!h.isRegistered()) {
                    try {
                        cs.add(h);
                        handlers.put(h, cs);
                    } catch (IOException ioex) {
                        ioex.printStackTrace();
                    }
                }
            }

            for (WebSocketProtocolHandler wsh : diWS.getWebSocketProtocolHandler()) {
                try {
                    wsh.onStarted(cs, diWS);
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onStop(CS cs) {
        if (cs != null) {
            try {
                for (WebSocketProtocolHandler wsh : diWS.getWebSocketProtocolHandler()) {
                    try {
                        wsh.onStop(cs, diWS);
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }

                Iterator<TCPHandler> it = handlers.keySet().iterator();
                while (it.hasNext()) {
                    TCPHandler h = it.next();
                    CS hcs = handlers.get(h);
                    if (cs == hcs) {
                        try {
                            hcs.remove(h);
                        } catch (IOException ioex) {
                            ioex.printStackTrace();
                        } finally {
                            it.remove();
                        }
                    }
                }
            } finally {
                running = false;
            }
        }
    }

    /**
     * Prepare addOns as extension of optionally provided ones and internally
     * defined for client or server context and given provider.
     *
     * May be used to extend and/or disable some functionality depending on
     * provider/context.
     *
     * @param provider
     * @param client
     * @param addOns
     * @return
     */
    public WebSocket.WebSocketAddons prepareAddons(Channel provider, boolean client, WebSocket.WebSocketAddons addOns) {
        return diWS.prepareWebSocketAddons(provider, client, addOns);
    }

    /**
     * Creates HttpConnectionUpgradeWS instance for use in HttpService to handle
     * WebSocket messages as defined in this group.
     *
     * @param path
     * @param addOns explicitly define addOns. If not specified, default addOns
     * are used
     * @return
     */
    public HttpConnectionUpgradeWS createWebSocketConnectionUpgrade(String path, final WebSocket.WebSocketAddons addOns) {
        HttpConnectionUpgradeWS r = new HttpConnectionUpgradeWS() {
            @Override
            public HttpWS createWSHttp(Channel provider, HttpData data) throws IOException {
                // create websocket to process provider data.
                WebSocketChannel webSocket = new WebSocketChannel(
                        (addOns != null) ? addOns : prepareAddons(provider, false, null),
                        (SocketChannel) provider) {
                    @Override
                    public void onClosed() throws IOException {
                        super.onClosed();
                        if (getProtocolHandler() != null) {
                            try {
                                getProtocolHandler().delete(provider, this);
                            } catch (Throwable th) {
                            }
                        }
                    }

                    @Override
                    public void setInitialized(boolean initialized) {
                        super.setInitialized(initialized);
                        if (initialized) {
                            getDI().onInitializedWS(provider, this);
                            if (getProtocolHandler() != null && getProtocolHandler().canInitialize(provider, this)) {
                                getProtocolHandler().initialize(provider, this);
                            }
                        }
                    }

                    @Override
                    public long add(Collection<ByteBuffer>... bbs) throws IOException {
                        long r = super.add(bbs);

                        if (getDefaultMessageListener() instanceof BufferingMessageListener) {
                            BufferingMessageListener bml = (BufferingMessageListener) getDefaultMessageListener();
                            if (bml.getMessagesCount() > 0) {
                                List lst = bml.removeMessages();
                                if (lst != null) {
                                    for (Object message : lst) {
                                        if (message instanceof String || message instanceof byte[]) {
                                            if (getProtocolHandler() != null) {
                                                getProtocolHandler().onConsume(provider, this, message);
                                            } else {
                                                int a = 0;
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        return r;
                    }
                };

                // ensure proper messages listener is used
                webSocket.setDefaultMessageListener(new BufferingMessageListener());
                // initialize server-size handshake as response to client request
                webSocket.handshake(data.getHead());

                // return HttpWS wrapper for use within HttpService.
                return new HttpWS(webSocket);
            }

            @Override
            public boolean testWSPath(String root, Head head) {
                return super.testWSPath(root, head) && head.getProtocolInfo()[1].equals(root + path);
            }
        };

        httpListeners.put(path, r);

        return r;
    }

    public class DI_WS_CS extends DI_WS implements DataHandlerListener {

        @Override
        public WebSocket createWebSocket(Channel provider, boolean client, WebSocket.WebSocketAddons addOns) {
            WebSocket ws = super.createWebSocket(provider, client, addOns);
            if (ws instanceof WebSocketChannel) {
                ((WebSocketChannel) ws).DUMP = WS_DUMP;
            }
            return ws;
        }

        @Override
        public void onAssociated(Handler handler) {
        }

        @Override
        public void onUnassociated(Handler handler) {
        }
    }
}
