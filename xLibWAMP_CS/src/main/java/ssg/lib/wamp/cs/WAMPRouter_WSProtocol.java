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
import java.nio.channels.Channel;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import ssg.lib.http.HttpSession;
import ssg.lib.http.HttpUser;
import ssg.lib.net.CS;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPFeatureProvider;
import ssg.lib.wamp.WAMPRealm;
import ssg.lib.wamp.WAMPRealmFactory;
import ssg.lib.wamp.nodes.WAMPRouter;
import ssg.lib.wamp.WAMPTransport;
import ssg.lib.wamp.WAMPTransport.WAMPTransportMessageListener;
import ssg.lib.wamp.auth.WAMPAuth;
import ssg.lib.wamp.messages.WAMP_DT;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.wamp.stat.WAMPStatistics;
import ssg.lib.websocket.WebSocket;
import ssg.lib.websocket.impl.WebSocketProtocolHandler;

/**
 *
 * @author 000ssg
 */
public class WAMPRouter_WSProtocol implements WebSocketProtocolHandler {

    WAMPRouter wampRouter;
    ScheduledFuture<?> executor; // process to perform regular execution of wampServer.runCycle
    Map<WebSocket, WAMPTransportJSON> wampTransports = WAMPTools.createSynchronizedMap(false);
    private int maxInputQueueSize = 100;

    public WAMPRouter_WSProtocol() {
        wampRouter = new WAMPRouter(Role.broker, Role.dealer) {
            @Override
            public synchronized WAMPRealm createRealm(Object context, String name, WAMPFeature[] features, Role... roles) throws WAMPException {
                if (!canCreateRealm(name, roles)) {
                    throw new WAMPException("Unsupported realm: " + name + " for " + ((roles != null) ? Arrays.asList(roles) : "<no roles>"));
                }
                WAMPRealm r = super.createRealm(context, name, features, roles);
                return r;
            }
        }
                .configure(WAMPFeature.registration_meta_api, WAMPFeature.shared_registration)
                .configure(new WAMPStatistics("router"));
    }

    public WAMPRouter_WSProtocol(Role[] roles, final WAMPFeature... routerFeatures) {
        wampRouter = new WAMPRouter(roles) {
            @Override
            public synchronized WAMPRealm createRealm(Object context, String name, WAMPFeature[] features, Role... roles) throws WAMPException {
                if (!canCreateRealm(name, roles)) {
                    throw new WAMPException("Unsupported realm: " + name + " for " + ((roles != null) ? Arrays.asList(roles) : "<no roles>"));
                }
                WAMPRealm r = super.createRealm(context, name, features, roles);
                return r;
            }
        }
                .configure(WAMPFeature.registration_meta_api, WAMPFeature.shared_registration)
                .configure(new WAMPStatistics("router"));
    }

    public WAMPRouter_WSProtocol(WAMPRouter router) {
        wampRouter = router;
    }

    public WAMPRouter getRouter() {
        return wampRouter;
    }

    public WAMPRouter_WSProtocol configure(WAMPFeature feature, WAMPFeatureProvider featureProvider) {
        if (feature != null) {
            wampRouter.configure(feature, featureProvider);
        }
        return this;
    }

    public WAMPRouter_WSProtocol configure(WAMPFeature feature) {
        if (feature != null) {
            wampRouter.configure(feature);
        }
        return this;
    }

    public WAMPRouter_WSProtocol configure(WAMPRealmFactory realmFactory) {
        wampRouter.configure(realmFactory);
        return this;
    }

    public WAMPRouter_WSProtocol configure(WAMPTransportMessageListener l) {
        wampRouter.addWAMPTransportMessageListener(l);
        return this;
    }

    public boolean canCreateRealm(String name, Role... roles) {
        boolean isRouter = Role.hasRole(Role.router, roles);
        boolean isClient = Role.hasRole(Role.client, roles);
        return WAMP_DT.uri.validate(name) && (isRouter || isClient) && !(isRouter && isClient);
    }

    @Override
    public boolean canInitialize(Channel provider, WebSocket ws) {
        //return (provider == null || provider.isOpen() && ws != null && ws.isInitialized()) && ws != null && WAMP.WS_SUB_PROTOCOL_JSON.equals(ws.getProtocol()) && !ws.isClient();
        return (provider == null || provider.isOpen()) && ws != null && WAMP.WS_SUB_PROTOCOL_JSON.equals(ws.getProtocol()) && !ws.isClient();
    }

    @Override
    public void initialize(Channel provider, WebSocket ws) {
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
        if (ws.owner() instanceof HttpSession && ((HttpSession) ws.owner()).getUser() != null) {
            HttpUser user = ((HttpSession) ws.owner()).getUser();
            WAMPAuth auth = new WAMPAuth(
                    "http", // method
                    user.getId(), // authid
                    (user.getRoles() != null && !user.getRoles().isEmpty() ? user.getRoles().get(0) : null), // String role
                    new LinkedHashMap<>() //Map<String, Object> details
            );
            if (user.getProperties() != null) {
                auth.getDetails().putAll(user.getProperties());
            }
            if (user.getRoles() != null && user.getRoles().size() > 1) {
                auth.getDetails().put("http_roles", WAMPTools.createList(user.getRoles()));
            }
            transport.configureAuth(auth);
        }
        wampTransports.put(ws, transport);
        wampRouter.onNewTransport(transport);
    }

    public void onSend(WebSocket ws, Object message) {
    }

    @Override
    public void delete(Channel provider, WebSocket ws) {
        final WAMPTransport transport = (ws != null) ? wampTransports.get(ws) : null;
        // shutdown session
        if (transport != null) {
            try {
                wampRouter.onClosedTransport(transport);
            } catch (Throwable th) {
                th.printStackTrace();
            }
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
            return true;
        }
        return false;
    }

    @Override
    public boolean onProduce(Channel provider, WebSocket ws) throws IOException {
        WAMPTransport wt = (ws != null) ? wampTransports.get(ws) : null;
        if (wt != null) {
            wampRouter.runCycle(wt);
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
        CS cs = null;
        if (parameters != null) {
            for (Object p : parameters) {
                if (p instanceof CS) {
                    cs = (CS) p;
                    break;
                }
            }
        }
        executor = cs.getScheduledExecutorService().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                String old = Thread.currentThread().getName();
                Thread.currentThread().setName("wamp-router-run-" + System.identityHashCode(WAMPRouter_WSProtocol.this));
                try {
                    while (executor != null && !executor.isCancelled()) {
                        try {
                            wampRouter.runCycle();
                        } catch (WAMPException wex) {
                            onStop(parameters);
                        }
                        Thread.sleep(2);
                    }
                } catch (InterruptedException iex) {
                } finally {
                    executor = null;
                    Thread.currentThread().setName(old);
                }
            }
        }, 5, 10, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onStop(Object... parameters) {
        CS cs = null;
        if (parameters != null) {
            for (Object p : parameters) {
                if (p instanceof CS) {
                    cs = (CS) p;
                    break;
                }
            }
        }
        if (executor != null) {
            executor.cancel(false);
        }
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
}
