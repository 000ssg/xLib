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
import ssg.lib.wamp.util.LS;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.WAMPActor;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPRealm;
import ssg.lib.wamp.WAMPRealm.WAMPRealmListener;
import ssg.lib.wamp.WAMPRealmFactory;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.WAMPSession.WAMPSessionExtendedListener;
import ssg.lib.wamp.WAMPSessionState;
import ssg.lib.wamp.WAMPTransport;
import ssg.lib.wamp.auth.WAMPAuthProvider;
import ssg.lib.wamp.WAMPFeatureProvider;
import ssg.lib.wamp.flows.WAMPMessagesFlow;
import ssg.lib.wamp.flows.WAMPSessionFlow;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.stat.WAMPStatistics;
import ssg.lib.wamp.util.WAMPTools;

/**
 * WAMP node is a base WAMP connection side: client or router.
 *
 * It provides re-usable functionality and events tracking for session lifecycle
 * (established/closed) and messages handling (handled, failed, fatal).
 *
 * To ensure data are processed the <method>runCycle</method> must be
 * implemented.
 *
 *
 * @author 000ssg
 */
public abstract class WAMPNode implements WAMPSessionExtendedListener, WAMPRealmListener {

    private static AtomicInteger NEXT_NODE_ID = new AtomicInteger(1);
    public static boolean DUMP_ESTABLISH_CLOSE = true;

    final int nodeId = NEXT_NODE_ID.getAndIncrement();
    LS<WAMPNodeListener> listeners = new LS<>(new WAMPNodeListener[0]);
    WAMPFeature[] defaultFeatures;
    private String agent;
    private WAMPStatistics statistics;
    private WAMPRealmFactory realmFactory;
    private List<WAMPAuthProvider> authProviders;
    private Map<WAMPFeature, WAMPFeatureProvider> featureProviders = WAMPTools.createSynchronizedMap();

    public <T extends WAMPNode> T configureAgent(String agent) {
        this.agent = agent;
        return (T) this;
    }

    public <T extends WAMPNode> T configure(WAMPFeature... features) {
        defaultFeatures = WAMPFeature.merge(defaultFeatures, features);
        return (T) this;
    }

    public <T extends WAMPNode> T configure(WAMPFeature feature, WAMPFeatureProvider provider) {
        if (feature == null) {
            // ignore unfeatured provider
        } else if (provider == null) {
            if (featureProviders.containsKey(feature)) {
                WAMPFeatureProvider p = featureProviders.remove(feature);
                if (p instanceof WAMPNodeListener) {
                    listeners.remove((WAMPNodeListener) provider);
                }
                defaultFeatures = WAMPFeature.remove(defaultFeatures, feature);
            }
        } else {
            featureProviders.put(feature, provider);
            if (provider instanceof WAMPNodeListener) {
                listeners.add((WAMPNodeListener) provider);
            }
            defaultFeatures = WAMPFeature.merge(defaultFeatures, feature);
        }
        return (T) this;
    }

    public <T extends WAMPNode> T configure(WAMPStatistics statistics) {
        this.statistics = statistics;
        return (T) this;
    }

    public <T extends WAMPNode> T configure(WAMPRealmFactory realmFactory) {
        this.realmFactory = realmFactory;
        return (T) this;
    }

    public <T extends WAMPNode> T configure(WAMPAuthProvider... authProviders) {
        if (authProviders != null && authProviders.length > 0) {
            if (this.authProviders == null) {
                this.authProviders = new ArrayList<>();
            }
            for (WAMPAuthProvider ap : authProviders) {
                if (ap != null && !this.authProviders.contains(ap)) {
                    this.authProviders.add(ap);
                }
            }
        }
        return (T) this;
    }

    /**
     * Regular actions to process input messages and errors.
     *
     * NOTE: output messages are pushed in WAMPSession.send method.
     *
     * @throws WAMPException
     */
    public abstract void runCycle() throws WAMPException;

    public int getNodeId() {
        return nodeId;
    }

    /**
     * @return the agent
     */
    public String getAgent() {
        return agent;
    }

