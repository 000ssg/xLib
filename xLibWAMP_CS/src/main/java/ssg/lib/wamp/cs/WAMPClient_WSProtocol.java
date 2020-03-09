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
package ssg.lib.wamp.cs;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.channels.Channel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import ssg.lib.common.CommonTools;
import ssg.lib.net.CS;
import ssg.lib.net.TCPHandler;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.nodes.WAMPClient;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.nodes.WAMPNode.WAMPNodeListener;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.wamp.stat.WAMPStatistics;
import ssg.lib.wamp.util.LS;
import ssg.lib.websocket.WebSocket;
import ssg.lib.websocket.impl.DI_WS;
import ssg.lib.websocket.impl.WebSocketProtocolHandler;

/**
 *
 * @author 000ssg
 */
public class WAMPClient_WSProtocol implements WebSocketProtocolHandler {

    CS cs;
    DI_WS diWS;

    TCPHandler tcph = new TCPHandler();
    List<WAMPClient> pendingClients = WAMPTools.createSynchronizedList();
    Map<Channel, WAMPClient> clients = WAMPTools.createSynchronizedMap(true);
    Map<WebSocket, WAMPTransportJSON> wampTransports = WAMPTools.createSynchronizedMap(false);
    private WAMPStatistics statistics = new WAMPStatistics("client");
    private int maxInputQueueSize = 100;
    LS<WAMPNodeListener> listeners = new LS<>(new WAMPNodeListener[0]);

    public WAMPClient_WSProtocol() {
    }

    @Override
    public boolean canInitialize(Channel provider, WebSocket ws) {
        return (provider == null || provider.isOpen()) && ws != null && ws.isClient() && WAMP.WS_SUB_PROTOCOL_JSON.equals(ws.getProtocol());
    }

    @Override
    public void initialize(Channel provider, final WebSocket ws) {
        WAMPTransportJSON transport = new WAMPTransportJSON(provider, new WAMPTransportJSON.TransportData(String.class) {
            @Override
            public void send(Object... messages) {
                if (messages != null) {
                    for (Object msg : messages) {
                        try {
                            if (msg instanceof byte[]) {
                                onSend(ws, msg);
                                ws.send((byte[]) msg);
                            } else if (msg instanceof String) {
                                onSend(ws, msg);
                                ws.send((String) msg);
                            }
                        } catch (IOException ioex) {
                            ioex.printStackTrace();
                        }
                    }
                }
            }

            @Override
            public boolean isOpen() {
                return ws != null && ws.isConnected();
            }

            @Override
            public void close() {
                if (ws != null && ws.isConnected()) {
                    try {
                        ws.closeConnection();
                    } catch (IOException ioex) {
                        ioex.printStackTrace();
                    }
                }
            }
        });
        wampTransports.put(ws, transport);
        WAMPClient wc = clients.get(provider);
        try {
            wc.setTransport(transport);
            wc.connect();
        } catch (WAMPException wex) {
            wex.printStackTrace();
        }
    }

    public void onSend(WebSocket ws, Object message) {
    }

    @Override
    public void delete(Channel provider, WebSocket ws) {
        final WAMPClient client = clients.get(provider);
        // shutdown client
        if (client != null) {
            try {
                client.disconnect("TERMINATE");
                CommonTools.wait(1000, () -> client.isConnected());
            } catch (Throwable th) {
                th.printStackTrace();
            } finally {
                if (clients.containsKey(provider)) {
                    clients.remove(provider);
                }
            }
        }
        if (wampTransports.containsKey(ws)) {
            wampTransports.remove(ws);
        }
    }

    @Override
    public WebSocket.WebSocketAddons prepareAddons(Channel provider, boolean client, WebSocket.WebSocketAddons addOns) throws IOException {
        if (addOns == null) {
            addOns = new WebSocket.WebSocketAddons();
        }
        addOns.addProtocol(WAMP.WS_SUB_PROTOCOL_JSON, this);
        return addOns;
    }

