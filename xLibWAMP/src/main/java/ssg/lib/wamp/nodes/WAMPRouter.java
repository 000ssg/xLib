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
package ssg.lib.wamp.nodes;

import ssg.lib.wamp.util.WAMPException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.WAMPActor;
import ssg.lib.wamp.WAMPConstantsBase;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPRealm;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.WAMPSessionImpl;
import ssg.lib.wamp.WAMPSessionState;
import ssg.lib.wamp.WAMPTransport;
import ssg.lib.wamp.flows.WAMPMessagesFlow;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMPMessageType;
import ssg.lib.wamp.rpc.WAMPDealer;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.wamp.stat.WAMPMessageStatistics;
import ssg.lib.wamp.util.WAMPNodeSessionManagement;

/**
 * WAMP Router base registers shared realms and keeps track of WAMP sessions
 * associated with WAMP transports.
 *
 * Router runCycle is sequence of per-transport run cycles.
 *
 * @author 000ssg
 */
public class WAMPRouter extends WAMPNode implements WAMPNodeSessionManagement {

    AtomicLong nextSessionId = new AtomicLong(1);

    Role[] roles = new Role[]{Role.broker};
    Map<String, WAMPRealm> realms = WAMPTools.createSynchronizedMap();
    Map<WAMPTransport, WAMPSession> sessions = WAMPTools.createSynchronizedMap();
    private int maxPendingMessagesQueue = 100;

    public WAMPRouter() {
    }

    public WAMPRouter(Role... roles) {
        this.roles = roles;
    }

    public void onNewTransport(WAMPTransport transport) {
        sessions.put(transport, WAMPSessionImpl.NO_SESSION);
        if (!listeners.isEmpty()) {
            for (WAMPNodeListener l : listeners.get()) {
                if (l instanceof WAMPRouterNodeListener) {
                    try {
                        ((WAMPRouterNodeListener) l).onNewTransport(transport);
                    } catch (Throwable th) {
                        this.onListenerError(null, null, null, l, th, transport);
                    }
                }
            }
        }
        transport.addWAMPTransportMessageListener(tmListeners.get());
    }

    public void onClosedTransport(WAMPTransport transport) {
        WAMPSession session = sessions.remove(transport);
        transport.removeWAMPTransportMessageListener(tmListeners.get());
        if (!listeners.isEmpty()) {
            for (WAMPNodeListener l : listeners.get()) {
                if (l instanceof WAMPRouterNodeListener) {
                    try {
                        ((WAMPRouterNodeListener) l).onClosedTransport(transport);
                    } catch (Throwable th) {
                        this.onListenerError(null, null, null, l, th, transport);
                    }
                }
            }
        }
        if (session.getCloseReason() == null) {
            session.setCloseReason("on.closing.transport");
        }
        onSessionRemoved(session);
    }

    public WAMPRealm find(String realm) {
        return realms.get(realm);
    }

    public Map<String, Object> getMeta(Map<String, Object> meta) {
        if (meta == null) {
            meta = WAMPTools.createMap(true);
        }

        Map<String, Object> rs = WAMPTools.createMap(true);
        meta.put("realms", rs);
        try {
            for (String s : realms.keySet()) {
                WAMPRealm r = realms.get(s);
                Map<String, Object> ri = WAMPTools.createMap(true);
                rs.put(r.getName(), ri);
                for (Role ar : Role.values()) {
                    WAMPActor a = r.getActor(ar);
                    if (a != null) {
                        ri.put("" + ar, "" + a);
                    }
                }
            }
        } catch (Throwable th) {
        }

        Map<String, Object> ss = WAMPTools.createMap(true);
        meta.put("sessions", ss);
        try {
            for (WAMPTransport t : sessions.keySet()) {
                WAMPSession s = sessions.get(t);
                ss.put("" + s.getId(), "" + s);
            }
        } catch (Throwable th) {
        }

        Map<String, Object> as = WAMPTools.createMap(true);
        meta.put("activity", as);
        try {
        } catch (Throwable th) {
        }

        return meta;
    }

