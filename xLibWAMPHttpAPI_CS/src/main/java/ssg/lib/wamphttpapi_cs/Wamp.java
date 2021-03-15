/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ssg.lib.wamphttpapi_cs;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.Channel;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import ssg.lib.cs.ws.WebSocket_CSGroup;
import ssg.lib.net.CS;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPFeatureProvider;
import ssg.lib.wamp.WAMPRealmFactory;
import ssg.lib.wamp.cs.WAMPClient_WSProtocol;
import ssg.lib.wamp.cs.WAMPRouter_WSProtocol;
import ssg.lib.wamp.cs.WSCSCounters;
import ssg.lib.wamp.nodes.WAMPClient;
import ssg.lib.wamp.nodes.WAMPNode;
import ssg.lib.wamp.nodes.WAMPRouter;
import ssg.lib.wamp.stat.WAMPStatistics;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.websocket.WebSocket;

/**
 *
 * @author sesidoro
 */
public class Wamp {

    public boolean TRACE_MESSAGES = false;
    public int CLIENT_CYCLES_DELAY = 300;
    public byte CLIENT_CYCLE_DELAY = 2;

    // WAMP sockets transport support
    WAMPRouter_WSProtocol routerCS;
    WAMPClient_WSProtocol clientCS;
    WebSocket_CSGroup wsGroup = new WebSocket_CSGroup();
    ScheduledFuture<?> executor; // process to perform regular execution of wampServer.runCycle
    // counters
    WSCSCounters counters = new WSCSCounters();

    // extra wamp features
    Map<WAMPFeature, WAMPFeatureProvider> features = new LinkedHashMap<>();
    WAMPRealmFactory realmFactory;

