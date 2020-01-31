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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import ssg.lib.wamp.WAMPRealm;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.flows.WAMPMessagesFlow;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.nodes.WAMPNode.WAMPNodeListener;
import ssg.lib.wamp.util.stat.Statistics;

/**
 *
 * @author 000ssg
 */
public class WSCSCounters {

    Collection registered = new HashSet<>();
    Map<String, WAMPRealm> routerRStats = Collections.synchronizedMap(new HashMap<>());
    Map<Long, CTree> nodes = Collections.synchronizedMap(new HashMap<>());
    CTree routerTree;
    CTree clientsTree;
    long lastModifiedRouter;
    long lastModifiedRouterStruct;
    long lastModifiedClients;
    long lastModifiedClientsStruct;

    WAMPNodeListener routerListener = new WAMPNodeListener() {
        @Override
        public void onCreatedRealm(WAMPRealm realm) {
            routerRStats.put(realm.getName(), realm);
            try {
                CTree node = new CTree(routerTree, realm.getName(), realm.getStatistics());
                nodes.put((long) System.identityHashCode(realm), node);
                lastModifiedRouterStruct = System.currentTimeMillis();
            } catch (Throwable th) {
                int a = 0;
            }
        }

        @Override
        public void onEstablishedSession(WAMPSession session) {
            CTree parent = nodes.get((long) System.identityHashCode(session.getRealm()));
            CTree node = new CTree(parent, session.getRealm().getName() + ":" + session.getId() + ":" + session.getLocal().getAgent(), session.getStatistics());
            nodes.put((long) System.identityHashCode(session), node);
            lastModifiedRouter = System.currentTimeMillis();
            lastModifiedRouterStruct = System.currentTimeMillis();
        }

        @Override
        public void onClosedSession(WAMPSession session) {
            CTree node = nodes.remove((long) System.identityHashCode(session));
            if (node != null) {
                node.active = false;
                lastModifiedRouter = System.currentTimeMillis();
                lastModifiedRouterStruct = System.currentTimeMillis();
            }
        }

        @Override
        public void onHandled(WAMPSession session, WAMPMessage msg, WAMPMessagesFlow mf) {
            lastModifiedRouter = System.currentTimeMillis();
        }

        @Override
        public void onFailed(WAMPSession session, WAMPMessage msg, WAMPMessagesFlow mf) {
            lastModifiedRouter = System.currentTimeMillis();
        }

        @Override
        public void onFatal(WAMPSession session, WAMPMessage msg) {
            lastModifiedRouter = System.currentTimeMillis();
        }

        @Override
        public void onSent(WAMPSession session, WAMPMessage msg, Throwable error) {
            lastModifiedRouter = System.currentTimeMillis();
        }
    };

    WAMPNodeListener clientsListener = new WAMPNodeListener() {
        @Override
        public void onCreatedRealm(WAMPRealm realm) {
        }

        @Override
        public void onEstablishedSession(WAMPSession session) {
            CTree node = new CTree(clientsTree, session.getRealm().getName() + ":" + session.getId() + ":" + session.getLocal().getAgent(), session.getStatistics());
            nodes.put((long) System.identityHashCode(session), node);
            lastModifiedClients = System.currentTimeMillis();
            lastModifiedClientsStruct = System.currentTimeMillis();
        }

        @Override
        public void onClosedSession(WAMPSession session) {
            CTree node = nodes.remove((long) System.identityHashCode(session));
            if (node != null) {
                node.active = false;
                lastModifiedClients = System.currentTimeMillis();
                lastModifiedClientsStruct = System.currentTimeMillis();
            }
        }

        @Override
        public void onHandled(WAMPSession session, WAMPMessage msg, WAMPMessagesFlow mf) {
            lastModifiedClients = System.currentTimeMillis();
        }

        @Override
        public void onFailed(WAMPSession session, WAMPMessage msg, WAMPMessagesFlow mf) {
            lastModifiedClients = System.currentTimeMillis();
        }

        @Override
        public void onFatal(WAMPSession session, WAMPMessage msg) {
            lastModifiedClients = System.currentTimeMillis();
        }

        @Override
        public void onSent(WAMPSession session, WAMPMessage msg, Throwable error) {
            lastModifiedClients = System.currentTimeMillis();
        }
    };

    public WSCSCounters() {

    }

    public WSCSCounters(WAMPRouter_WSProtocol router, WAMPClient_WSProtocol clients) {
        registerRouter(router);
        registerClients(clients);
    }