    /**
     * Performs timeout checks and per-transport run cycles
     *
     * @throws WAMPException
     */
    @Override
    public void runCycle() throws WAMPException {
        if (!sessions.isEmpty()) {
            Object[] transports = sessions.keySet().toArray();

            if (transports != null) {
                for (Object to : transports) {
                    WAMPTransport transport = (WAMPTransport) to;
                    try {
                        runCycle(transport);
                    } catch (WAMPException wex) {
                        wex.printStackTrace();
                        WAMPSession session = sessions.remove(transport);
                        if (session.getCloseReason() == null) {
                            session.setCloseReason("router.runcycle.wamp.error");
                        }
                        onSessionRemoved(session);
                    } catch (Throwable th) {
                        th.printStackTrace();
                        WAMPSession session = sessions.remove(transport);
                        if (session.getCloseReason() == null) {
                            session.setCloseReason("router.runcycle.generic.error");
                        }
                        onSessionRemoved(session);
                    }
                }
            }
        }
    }

    @Override
    public WAMPRealm createRealm(Object context, String name, WAMPFeature[] features, Role... roles) throws WAMPException {
        if (realms.containsKey(name)) {
            return realms.get(name);
        }
        WAMPRealm realm = super.createRealm(context, name, getNodeFeatures(features), roles);
//        if (getStatistics() != null) {
//            realm.setStatistics(getStatistics().createChild(null, "realm." + name));
//        }
        realms.put(name, realm);
        realm.init();
        return realm;
    }

    public int getSessionsCount() {
        return sessions.size();
    }

    @Override
    public long nextSessionId() {
        return this.nextSessionId.getAndIncrement();
    }