    /**
     * @param agent the agent to set
     */
    public void setAgent(String agent) {
        this.agent = agent;
    }

//    /**
//     * Builder-style default features adder.
//     *
//     * @param <T>
//     * @param features
//     * @return
//     */
//    public <T extends WAMPNode> T addDefaultFeatures(WAMPFeature... features) {
//        defaultFeatures = WAMPFeature.merge(defaultFeatures, features);
//        return (T) this;
//    }
    /**
     * WAMP Realm builder. Override to validate.
     *
     * Realm is created based on set of default features and optionally provided
     * in the method.
     *
     * Once created, WAMP node listeners are notified.
     *
     * @param name
     * @param roles
     * @return
     * @throws WAMPException
     */
    public WAMPRealm createRealm(Object context, String name, WAMPFeature[] features, WAMP.Role... roles) throws WAMPException {
        WAMPRealm r = (getRealmFactory() != null)
                ? getRealmFactory().newRealm(context, name, getNodeFeatures(features), featureProviders, roles).addListener(this)
                : WAMPRealmFactory.createRealm(context, name, features, featureProviders, roles);
        if (getStatistics() != null) {
            r.setStatistics(getStatistics().createChild(null, "realm." + name));
        }
        if (!listeners.isEmpty()) {
            for (WAMPNodeListener l : listeners.get()) {
                try {
                    l.onCreatedRealm(r);
                } catch (Throwable th) {
                }
            }
        }
        return r;
    }

    /**
     * Returns merged set of features including default ones and provided as
     * parameters. Either set may be empty.
     *
     * @param features
     * @return
     */
    public WAMPFeature[] getNodeFeatures(WAMPFeature... features) {
        return WAMPFeature.mergeCopy(defaultFeatures, features);
    }

    /**
     * Callback for handling realm/actor init/done events. No actions by
     * default. Use to configure actors if needed.
     *
     * @param type
     * @param actor
     * @param sessions
     */
    @Override
    public void onActorEvent(WAMPActorEvent type, WAMPActor actor, WAMPSession... sessions) {
    }

    /**
     * WAMP session creator. Binds session to transport and listens for session
     * events.
     *
     * @param transport
     * @param realm
     * @param roles
     * @return
     * @throws WAMPException
     */
    public WAMPSession createSession(WAMPTransport transport, WAMPRealm realm, Role... roles) throws WAMPException {
        try {
            final WAMPSession session = new WAMPSession(realm, roles) {
                @Override
                public void onSend(WAMPMessage msg) throws WAMPException {
                    if (transport.isOpen()) {
                        transport.send(msg);
                    } else {
                        if (getCloseReason() == null) {
                            setCloseReason("node.transport.closed");
                        }
                        setState(WAMPSessionState.closed);
                    }
                }
            };
            if (realm != null && realm.getStatistics() != null) {
                session.setStatistics(realm.getStatistics().createChild(null, ""
                        + "session"
                        + "." + realm.getName()
                        + "." + ((getAgent() != null) ? getAgent() : "")
                        + "." + session.getId()
                ));
            }
            session.addWAMPSessionListener(this);

            if (authProviders != null || !realm.getAuthProviders().isEmpty()) {
                Collection<WAMPAuthProvider> aps = new ArrayList<>();
                if (realm.getAuthProviders().isEmpty()) {
                    aps.addAll(authProviders);
                } else {
                    aps.addAll(realm.getAuthProviders());
                }
                for (WAMPMessagesFlow f : session.getFlows()) {
                    if (f instanceof WAMPSessionFlow) {
                        WAMPSessionFlow wsf = (WAMPSessionFlow) f;
                        wsf.configure(aps.toArray(new WAMPAuthProvider[aps.size()]));
                    }
                }
            }

            return session;
        } catch (WAMPException wex) {
            throw wex;
        } catch (Throwable th) {
            throw new WAMPException(th);
        }
    }

    /**
     * Callback used to notify node listeners of established WAMP session.
     *
     * @param session
     */
    @Override
    public void onEstablished(WAMPSession session) {
        if (!listeners.isEmpty()) {
            for (WAMPNodeListener l : listeners.get()) {
                try {
                    l.onEstablishedSession(session);
                } catch (Throwable th) {
                    this.onListenerError(session, null, null, l, th);
                }
            }
            // fix session statistics name if has statistics
            if (session.getStatistics() != null) {
                WAMPStatistics stat = session.getStatistics();
                String n = stat.getGroupName();
                if (n != null && n.endsWith(".0")) {
                    stat.setGroupName(n.substring(0, n.length() - 1) + session.getId());
                }
            }
        }
        if (DUMP_ESTABLISH_CLOSE) {
            System.out.println("ESTABLISHED SESSION: " + session.getId() + ", '" + session.getRealm().getName() + "'\n    " + session.toString().replace("\n", "\n    "));
        }
    }

    /**
     * Callback used to notify node listeners of closed WAMP session.
     *
     * @param session
     */
    @Override
    public void onClosed(WAMPSession session) {
        if (!listeners.isEmpty()) {
            for (WAMPNodeListener l : listeners.get()) {
                try {
                    l.onClosedSession(session);
                } catch (Throwable th) {
                    this.onListenerError(session, null, null, l, th);
                }
            }
        }
        if (DUMP_ESTABLISH_CLOSE) {
            System.out.println("CLOSED      SESSION: " + session.getId() + ", '" + session.getRealm().getName() + "', reason=" + session.getCloseReason() + "\n    " + session.toString().replace("\n", "\n    "));
        }
    }