    public void registerRouter(WAMPRouter_WSProtocol router) {
        if (routerTree == null && router != null) {
            routerTree = new CTree("router", router.getRouter().getStatistics());
        }
        if (router != null) {
            registered.add(router);
            router.getRouter().addWAMPNodeListener(routerListener);
        }
    }

    public void unregisterRouter(WAMPRouter_WSProtocol router) {
        if (router != null) {
            router.getRouter().removeWAMPNodeListener(routerListener);
            registered.remove(router);
        }
    }

    public void registerClients(WAMPClient_WSProtocol clients) {
        if (clientsTree == null && clients != null) {
            clientsTree = new CTree("clients", clients.getStatistics());
        }
        if (clients != null) {
            clients.addWAMPNodeListener(clientsListener);
            registered.add(clients);
        }
    }

    public void unregisterClients(WAMPClient_WSProtocol clients) {
        if (clients != null) {
            clients.removeWAMPNodeListener(clientsListener);
            registered.remove(clients);
        }
    }

    public void stop() {
        Object[] oo = registered.toArray();
        for (Object o : oo) {
            if (o instanceof WAMPClient_WSProtocol) {
                unregisterClients((WAMPClient_WSProtocol) o);
            } else if (o instanceof WAMPRouter_WSProtocol) {
                unregisterRouter((WAMPRouter_WSProtocol) o);
            }
        }
    }

    /**
     * Retrieve structure
     *
     * @param activeOnly
     * @return
     */
    public Map getTree(boolean activeOnly) {
        Map map = new LinkedHashMap();
        if (routerTree != null) {
            map.put("router", routerTree.toMap(activeOnly));
        }
        if (clientsTree != null) {
            map.put("clients", clientsTree.toMap(activeOnly));
        }
        return map;
    }

    public Map getLastModified() {
        Map map = new LinkedHashMap();
        if (routerTree != null) {
            Map rm = new LinkedHashMap();
            map.put("router", rm);
            rm.put("structure", lastModifiedRouterStruct);
            rm.put("stat", lastModifiedRouter);
        }
        if (clientsTree != null) {
            Map rm = new LinkedHashMap();
            map.put("clients", rm);
            rm.put("structure", lastModifiedClientsStruct);
            rm.put("stat", lastModifiedClients);
        }
        return map;
    }

    public Map getUpdates(long after, boolean activeOnly, boolean compact) {
        Map map = new LinkedHashMap();
        Long[] ll = new Long[0];
        synchronized (nodes) {
            ll = nodes.keySet().toArray(new Long[nodes.size()]);

        }
        for (Long id : ll) {
            CTree node = nodes.get(id);
            if (node == null) {
                continue;
            }
            if (!activeOnly || node.active) {
                if (after == 0 || node.stat != null && node.stat.lastModified() > after) {
                    map.put(node.id, node.stat);
                }
            }
        }
        for (CTree node : new CTree[]{routerTree, clientsTree}) {
            if (node == null) {
                continue;
            }
            if (!activeOnly || node.active) {
                if (after == 0 || node.stat != null && node.stat.lastModified() > after) {
                    map.put(node.id, node.stat);
                }
            }
        }

        return map;
    }

    public static class CTree {

        static final AtomicInteger NEXT_ID = new AtomicInteger();

        String id = "n" + NEXT_ID.incrementAndGet();
        boolean active = true;
        String name;
        Statistics stat;
        List<CTree> children;

        public CTree(String name, Statistics stat) {
            this.name = name;
            this.stat = stat;
        }

        public CTree(CTree parent, String name, Statistics stat) {
            this.name = name;
            this.stat = stat;
            if (parent != null) {
                parent.addChild(this);
            }
        }

        public void addChild(CTree child) {
            if (child != null) {
                synchronized (this) {
                    if (children == null) {
                        children = Collections.synchronizedList(new ArrayList<>());
                    }
                    children.add(child);
                }
            }
        }

        public Map toMap(boolean activeOnly) {
            Map r = new LinkedHashMap();
            r.put("id", id);
            r.put("name", name);
            r.put("stat", stat);
            if (stat != null) {
                r.put("ts", stat.lastModified());
            }
            if (children != null && !children.isEmpty()) {
                synchronized (children) {
                    CTree[] chs = children.toArray(new CTree[children.size()]);
                    List lst = new ArrayList();
                    r.put("children", lst);
                    for (CTree ch : chs) {
                        if (!activeOnly || ch.active) {
                            lst.add(ch.toMap(activeOnly));
                        }
                    }
                }
            }
            return r;
        }
    }
}