    public void runCycle(final WAMPTransport transport) throws WAMPException {
        WAMPSession session = sessions.get(transport);
        if (session == WAMPSessionImpl.NO_SESSION) {
            session = null;
        }

        if (session != null) {
            WAMPDealer wd = session.getRealm().getActor(Role.dealer);
            if (wd != null) {
                wd.checkTimeout(session);
            }
        }

        boolean tooBusy = session != null && getMaxPendingMessagesQueue() > 0 && session.getPendingCount() > getMaxPendingMessagesQueue();

        // get last unhandled or next received message
        WAMPMessage msg = transport.receive();
        while (msg != null) {
            //System.out.println("ROUTER in: " + msg);
            // prepare session if needed
            if (session == null || WAMPSessionState.closed == session.getState()) {
                if (WAMPMessageType.T_HELLO == msg.getType().getId()) {
                    String realmS = msg.getUri(0);
                    WAMPRealm realm = createRealm(transport, realmS, getNodeFeatures(), roles);
                    session = createSession(transport, realm, roles);
                    session.getLocal().setAgent(getAgent());
                    session.setId(nextSessionId());
                    if (session.getStatistics() != null) {
//                        session.setStatistics(realm.getStatistics().createChild(null, ""
//                                + "session"
//                                + "." + realm.getName()
//                                + "." + getAgent()
//                                + "." + session.getId()
//                        ));
                        // ensure session statistics is assigned to session transport.
                        WAMPMessageStatistics twms = (session.getStatistics() != null) ? session.getStatistics().createChildMessageStatistics("transport") : null;
                        transport.setStatistics(twms);
                        // ensure HELLO message is counted...
                        if (twms != null) {
                            twms.onReceived(msg);
                        }
                    }
                    sessions.put(transport, session);
                } else {
                    if (WAMPMessageType.GOODBYE == msg.getType() || WAMPMessageType.ABORT == msg.getType()) {
                        int a = 0;
                    } else {
                        throw new WAMPException("No or closed session. Expect HELLO message to establish session. Got " + msg.getType());
                    }
                }
            }
            // process message
            boolean lastIsBusy = false;
            if (session != null) {
                try {
                    boolean done = false;
                    for (WAMPMessagesFlow mf : session.getFlows()) {
                        if (mf.canHandle(session, msg)) {
                            switch (mf.handle(session, msg)) {
                                case handled:
                                    onHandled(session, msg, mf);
                                    done = true;
                                    break;
                                case busy:
                                    transport.unreceive(msg);
                                    lastIsBusy = true;
                                    done = true;
                                    break;
                                case ignored:
                                    break;
                                case failed:
                                    onFailed(session, msg, mf);
                                    done = true;
                                //throw new WAMPException("Invalid/unhandled message: " + msg);
                            }
                            if (done) {
                                break;
                            }
                        }
                    }
                    if (!done) {
                        onFatal(session, msg);
                        throw new WAMPException("Unhandled message: " + msg + ": " + msg.toList());
                    }
                } catch (WAMPException wex) {
                    wex.printStackTrace();
                    session.send(WAMPMessage.abort(WAMPTools.createDict("message", msg.toList(), "error", WAMPTools.getStackTrace(wex)),
                            WAMPConstantsBase.ERROR_ProtocolViolation));
                }
            }
            tooBusy = false;//session != null && session.getPendingCount() > getMaxPendingMessagesQueue();
            if (!tooBusy && !lastIsBusy && session != null && WAMPSessionState.established == session.getState()) {
                msg = transport.receive();
            } else {
                msg = null;
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append("{");
        sb.append(", roles[" + roles.length + "]=");
        sb.append(Arrays.asList(roles));
        sb.append(", realms[" + realms.size() + "]=");
        sb.append(realms.keySet());
        sb.append(", sessions[" + sessions.size() + "]=");
        if (!realms.isEmpty()) {
            sb.append("\n  Realms[" + realms.size() + "]:");
            for (Object obj : realms.values().toArray()) {
                WAMPRealm realm = (WAMPRealm) obj;
                if (realm != null) {
                    sb.append("\n    " + realm.toString().replace("\n", "\n    "));
                }
            }
        }
        if (!sessions.isEmpty()) {
            sb.append("\n  Sessions[" + sessions.size() + "]:");
            for (Object obj : sessions.values().toArray()) {
                WAMPSession session = (WAMPSession) obj;
                if (session == null) {
                    sb.append("\n   <none>");
                } else {
                    sb.append("\n    " + session.toString().replace("\n", "\n    "));
                }
            }
        }

        if (!realms.isEmpty() || !sessions.isEmpty()) {
            sb.append('\n');
        }

        if (getStatistics() != null) {
            if (sb.charAt(sb.length() - 1) != '\n') {
                sb.append('\n');
            }
            sb.append("  " + getStatistics().dumpStatistics(false).replace("\n", "\n  "));
            sb.append('\n');
        }

        sb.append('}');
        return sb.toString();
    }

    /**
     * Perform any extra session closing actions if needed here. This method is
     * invoked just after removing session from router sessions.
     *
     * @param session
     */
    public void onSessionRemoved(WAMPSession session) {
        if (session != null && WAMPSessionState.established == session.getState()) {
            try {
                session.close();
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    /**
     * Add transport add/remove events to standard node listener
     */
    public static interface WAMPRouterNodeListener extends WAMPNodeListener {

        void onNewTransport(WAMPTransport transport);

        void onClosedTransport(WAMPTransport transport);
    }

    /**
     * Add transport add/remove events to standard node listener
     */
    public static interface WAMPRouterMessageListener extends WAMPNodeListener {

        void onMessageReceived(WAMPTransport transport, WAMPMessage msg);
        void onMessageSent(WAMPTransport transport, WAMPMessage msg);
    }

    /**
     * @return the maxPendingMessagesQueue
     */
    public int getMaxPendingMessagesQueue() {
        return maxPendingMessagesQueue;
    }

    /**
     * @param maxPendingMessagesQueue the maxPendingMessagesQueue to set
     */
    public void setMaxPendingMessagesQueue(int maxPendingMessagesQueue) {
        this.maxPendingMessagesQueue = maxPendingMessagesQueue;
    }

}