    /**
     * Callback used to notify node listeners of send WAMP message or send
     * error.
     *
     * @param session
     * @param message
     * @param error
     */
    @Override
    public void onSent(WAMPSession session, WAMPMessage message, Throwable error) {
        if (!listeners.isEmpty()) {
            for (WAMPNodeListener l : listeners.get()) {
                try {
                    l.onSent(session, message, error);
                } catch (Throwable th) {
                    this.onListenerError(session, message, null, l, th);
                }
            }
        }
    }

    /**
     * Callback used to notify node listeners of handled WAMP message and
     * related flow handler.
     *
     * @param session
     * @param msg
     * @param mf
     */
    public void onHandled(WAMPSession session, WAMPMessage msg, WAMPMessagesFlow mf) {
        if (!listeners.isEmpty()) {
            for (WAMPNodeListener l : listeners.get()) {
                try {
                    l.onHandled(session, msg, mf);
                } catch (Throwable th) {
                    this.onListenerError(session, msg, mf, l, th);
                }
            }
        }
    }

    /**
     * Callback used to notify node listeners of failed WAMP message and related
     * flow handler.
     *
     * @param session
     * @param msg
     * @param mf
     */
    public void onFailed(WAMPSession session, WAMPMessage msg, WAMPMessagesFlow mf) {
        if (!listeners.isEmpty()) {
            for (WAMPNodeListener l : listeners.get()) {
                try {
                    l.onFailed(session, msg, mf);
                } catch (Throwable th) {
                    this.onListenerError(session, msg, mf, l, th);
                }
            }
        }
    }

    /**
     * Callback used to notify node listeners of WAMP message that caused fatal
     * error.
     *
     * @param session
     * @param msg
     */
    public void onFatal(WAMPSession session, WAMPMessage msg) {
        if (!listeners.isEmpty()) {
            for (WAMPNodeListener l : listeners.get()) {
                try {
                    l.onFatal(session, msg);
                } catch (Throwable th) {
                    this.onListenerError(session, msg, null, l, th);
                }
            }
        }
    }