    @Override
    public boolean onConsume(Channel provider, WebSocket ws, Object message) throws IOException {
        if (message != null && ws != null && wampTransports.get(ws) != null) {
            wampTransports.get(ws).transport.add(message);
            WAMPClient client = clients.get(provider);
            if (client != null) {
                client.runCycle();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onProduce(Channel provider, WebSocket ws) throws IOException {
        WAMPClient client = clients.get(provider);
        if (client != null) {
            client.runCycle();
            return true;
        }
        return false;
    }

    @Override
    public boolean isReady(Channel provider, WebSocket ws) throws IOException {
        WAMPTransportJSON wt = wampTransports.get(ws);
        if (wt != null && wt.transport != null && getMaxInputQueueSize() > 0 && wt.transport.getInputQueueSize() > getMaxInputQueueSize()) {
            return false;
        }
        return true;
    }

    @Override
    public void onStarted(Object... parameters) {
        //CS cs = null;
        if (parameters != null) {
            for (Object p : parameters) {
                if (p instanceof CS) {
                    cs = (CS) p;
                } else if (p instanceof DI_WS) {
                    diWS = (DI_WS) p;
                }
            }
        }

        try {
            cs.add(tcph);
            if (!pendingClients.isEmpty()) {
                Iterator<WAMPClient> it = pendingClients.iterator();
                while (it.hasNext()) {
                    WAMPClient client = it.next();
                    Map<String, Object> props = client.getProperties();
                    try {
                        URI uri = (URI) props.get("uri");
                        int port = uri.getPort();
                        if (port <= 0) {
                            try {
                                port = uri.toURL().getDefaultPort();
                            } catch (Throwable th) {
                            }
                        }
                        try {
                            SocketAddress wsSA = new InetSocketAddress(InetAddress.getByName(uri.getHost()), port);
                            SocketChannel provider = tcph.connect(wsSA, diWS);
                            clients.put(provider, client);
                            it.remove();
                        } catch (Throwable th) {
                        }
                    } catch (Throwable th) {
                        th.printStackTrace();
                    }
                }
            }
        } catch (IOException ioex) {
            ioex.printStackTrace();
        }
    }

    @Override
    public void onStop(Object... parameters) {
        try {
            if (!clients.isEmpty()) {
                Channel[] keys = clients.keySet().toArray(new Channel[clients.size()]);
                for (Channel key : keys) {
                    try {
                        WAMPClient client = clients.get(key);
                        if (client != null) {
                            //diWS.delete(key);
                            client.disconnect("TERMINATE");
                        }
                    } catch (IOException ioex) {
                        ioex.printStackTrace();
                    } finally {
                        synchronized (clients) {
                            if (clients.containsKey(key)) {
                                clients.remove(key);
                            }
                        }
                    }
                }
            }

            try {
                cs.remove(tcph);
            } catch (IOException ioex) {
                ioex.printStackTrace();
            }
        } finally {
            diWS = null;
        }
    }

    /**
     * Base WAMP client initialization: build predefined client and initializes
     * connection procedure.
     *
     * @param uri
     * @param protocol
     * @param agent
     * @param realm
     * @param roles
     * @return
     * @throws IOException
     */
    public WAMPClient connect(URI uri, String protocol, WAMPFeature[] features, String agent, String realm, WAMP.Role... roles) throws IOException {
        WAMPClient client = new WAMPClient()
                .configure(null, features, agent, realm, roles)
                .configure((WAMPStatistics) ((statistics != null) ? statistics.createChild(null, agent) : null));
        Map<String, Object> props = client.getProperties();
        props.put("version", "0.1");
        props.put("uri", uri);
        props.put("protocols", new String[]{(protocol != null) ? protocol : WAMP.WS_SUB_PROTOCOL_JSON});
        int port = uri.getPort();
        if (port <= 0) {
            try {
                port = uri.toURL().getDefaultPort();
            } catch (Throwable th) {
            }
        }
        onClientCreated(client);
        client.addWAMPNodeListener(listeners.get());
        if (tcph.isRegistered()) {
            SocketAddress wsSA = new InetSocketAddress(InetAddress.getByName(uri.getHost()), port);
            SocketChannel provider = tcph.connect(wsSA, diWS);
            diWS.addPendingWebSocketClientHandshake(provider, new DI_WS.WebSocketHandshakeListener() {
                @Override
                public void onWebSocketClientHandshake(Channel provider, WebSocket ws) throws IOException {
                    WAMPClient client = clients.get(provider);
                    URI uri = (URI) client.getProperties().get("uri");
                    String[] protocols = ws.getAddOns().getProposedProtocols();
                    String[] extensions = ws.getAddOns().getProposedExtensions();
                    String version = (String) client.getProperties().get("version");
                    int wsVersion = 0;
                    String origin = null;
                    ws.handshake(version, uri.getPath(), uri.getHost(), origin, protocols, extensions, wsVersion);
                }
            });
            clients.put(provider, client);
        } else {
            pendingClients.add(client);
        }
        return client;
    }

    /**
     * Override to perform any client pre-configuration before connecting it...
     *
     * @param client
     * @throws IOException
     */
    public void onClientCreated(WAMPClient client) throws IOException {
    }

    public Collection<WAMPClient> getClients() {
        synchronized (clients) {
            List<WAMPClient> r = new ArrayList<>();
            r.addAll(clients.values());
            return r;
        }
    }

    /**
     * @return the statistics
     */
    public WAMPStatistics getStatistics() {
        return statistics;
    }

    /**
     * @param statistics the statistics to set
     */
    public void setStatistics(WAMPStatistics statistics) {
        this.statistics = statistics;
    }

    /**
     * Block reading input until accumulated input messages is less than max
     * allowed.
     *
     * @return the maxInputQueueSize
     */
    public int getMaxInputQueueSize() {
        return maxInputQueueSize;
    }

    /**
     * @param maxInputQueueSize the maxInputQueueSize to set
     */
    public void setMaxInputQueueSize(int maxInputQueueSize) {
        this.maxInputQueueSize = maxInputQueueSize;
    }

    /**
     * Register WAMP node listener(s). No action if null or already registered.
     *
     * @param ls
     */
    public void addWAMPNodeListener(WAMPNodeListener... ls) {
        listeners.add(ls);
    }

    /**
     * Unregister WAMP node listeners. No action if null or not registered.
     *
     * @param ls
     */
    public void removeWAMPNodeListener(WAMPNodeListener... ls) {
        listeners.remove(ls);
    }
}