    public void onStarted(CS cs) {
        wsGroup.onStarted(cs);
        if (clientCS != null) {
            executor = cs.getScheduledExecutorService().scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    String old = Thread.currentThread().getName();
                    Thread.currentThread().setName("wamp-client-run-" + System.identityHashCode(Wamp.this));
                    try {
                        while (executor != null && !executor.isCancelled()) {
                            try {
                                Collection<WAMPClient> cclients = clientCS.getClients();
                                WAMPClient[] clients = null;
                                synchronized (cclients) {
                                    clients = cclients.toArray(new WAMPClient[cclients.size()]);
                                }
                                long timeout = System.currentTimeMillis() + CLIENT_CYCLES_DELAY;
                                while (System.currentTimeMillis() < timeout) {
                                    for (WAMPClient client : clients) {
                                        client.runCycle();
                                    }
                                    Thread.sleep(CLIENT_CYCLE_DELAY > 0 ? CLIENT_CYCLE_DELAY : 2);
                                }
                            } catch (WAMPException wex) {
                                //
                            }
                        }
                    } catch (InterruptedException iex) {
                    } finally {
                        executor = null;
                        Thread.currentThread().setName(old);
                    }
                }
            }, 5, 10, TimeUnit.MILLISECONDS);
        }
    }

    public void onStop(CS cs) {
//        if (clientCS != null) {
//            WAMPClient[] clients = clientCS.getClients().toArray(new WAMPClient[clientCS.getClients().size()]);
//            for (WAMPClient client : clients)try {
//                client.disconnect(null);
//            } catch (WAMPException wex) {
//            }
//        }
        wsGroup.onStop(cs);
        if (executor != null) {
            executor.cancel(true);
        }
    }

    public <Z extends Wamp> Z configure(WAMPRealmFactory realmFactory) {
        this.realmFactory = realmFactory;
        if (clientCS != null) {
            clientCS.configure(realmFactory);
        }
        if (routerCS != null) {
            routerCS.configure(realmFactory);
        }
        return (Z) this;
    }

    public <Z extends Wamp> Z configureClient(boolean withStatistics, final WebSocket.FrameMonitor fm) {
        if (clientCS == null) {
            clientCS = new WAMPClient_WSProtocol() {
                @Override
                public boolean onConsume(Channel provider, WebSocket ws, Object message) throws IOException {
                    if (TRACE_MESSAGES) {
                        System.out.println("[" + System.currentTimeMillis() + "][WC-" + ws.id + "]-IN: " + ("" + message).replace("\n", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ").replace("  ", " "));
                    }
                    return super.onConsume(provider, ws, message);
                }

                @Override
                public void onSend(WebSocket ws, Object message) {
                    if (TRACE_MESSAGES) {
                        System.out.println("[" + System.currentTimeMillis() + "][WC-" + ws.id + "]-OU: " + ("" + message).replace("\n", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ").replace("  ", " "));
                    }
                }

                @Override
                public void initialize(Channel provider, WebSocket ws) {
                    if (fm != null) {
                        ws.setFrameMonitor(fm);
                    }
                    super.initialize(provider, ws);
                }
            }.configure(realmFactory);
        }
        if (withStatistics) {
            clientCS.setStatistics(new WAMPStatistics("wamp-cs-client"));
        }
        if (counters != null) {
            counters.registerClients(clientCS);
        }
        if (!features.isEmpty()) {
            for (Entry<WAMPFeature, WAMPFeatureProvider> entry : features.entrySet()) {
                clientCS.configure(entry.getKey(), entry.getValue());
            }
        }
        wsGroup.addWebSocketProtocolHandler(clientCS);
        return (Z) this;
    }

    public <Z extends Wamp> Z configureRouter(boolean withStatistics, final WebSocket.FrameMonitor fm) {
        if (routerCS == null) {
            routerCS = new WAMPRouter_WSProtocol() {
                @Override
                public boolean onConsume(Channel provider, WebSocket ws, Object message) throws IOException {
                    if (TRACE_MESSAGES) {
                        System.out.println("[" + System.currentTimeMillis() + "][WR-" + ws.id + "]-IN: " + ("" + message).replace("\n", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ").replace("  ", " "));
                    }
                    return super.onConsume(provider, ws, message);
                }

                @Override
                public void onSend(WebSocket ws, Object message) {
                    if (TRACE_MESSAGES) {
                        System.out.println("[" + System.currentTimeMillis() + "][WR-" + ws.id + "]-OU: " + ("" + message).replace("\n", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ").replace("  ", " ").replace("  ", " "));
                    }
                }

                @Override
                public void initialize(Channel provider, WebSocket ws) {
                    if (fm != null) {
                        ws.setFrameMonitor(fm);
                    }
                    super.initialize(provider, ws);
                }
            }.configure(realmFactory);
            if (withStatistics) {
                routerCS.getRouter().setStatistics(new WAMPStatistics("wamp-cs-router"));
            }
            if (counters != null) {
                counters.registerRouter(routerCS);
            }
            if (!features.isEmpty()) {
                for (Entry<WAMPFeature, WAMPFeatureProvider> entry : features.entrySet()) {
                    routerCS.configure(entry.getKey(), entry.getValue());
                }
            }
            wsGroup.addWebSocketProtocolHandler(routerCS);
        }
        return (Z) this;
    }

    public <Z extends Wamp> Z configureFeature(WAMPFeature feature, WAMPFeatureProvider featureProvider) {
        if (feature != null) {
            features.put(feature, featureProvider);
            if (feature != null) {
                if (routerCS != null) {
                    routerCS.configure(feature, featureProvider);
                }
                if (clientCS != null) {
                    clientCS.configure(feature, featureProvider);
                }
            }
        }
        return (Z) this;
    }

    public boolean supportsFeature(WAMPFeature f) {
        return f != null && features != null && features.containsKey(f);
    }

    public <Z extends Wamp> Z trace(boolean enableTrace) {
        TRACE_MESSAGES = enableTrace;
        return (Z) this;
    }

    public WebSocket_CSGroup getCSGroup() {
        return wsGroup;
    }

    public WAMPRouter getRouter() {
        return routerCS != null ? routerCS.getRouter() : null;
    }

    public WAMPClient connect(
            URI uri,
            String api,
            WAMPFeature[] features,
            String authid,
            String agent,
            String realm,
            WAMP.Role... roles
    ) throws WAMPException {
        //System.out.println("++++++++++++++++++ wamp.connect: realm="+realm+", authid="+authid);
        WAMPClient client = null;
        try {
            client = clientCS.connect(uri, api, features, authid, agent, realm, roles);
            return client;
        } catch (WAMPException wex) {
            throw wex;
        } catch (IOException ioex) {
            throw new WAMPException(ioex);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{"
                + "TRACE_MESSAGES=" + TRACE_MESSAGES
                + ", routerCS=" + (routerCS != null)
                + ", clientCS=" + (clientCS != null)
                + "\n  wsGroup=" + wsGroup.toString().replace("\n", "\n    ")
                + '}';
    }

    public void addRouterListener(WAMPNode.WAMPNodeListener... ls) {
        if (routerCS != null) {
            routerCS.getRouter().addWAMPNodeListener(ls);
        }
    }

    public void removeRouterListener(WAMPNode.WAMPNodeListener... ls) {
        if (routerCS != null) {
            routerCS.getRouter().removeWAMPNodeListener(ls);
        }
    }

//    public class WSCSCountersREST {
//
//        public Map getStructure(Boolean activeOnly) {
//            long timestamp = System.currentTimeMillis();
//            Map r = counters.getTree((activeOnly != null) ? activeOnly : false);
//            stat2rest(r, false);
//            r.put("timestamp", timestamp);
//            return r;
//        }
//
//        public Map getModified() {
//            Map r = counters.getLastModified();
//            stat2rest(r, false);
//            return r;
//        }
//
//        public Map getUpdates(Long after, Boolean activeOnly, Boolean compact) {
//            long timestamp = System.currentTimeMillis();
//            Map r = counters.getUpdates((after != null) ? after : 0, (activeOnly != null) ? activeOnly : false, (compact != null) ? compact : true);
//            stat2rest(r, (compact != null) ? compact : true);
//            r.put("timestamp", timestamp);
//            return r;
//        }
//
//        <T> T stat2rest(Object object, boolean compact) {
//            if (object instanceof Map) {
//                Map map = (Map) object;
//                for (Object key : map.keySet()) {
//                    Object v = map.get(key);
//                    Object v2 = stat2rest(v, compact);
//                    if (v2 != v) {
//                        map.put(key, v2);
//                    }
//                }
//            } else if (object instanceof Statistics) {
//                return (T) ((Statistics) object).dumpStatistics(compact);
//            } else if (object instanceof Collection) {
//                Map changed = null;
//                Iterator it = ((Collection) object).iterator();
//                while (it.hasNext()) {
//                    Object o = it.next();
//                    Object o2 = stat2rest(o, compact);
//                    if (o2 != o) {
//                        if (changed == null) {
//                            changed = new HashMap();
//                        }
//                        changed.put(o, o2);
//                    }
//                }
//                if (changed != null) {
//                    List l = new ArrayList();
//                    for (Object o : ((Collection) object)) {
//                        if (changed.containsKey(o)) {
//                            l.add(changed.get(o));
//                        } else {
//                            l.add(o);
//                        }
//                    }
//                    object = l;
//                }
//            } else if (object != null && object.getClass().isArray() && !object.getClass().getComponentType().isPrimitive()) {
//                Object[] r = new Object[Array.getLength(object)];
//                boolean changed = false;
//                for (int i = 0; i < r.length; i++) {
//                    Object o = Array.get(object, i);
//                    Object o2 = stat2rest(o, compact);
//                    r[i] = o2;
//                    if (o2 != o) {
//                        changed = true;
//                    }
//                }
//                if (changed) {
//                    object = r;
//                }
//            }
//            return (T) object;
//        }
//    }
}