    /**
     * Diagnostic callback to detect failing WAMP node event listener(s).
     *
     * @param session
     * @param msg
     * @param mf
     * @param l
     * @param th
     * @param params
     */
    public void onListenerError(WAMPSession session, WAMPMessage msg, WAMPMessagesFlow mf, WAMPNodeListener l, Throwable th, Object... params) {
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
     * @return the realmFactory
     */
    public WAMPRealmFactory getRealmFactory() {
        return realmFactory;
    }

    /**
     * @param actorFactory the actorFactory to set
     */
    public <T extends WAMPNode> T realmFactory(WAMPRealmFactory realmFactory) {
        this.realmFactory = realmFactory;
        return (T) this;
    }

    /**
     * WAMP node event listener used to monitor realm/session/message events.
     */
    public static interface WAMPNodeListener {

        void onCreatedRealm(WAMPRealm realm);

        void onEstablishedSession(WAMPSession session);

        void onClosedSession(WAMPSession session);

        void onHandled(WAMPSession session, WAMPMessage msg, WAMPMessagesFlow mf);

        void onFailed(WAMPSession session, WAMPMessage msg, WAMPMessagesFlow mf);

        void onFatal(WAMPSession session, WAMPMessage msg);

        void onSent(WAMPSession session, WAMPMessage msg, Throwable error);
    }

    /**
     * Debug helper: shows compact/Extended info of session lifecycle and
     * messages processing.
     */
    public static class WAMPNodeListenerDebug implements WAMPNodeListener {

        String prefix = "";
        boolean compact = true;
        WAMPEventFilter filter;

        public WAMPNodeListenerDebug() {
        }

        public WAMPNodeListenerDebug(String prefix) {
            this.prefix = prefix;
        }

        public WAMPNodeListenerDebug filter(WAMPEventFilter filter) {
            this.filter = filter;
            return this;
        }

        public WAMPNodeListenerDebug prefix(String prefix) {
            this.prefix = (prefix != null) ? prefix : "";
            return this;
        }

        public WAMPNodeListenerDebug compact(boolean compact) {
            this.compact = compact;
            return this;
        }

        PrintStream getWriter(WAMPSession session, Object... params) {
            return (filter != null) ? filter.writer(session, params) : System.out;
        }

        @Override
        public void onCreatedRealm(WAMPRealm realm) {
            PrintStream out = getWriter(null, realm);
            if (out != null) {
                out.println("[" + System.currentTimeMillis() + "]" + prefix + "CREATED_REALM[" + realm.getName() + "]: " + ((compact)
                        ? ((realm.getStatistics() != null) ? realm.getStatistics().dumpStatistics(false) : "")
                        : "\n    " + realm.toString().replace("\n", "\n    "))
                );
            }
        }

        @Override
        public void onEstablishedSession(WAMPSession session) {
            if (filter == null || filter.handleEvent(session, null, null, null)) {
                PrintStream out = getWriter(session);
                if (out != null) {
                    out.println("[" + System.currentTimeMillis() + "]" + prefix + "ESTABLISHED[" + session.getId() + "]: " + ((compact) ? "" + session.getState() + "/" + session.getRealm().getName() : session.toString().replace("\n", "\n    ")));
                }
            }
        }

        @Override
        public void onClosedSession(WAMPSession session) {
            if (filter == null || filter.handleEvent(session, null, null, null)) {
                PrintStream out = getWriter(session);
                if (out != null) {
                    out.println("[" + System.currentTimeMillis() + "]" + prefix + "CLOSED[" + session.getId() + "]: " + ((compact) ? "" + session.getState() + "/" + session.getRealm().getName() : session.toString().replace("\n", "\n    ")));
                }
            }
        }

        @Override
        public void onHandled(WAMPSession session, WAMPMessage msg, WAMPMessagesFlow mf) {
            if (filter == null || filter.handleEvent(session, msg, mf, null)) {
                PrintStream out = getWriter(session, msg, mf);
                if (out != null) {
                    out.println("[" + System.currentTimeMillis() + "]" + prefix + "onHandled[" + session.getId() + "]: by " + mf.getClass().getName()
                            + ((compact) ? ": " + msg.getType().getName() : "\n    " + msg.toString().replace("\n", "\n    "))
                            + ((compact) ? ": " + msg.toList() : "\n    " + msg.toList().toString().replace("\n", "\n    "))
                    );
                }
            }
        }

        @Override
        public void onFailed(WAMPSession session, WAMPMessage msg, WAMPMessagesFlow mf) {
            if (filter == null || filter.handleEvent(session, msg, mf, null)) {
                PrintStream out = getWriter(session, msg, mf);
                if (out != null) {
                    out.println("[" + System.currentTimeMillis() + "]" + prefix + "onFailed[" + session.getId() + "]: by " + mf
                            + ((compact) ? ": " + session.getState() + "/" + session.getRealm().getName() : "\n    " + session.toString().replace("\n", "\n    "))
                            + ((compact) ? ": " + msg.getType().getName() : "\n    " + msg.toString().replace("\n", "\n    "))
                            + ((compact) ? ": " + msg.toList() : "\n    " + msg.toList().toString().replace("\n", "\n    "))
                    );
                }
            }
        }

        @Override
        public void onFatal(WAMPSession session, WAMPMessage msg) {
            if (filter == null || filter.handleEvent(session, msg, null, null)) {
                PrintStream out = getWriter(session, msg);
                if (out != null) {
                    out.println("[" + System.currentTimeMillis() + "]" + prefix + "onFatal[" + session.getId() + "]: " + msg.toString().replace("\n", "\n    ")
                            + "\n    " + msg.toList().toString().replace("\n", "\n    ")
                    );
                }
            }
        }

        @Override
        public void onSent(WAMPSession session, WAMPMessage msg, Throwable error) {
            if (filter == null || filter.handleEvent(session, msg, null, error)) {
                PrintStream out = getWriter(session, msg, error);
                if (out != null) {
                    if (error != null) {
                        String err = "" + error;
                        if (error != null) {
                            try {
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                error.printStackTrace(new PrintStream(baos));
                                baos.close();
                                err = baos.toString();
                            } catch (Throwable th) {
                            }
                        }
                        System.out.println("[" + System.currentTimeMillis() + "]" + prefix + "onSendError[" + session.getId() + "]: " + ((error != null) ? "ERROR: " + err + "\n    " : "") + msg.toString().replace("\n", "\n    ")
                                + ((compact) ? ": " + msg.toList() : "\n    " + msg.toList().toString().replace("\n", "\n    "))
                        );
                    } else {
                        System.out.println(prefix + "onSent[" + session.getId() + "]: " + msg.toString().replace("\n", "\n    ")
                                + ((compact) ? ": " + msg.toList() : "\n    " + msg.toList().toString().replace("\n", "\n    "))
                        );
                    }
                }
            }
        }

        /**
         * Flexible debug messages filtering enabler.
         */
        public static interface WAMPEventFilter {

            default PrintStream writer(WAMPSession session, Object... params) {
                return System.out;
            }

            boolean handleEvent(WAMPSession session, WAMPMessage msg, WAMPMessagesFlow mf, Throwable error);
        }
    }

}
