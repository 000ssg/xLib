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
import java.util.Map.Entry;
import ssg.lib.common.CommonTools;
import ssg.lib.common.net.NetTools;
import ssg.lib.net.CS;
import ssg.lib.net.TCPHandler;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.nodes.WAMPClient;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPFeatureProvider;
import ssg.lib.wamp.WAMPRealmFactory;
import ssg.lib.wamp.auth.WAMPAuth;
import ssg.lib.wamp.nodes.WAMPNode.WAMPNodeListener;
import ssg.lib.wamp.nodes.WAMPRouter;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.wamp.stat.WAMPStatistics;
import ssg.lib.wamp.util.LS;
import ssg.lib.wamp.util.WAMPTransportList;
import ssg.lib.websocket.WebSocket;
import ssg.lib.websocket.WebSocket.WebSocketLifecycleListener;
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
    // enable support of direct (no WS) router clients
    Map<Channel, WAMPClient> directClients = WAMPTools.createSynchronizedMap(true);
    Runnable directClientsHandler = null;
    //
    WAMPFeature[] defaultFeatures = new WAMPFeature[0];
    Map<WAMPFeature, WAMPFeatureProvider> wampFeatureProviders = WAMPTools.createMap(true);
    WAMPRealmFactory realmFactory;

    public WAMPClient_WSProtocol() {
    }

    public WAMPClient_WSProtocol configure(WAMPFeature feature, WAMPFeatureProvider featureProvider) {
        if (feature != null) {
            if (featureProvider != null) {
                wampFeatureProviders.put(feature, featureProvider);
            } else {
                wampFeatureProviders.remove(feature);
            }
        }
        return this;
    }

    public Map<WAMPFeature, WAMPFeatureProvider> getFeatureProviders() {
        return wampFeatureProviders;
    }

    public WAMPClient_WSProtocol configure(WAMPRealmFactory realmFactory) {
        this.realmFactory = realmFactory;
        return this;
    }

    public WAMPClient_WSProtocol configure(WAMPFeature feature) {
        defaultFeatures = WAMPFeature.mergeCopy(defaultFeatures, feature);
        return this;
    }

    @Override
    public boolean canInitialize(Channel provider, WebSocket ws) {
        return (provider == null || provider.isOpen()) && ws != null && ws.isClient() && WAMP.WS_SUB_PROTOCOL_JSON.equals(ws.getProtocol());
    }

    @Override
    public synchronized void initialize(Channel provider, final WebSocket ws) {
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
            for (Entry<WAMPFeature, WAMPFeatureProvider> entry : getFeatureProviders().entrySet()) {
                wc.configure(entry.getKey(), entry.getValue());
            }
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
                if (directClients.containsKey(provider)) {
                    directClients.remove(provider);
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
                            if (directClients.containsKey(key)) {
                                directClients.remove(key);
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
     * @param authid
     * @param agent
     * @param realm
     * @param roles
     * @return
     * @throws IOException
     */
    public WAMPClient connect(URI uri, String protocol, Map<String, String> httpHeaders, WAMPFeature[] features, String authid, String agent, String realm, WAMP.Role... roles) throws IOException {
        WAMPClient client = new WAMPClient(authid)
                .configure(realmFactory)
                .configure((WAMPStatistics) ((statistics != null) ? statistics.createChild(null, agent) : null))
                .configure(null, WAMPFeature.mergeCopy(defaultFeatures, features), agent, realm, roles);
        for (Entry<WAMPFeature, WAMPFeatureProvider> entry : getFeatureProviders().entrySet()) {
            client.configure(entry.getKey(), entry.getValue());
        }
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
            synchronized (clients) {
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
                        ws.addWebSocketLifecycleListener(new WebSocketLifecycleListener() {
                            @Override
                            public void onOpened(WebSocket ws, Object... parameters) {
                            }

                            @Override
                            public void onInitialized(WebSocket ws) {
                            }

                            @Override
                            public void onClosed(WebSocket ws, Object... parameters) {
                                delete(
                                        parameters != null && parameters.length > 0 && parameters[0] instanceof Channel
                                                ? (Channel) parameters[0]
                                                : null,
                                        ws
                                );
                            }
                        });
                        ws.handshake(version, uri.getPath(), uri.getHost(), origin, protocols, extensions, wsVersion, httpHeaders);
                    }
                });
                clients.put(provider, client);
            }
        } else {
            pendingClients.add(client);
        }
        return client;
    }

    /**
     * Direct router connection (bypassing WS/TCP transport.
     *
     * @param router
     * @param authid
     * @param agent
     * @param realm
     * @param roles
     * @return
     * @throws IOException
     */
    public WAMPClient connect(WAMPRouter router, WAMPAuth auth, WAMPFeature[] features, String authid, String agent, String realm, WAMP.Role... roles) throws IOException {
        WAMPTransportList.WAMPTransportLoop crt = new WAMPTransportList.WAMPTransportLoop();
        if (auth != null) {
            crt.remote.configureAuth(auth);
        }

        WAMPClient client = new WAMPClient(authid)
                .configure(realmFactory)
                .configure((WAMPStatistics) ((statistics != null) ? statistics.createChild(null, agent) : null))
                .configure(crt.local, WAMPFeature.mergeCopy(defaultFeatures, features), agent, realm, roles);
        for (Entry<WAMPFeature, WAMPFeatureProvider> entry : getFeatureProviders().entrySet()) {
            client.configure(entry.getKey(), entry.getValue());
        }
        Map<String, Object> props = client.getProperties();
        props.put("version", "0.1");
        props.put("router", router.getNodeId());
        onClientCreated(client);
        client.addWAMPNodeListener(listeners.get());

        Channel ch = new Channel() {
            boolean open = true;

            @Override
            public boolean isOpen() {
                return open;
            }

            @Override
            public void close() throws IOException {
                open = false;
            }
        };
        clients.put(ch, client);
        directClients.put(ch, client);
        onDirectClientCreated(client);

        client.connect();
        router.onNewTransport(crt.remote);

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

    public void onDirectClientCreated(WAMPClient client) throws IOException {
        synchronized (directClients) {
            if (directClientsHandler == null) {
                final long id = System.identityHashCode(this);
                directClientsHandler = new Runnable() {
                    @Override
                    public void run() {
                        String old = Thread.currentThread().getName();
                        Thread.currentThread().setName("WAMPClient_WSProtocol_direct-" + id);
                        try {
                            while (!directClients.isEmpty()) {
                                Entry<Channel, WAMPClient>[] cls = null;
                                synchronized (directClients) {
                                    cls = directClients.entrySet().toArray(new Entry[directClients.size()]);
                                }
                                for (Entry<Channel, WAMPClient> cl : cls) {
                                    try {
                                        cl.getValue().runCycle();
                                    } catch (Throwable th) {
                                        th.printStackTrace();
                                    }
                                }
                                NetTools.delay(1);
                            }
                        } finally {
                            directClientsHandler = null;
                            Thread.currentThread().setName(old);
                        }
                    }
                };
                cs.getScheduledExecutorService().execute(directClientsHandler);
            }
        }
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
