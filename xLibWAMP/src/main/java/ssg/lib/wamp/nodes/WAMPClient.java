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
import java.util.ArrayList;
import java.util.Arrays;
import ssg.lib.wamp.events.WAMPEventListener;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.WAMPActor;
import ssg.lib.wamp.WAMPConstantsBase;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPRealm;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.WAMPSessionState;
import ssg.lib.wamp.WAMPTransport;
import ssg.lib.wamp.WAMPTransport.WAMPTransportWrapper;
import ssg.lib.wamp.auth.WAMPAuthProvider;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_ID;
import static ssg.lib.wamp.auth.WAMPAuthProvider.K_AUTH_METHODS;
import ssg.lib.wamp.events.WAMPPublisher;
import ssg.lib.wamp.flows.WAMPMessagesFlow;
import ssg.lib.wamp.flows.WAMPMessagesFlow.WAMPFlowStatus;
import static ssg.lib.wamp.flows.WAMPMessagesFlow.WAMPFlowStatus.failed;
import static ssg.lib.wamp.flows.WAMPMessagesFlow.WAMPFlowStatus.handled;
import static ssg.lib.wamp.flows.WAMPMessagesFlow.WAMPFlowStatus.ignored;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMP_DT;
import ssg.lib.wamp.rpc.impl.callee.CalleeProcedure.Callee;
import ssg.lib.wamp.rpc.impl.callee.WAMPRPCCallee;
import ssg.lib.wamp.rpc.impl.caller.WAMPRPCCaller;
import ssg.lib.wamp.events.WAMPSubscriber;
import ssg.lib.wamp.flows.WAMPSessionFlow;
import ssg.lib.wamp.rpc.impl.WAMPRPCListener;
import ssg.lib.wamp.rpc.impl.WAMPRPCListener.CALL_STATE;
import ssg.lib.wamp.rpc.impl.WAMPRPCListener.WAMPRPCListenerWrapper;
import ssg.lib.wamp.rpc.impl.caller.CallerCall.CallListener;
import ssg.lib.wamp.rpc.impl.caller.CallerCall.SimpleCallListener;
import ssg.lib.wamp.util.LS;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author 000ssg
 */
public class WAMPClient extends WAMPNode {

    WAMPTransport transport;
    WAMPSession session;

    Collection<WAMPEventListener> eventListeners = WAMPTools.createSynchronizedList();
    //Collection<WAMPRPCListener> rpcListeners = WAMPTools.createSynchronizedList();
    LS<WAMPRPCListener> rpcListeners = new LS<>(new WAMPRPCListener[0]);
    List<Runnable> pendingActions = WAMPTools.createSynchronizedList();
    Map<String, Object> properties = WAMPTools.createDict(null);
    private int maxPendingMessagesQueue = 100;
    private long maxWaitTimeForSynchronousCall = 1000 * 15;
    private Object connectionContext;

    public WAMPClient() {
    }

    /**
     * Test if connection is configured.
     *
     * @return
     */
    public boolean isConfigured() {
        return session != null;
    }

    /**
     * Configure optional connection context used to initialize WAMP realm.
     *
     * @param <T>
     * @param context
     * @return
     */
    public <T extends WAMPClient> T configureContext(Object context) {
        this.connectionContext = context;
        return (T) this;
    }

    public <T extends WAMPClient> T configure(WAMPTransport transport, String agent, String realmS, Role... roles) throws WAMPException {
        if (session != null) {
            throw new WAMPException("Cannot change client configuration.");
        }
        this.transport = (transport != null) ? transport : new WAMPTransportWrapper(transport);
        setAgent(agent);
        WAMPRealm realm = createRealm(connectionContext, realmS, getNodeFeatures(), roles);
        session = createSession(WAMPClient.this.transport, realm, roles);
        session.getLocal().setAgent(getAgent());
        session.addWAMPSessionListener(this);
        return (T) this;
    }

    public <T extends WAMPClient> T configure(WAMPTransport transport, WAMPFeature[] features, String agent, String realmS, Role... roles) throws WAMPException {
        if (session != null) {
            throw new WAMPException("Cannot change client configuration.");
        }
        this.transport = (transport != null) ? transport : new WAMPTransportWrapper(transport);
        setAgent(agent);
        WAMPRealm realm = createRealm(connectionContext, realmS, getNodeFeatures(features), roles);
        session = createSession(WAMPClient.this.transport, realm, roles);
        session.getLocal().setAgent(getAgent());
        session.addWAMPSessionListener(this);
        return (T) this;
    }

    public <T extends WAMPClient> T configure(WAMPAuthProvider authProviders) {
        return (T) super.configure(authProviders);
    }

    public <T extends WAMPClient> T configure(WAMPTransport transport, String agent, WAMPRealm realm, Role... roles) throws WAMPException {
        if (session != null) {
            throw new WAMPException("Cannot change client configuration.");
        }
        this.transport = (transport != null) ? transport : new WAMPTransportWrapper(transport);
        setAgent(agent);
        session = new WAMPSession(realm, roles) {
            @Override
            public void onSend(WAMPMessage msg) throws WAMPException {
                if (WAMPClient.this.transport != null && WAMPClient.this.transport.isOpen()) {
                    WAMPClient.this.transport.send(msg);
                } else {
                    if (session.getCloseReason() == null) {
                        session.setCloseReason("client.transport." + ((WAMPClient.this.transport == null) ? "none" : "closed"));
                    }
                    setState(WAMPSessionState.closed);
                }
            }
        };
        session.getLocal().setAgent(getAgent());
        session.addWAMPSessionListener(this);
        return (T) this;
    }

    public synchronized void setTransport(WAMPTransport transport) throws WAMPException {
        if (this.transport instanceof WAMPTransportWrapper && ((WAMPTransportWrapper) this.transport).getBase() == null && transport != null && WAMPSessionState.open == session.getState()) {
            ((WAMPTransportWrapper) this.transport).setBase(transport);
            this.transport.setStatistics(getStatistics() != null ? getStatistics().createChildMessageStatistics("transport") : null);
        } else {
            throw new WAMPException("Cannot change already assigned valid WAMP transport: now=" + this.transport + ", tried: " + transport);
        }
    }

    public boolean hasRole(Role role) {
        return session.hasLocalRole(role);
    }

    public String getRealm() {
        return session.getRealm().getName();
    }

    public boolean isSessionEstablished() {
        return session != null && WAMPSessionState.established == session.getState();
    }

    /**
     * Utility method to wait until session is established (1ms granularity) for
     * up to timeout ms (defaults to 1000 if null or <=0).
     *
     * @param timeout
     * @return >=0 - established (in ms), -1 - no timeout, -2 - thread interrupted, -3 - timed out
     */
    public long waitEstablished(Long timeout) {
        long r=System.currentTimeMillis();
        if (timeout == null || timeout <= 0) {
            timeout = 1000L;
        }
        if (isConnected()) {
            timeout = System.currentTimeMillis() + timeout;
            while (!isSessionEstablished()) {
                if (System.currentTimeMillis() >= timeout) {
                    return -1;
                }
                try {
                    Thread.sleep(1);
                } catch (Throwable th) {
                    return -2;
                }
            }
            return System.currentTimeMillis()-r;
        }
        return -3;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public boolean supportsFeature(WAMPFeature feature) {
        return session != null && session.supportsFeature(feature);
    }

    @Override
    public void onEstablished(WAMPSession session) {
        super.onEstablished(session);
        session.getRealm().init();
        if (session.getRealm().getActor(WAMP.Role.subscriber) instanceof WAMPSubscriber) {
            if (!eventListeners.isEmpty()) {
                for (WAMPEventListener l : eventListeners) {
                    try {
                        ((WAMPSubscriber) session.getRealm().getActor(WAMP.Role.subscriber)).subscribe(session, l);
                    } catch (WAMPException wex) {
                        wex.printStackTrace();
                    }
                }
            }
        }
        if (session.getRealm().getActor(WAMP.Role.caller) instanceof WAMPRPCCaller) {
            if (!rpcListeners.isEmpty()) {
                for (WAMPRPCListener l : rpcListeners.get()) {
                    tryCall(l);
                }
            }
        }
        while (!pendingActions.isEmpty()) {
            Runnable action = pendingActions.remove(0);
            if (action != null) {
                action.run();
            }
        }
    }

    @Override
    public void onClosed(WAMPSession session) {
        super.onClosed(session);
        if (!eventListeners.isEmpty()) {
            for (WAMPEventListener l : eventListeners) {
                // invalidate subscription Id
                if (WAMP_DT.id.validate(l.getSubscriptionId())) {
                    l.onSubscribed(0);
                }
            }
        }
        if (!rpcListeners.isEmpty()) {
            for (WAMPRPCListener l : rpcListeners.get()) {
                // invalidate call
                if (l != null) {
                    l.onCancel(0, WAMPConstantsBase.INFO_CloseRealm);
                }
            }
        }
        transport.close();
    }

    public void addWAMPEventListener(WAMPEventListener l) throws WAMPException {
        synchronized (eventListeners) {
            if (session != null && session.getState() == WAMPSessionState.established) {
                if (session.getRealm().getActor(WAMP.Role.subscriber) instanceof WAMPSubscriber) {
                    ((WAMPSubscriber) session.getRealm().getActor(WAMP.Role.subscriber)).subscribe(session, l);
                    eventListeners.add(l);
                }
            } else {
                eventListeners.add(l);
            }
        }
    }

    public void removeWAMPEventListener(WAMPEventListener l) throws WAMPException {
        synchronized (eventListeners) {
            if (eventListeners.contains(l)) {
                eventListeners.remove(l);
                if (session.getRealm().getActor(WAMP.Role.subscriber) instanceof WAMPSubscriber) {
                    if (WAMP_DT.id.validate(l.getSubscriptionId())) {
                        // unsubscribe only if have valid subscription id
                        ((WAMPSubscriber) session.getRealm().getActor(WAMP.Role.subscriber)).unsubscribe(session, l.getSubscriptionId());
                    }
                }
            }
        }
    }

    public void removeWAMPEventListener(String topic) throws WAMPException {
        WAMPEventListener[] wel = null;
        for (WAMPEventListener l : eventListeners) {
            if (topic.equals(l.getTopic())) {
                if (wel == null) {
                    wel = new WAMPEventListener[]{l};
                } else {
                    wel = Arrays.copyOf(wel, wel.length + 1);
                    wel[wel.length - 1] = l;
                }
            }
        }
        if (wel != null) {
            for (WAMPEventListener l : wel) {
                removeWAMPEventListener(l);
            }
        }
    }

    public void addWAMPRPCListener(WAMPRPCListener l) throws WAMPException {
        if (l != null) {
            synchronized (rpcListeners) {
                rpcListeners.add(l);
            }
        }
    }

    public void removeWAMPRPCListener(WAMPRPCListener l) throws WAMPException {
        if (l != null) {
            rpcListeners.add(l);
        }
    }

    public boolean isConnected() {
        return session != null
                && (WAMPSessionState.open == session.getState()
                || WAMPSessionState.established == session.getState()
                || WAMPSessionState.closing == session.getState());
    }

    public void connect() throws WAMPException {
        connect(null);
    }

    public void connect(String authid) throws WAMPException {
        if (session != null && WAMPSessionState.open == session.getState()) {
            Map<String, Object> details = WAMPTools.createDict(null);
            Map<String, Map> roles = WAMPTools.createMap(true);
            details.put("roles", roles);
            if (session.getLocal().getAgent() != null) {
                details.put("agent", session.getLocal().getAgent());
            }
            Map<Role, Map<String, Object>> localRoles = session.getLocal().getRoles();
            for (Role r : localRoles.keySet()) {
                roles.put("" + r, localRoles.get(r));
                Collection<WAMPFeature> fs = session.getLocal().features();
                if (fs != null && !fs.isEmpty()) {
                    Map<String, Object> lfs = WAMPTools.createDict(null);
                    for (WAMPFeature f : fs) {
                        if (Role.hasRole(r, f.scope())) {
                            lfs.put(f.uri(), true);
                        }
                    }
                    if (!lfs.isEmpty()) {
                        roles.get("" + r).put("features", lfs);
                    }
                }
            }
            if (authid != null) {
                WAMPSessionFlow wsf = session.getFlow(WAMPSessionFlow.class);
                List<String> auths = (wsf != null) ? wsf.getAuthMethods() : null;
                if (auths != null && !auths.isEmpty()) {
                    details.put(K_AUTH_METHODS, auths);
                    details.put(K_AUTH_ID, authid);
                }
            }
            session.send(WAMPMessage.hello(session.getRealm().getName(), details));
        }
    }

    public void disconnect(String reason) throws WAMPException {
        if (session != null && WAMPSessionState.established == session.getState()) {
            session.send(WAMPMessage.goodbye(WAMPTools.EMPTY_DICT, reason));
        }
    }

    private volatile boolean runCycleWIP = false;
    Map<WAMPMessage, WAMPFlowStatus> tempStat = WAMPTools.createSynchronizedMap();

    public void runCycle() throws WAMPException {
        if (runCycleWIP) {
            return;
        }
        runCycleWIP = true;
        try {
            synchronized (session) {
                WAMPMessage msg = null;
                msg = transport.receive();

                if (msg != null) {
                    try {
                        boolean done = false;
                        for (WAMPMessagesFlow mf : session.getFlows()) {
                            if (mf.canHandle(session, msg)) {
                                switch (mf.handle(session, msg)) {
                                    case handled:
                                        onHandled(session, msg, mf);
                                        tempStat.put(msg, WAMPFlowStatus.handled);
                                        done = true;
                                        break;
                                    case busy:
                                        transport.unreceive(msg);
                                        tempStat.put(msg, WAMPFlowStatus.busy);
                                        done = true;
                                        break;
                                    case ignored:
                                        break;
                                    case failed:
                                        onFailed(session, msg, mf);
                                        tempStat.put(msg, WAMPFlowStatus.failed);
                                        done = true;
                                    //throw new WAMPException("Invalid/unhandled ("+mf.getClass().getSimpleName()+") message: " + msg+": "+msg.toList());
                                }
                                if (done) {
                                    break;
                                }
                            }
                        }
                        if (!done) {
                            onFatal(session, msg);
                            throw new WAMPException("Unhandled message: " + msg + "\n  data: " + msg.toList().toString().replace("\n", "\n  "));
                        }
                    } catch (WAMPException wex) {
                        wex.printStackTrace();
                        session.send(WAMPMessage.abort(WAMPTools.createDict("message", msg.toList(), "error", WAMPTools.getStackTrace(wex)),
                                WAMPConstantsBase.ERROR_ProtocolViolation));
                    }
                }
            }

            if (WAMPSessionState.established == session.getState()) {
                if (session.getRealm().getActor(WAMP.Role.caller) instanceof WAMPRPCCaller) {
                    if (!rpcListeners.isEmpty() && (getMaxPendingMessagesQueue() <= 0 || session.getPendingCount() < getMaxPendingMessagesQueue())) {
                        WAMPRPCListener[] ls = rpcListeners.get();
                        if (ls != null) {
                            for (WAMPRPCListener l : ls) {
                                if (l == null) {
                                    continue;
                                }
                                long callId = l.getCallId();
                                if (callId <= 0) {
                                    synchronized (l) {
                                        callId = tryCall(l);
                                    }
                                }
                                if (callId <= 0) {
                                    int a = 0;
                                }
                                if (getMaxPendingMessagesQueue() > 0 && session.getPendingCount() > getMaxPendingMessagesQueue()) {
                                    break;
                                }
                            }
                        }
                    }
                }

                try {
                    while (!pendingActions.isEmpty() && (getMaxPendingMessagesQueue() <= 0 || session.getPendingCount() < getMaxPendingMessagesQueue())) {
                        Runnable action = pendingActions.remove(0);
                        if (action != null) {
                            action.run();
                        }
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        } finally {
            runCycleWIP = false;
        }
    }

    public synchronized long tryCall(WAMPRPCListener l) {
        try {
            if (l != null && l.getCallId() <= 0) {
                l.onCall(((WAMPRPCCaller) session.getRealm().getActor(WAMP.Role.caller)).call(
                        session,
                        l.getOptions(),
                        l.getProcedure(),
                        l.getArgs(),
                        l.getArgsKw(),
                        new WAMPRPCListenerWrapper(l) {
                    @Override
                    public void onError(long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                        synchronized (rpcListeners) {
                            rpcListeners.remove(getBase());
                        }
                        super.onError(callId, error, details, args, argsKw);
                        setState(CALL_STATE.failed);
                    }

                    @Override
                    public boolean onResult(long callId, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                        boolean r = super.onResult(callId, details, args, argsKw);
                        if (r) {
                            synchronized (rpcListeners) {
                                rpcListeners.remove(getBase());
                            }
                            setState(CALL_STATE.completed);
                        }
                        return r;
                    }

                    @Override
                    public void onCancel(long callId, String reason) {
                        synchronized (rpcListeners) {
                            rpcListeners.remove(getBase());
                        }
                        super.onCancel(callId, reason);
                        setState(CALL_STATE.cancelled);
                    }
                }));
                return l.getCallId();
            }
        } catch (WAMPException wex) {
            wex.printStackTrace();
        }
        return 0L;
    }

    public boolean canPublish() {
        return hasRole(Role.publisher) && WAMPSessionState.established == session.getState() && session.getRealm().getActor(WAMP.Role.publisher) instanceof WAMPPublisher;
    }

    public boolean publish(Map<String, Object> options, String topic, List arguments, Map<String, Object> argumentsKw) throws WAMPException {
        if (canPublish()) {
            ((WAMPPublisher) session.getRealm().getActor(WAMP.Role.publisher)).publish(session, options, topic, arguments, argumentsKw);
            return true;
        }
        return false;
    }

    public boolean canExecute() {
        return hasRole(Role.callee) && WAMPSessionState.established == session.getState() && session.getRealm().getActor(WAMP.Role.callee) instanceof WAMPRPCCallee;
    }

    public boolean addExecutor(final Map<String, Object> options, final String procedure, final Callee callee) throws WAMPException {
        if (canExecute()) {
            ((WAMPRPCCallee) session.getRealm().getActor(WAMP.Role.callee)).register(
                    session,
                    options,
                    procedure,
                    callee);
            return true;
        } else if (hasRole(Role.callee)) {
            pendingActions.add(new Runnable() {
                @Override
                public void run() {
                    try {
                        ((WAMPRPCCallee) session.getRealm().getActor(WAMP.Role.callee)).register(
                                session,
                                options,
                                procedure,
                                callee);
                    } catch (WAMPException wex) {
                        wex.printStackTrace();
                        //onError(wex);
                    }
                }
            });
        }
        return false;
    }

    public boolean removeExecutor(String procedure) throws WAMPException {
        if (canExecute()) {
            WAMPRPCCallee rpc = (WAMPRPCCallee) session.getRealm().getActor(WAMP.Role.callee);
            Long id = rpc.getProcedureRegistrationId(procedure);
            if (WAMP_DT.id.validate(id)) {
                rpc.unregister(session, id);
            }
        }
        return false;
    }

    public boolean hasRunningExecutors() {
        return !rpcListeners.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append("{");
        sb.append("agent=" + getAgent());
        sb.append(", eventListeners=" + eventListeners.size());
        sb.append("\n  transport=" + transport);
        sb.append("\n  session=" + session.toString().replace("\n", "\n  "));
        sb.append("\n    roles=" + session.getLocal().getRoles().keySet());
        for (WAMP.Role role : session.getLocal().getRoles().keySet()) {
            WAMPActor actor = session.getRealm().getActor(role);
            sb.append("\n      " + role + "=");
            if (actor != null) {
                sb.append(actor.toString().replace("\n", "\n      "));
            }
        }
        Collection<WAMPMessage> lost = getLostMessages();
        if (!lost.isEmpty()) {
            sb.append("\n  Lost messages[" + lost.size() + "]");
            for (WAMPMessage msg : lost) {
                sb.append("\n    " + ("" + ((msg != null) ? msg.toList() : " <none>") + ((msg != null) ? "  " + tempStat.get(msg) : "")).replace("\n", "\\n"));
            }
            sb.append('\n');
        }
        sb.append('}');
        return sb.toString();
    }

    public Collection<WAMPMessage> getLostMessages() {
        List<WAMPMessage> r = new ArrayList<>();
        synchronized (tempStat) {
            for (Entry<WAMPMessage, WAMPFlowStatus> entry : tempStat.entrySet()) {
                if (entry.getValue() == null || WAMPFlowStatus.busy == entry.getValue()) {
                    r.add(entry.getKey());
                }
            }
        }
        return r;
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

    /**
     * Synchronous call. Actually performs asynchronous call waiting for result
     * up to
     *
     * @param <T>
     * @param procedure
     * @param args
     * @param argsKw
     * @return
     * @throws WAMPException
     */
    public <T> T call(String procedure, List args, Map<String, Object> argsKw) throws WAMPException {
        final boolean[] done = new boolean[]{false};
        final Object[] result = new Object[2];
        addWAMPRPCListener(
                new WAMPRPCListener.WAMPRPCListenerBase(WAMPTools.EMPTY_DICT, procedure, args, argsKw) {
            @Override
            public void onCall(long callId) {
                super.onCall(callId);
            }

            @Override
            public void onCancel(long callId, String reason) {
                result[1] = reason;
                done[0] = true;
            }

            @Override
            public boolean onResult(long callId, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                result[0] = (argsKw != null) ? argsKw : args;
                done[0] = true;
                return true;
            }

            @Override
            public void onError(long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                result[0] = (argsKw != null) ? argsKw : args;
                result[1] = error;
                done[0] = true;
            }
        });

        long timeout = System.currentTimeMillis() + getMaxWaitTimeForSynchronousCall();
        while (System.currentTimeMillis() < timeout) {
            if (done[0]) {
                break;
            }
            try {
                Thread.sleep(5);
            } catch (Throwable th) {
                break;
            }
        }

        if (result[1] != null) {
            throw new WAMPException((String) result[1]);
        }

        return (T) result[0];
    }

    /**
     * @return the maxWaitTimeForSynchronousCall
     */
    public long getMaxWaitTimeForSynchronousCall() {
        return maxWaitTimeForSynchronousCall;
    }

    /**
     * @param maxWaitTimeForSynchronousCall the maxWaitTimeForSynchronousCall to
     * set
     */
    public void setMaxWaitTimeForSynchronousCall(long maxWaitTimeForSynchronousCall) {
        this.maxWaitTimeForSynchronousCall = maxWaitTimeForSynchronousCall;
    }

    /**
     * Asynchronous call registration.
     *
     * @param procedure
     * @param args
     * @param argsKw
     * @return true if call is registered
     * @throws WAMPException
     */
    public boolean call(String procedure, List args, Map<String, Object> argsKw, final CallListener caller) throws WAMPException {
        if (procedure != null && caller != null) {
            addWAMPRPCListener(
                    new WAMPRPCListener.WAMPRPCListenerBase(WAMPTools.EMPTY_DICT, procedure, args, argsKw) {
                @Override
                public void onCall(long callId) {
                    super.onCall(callId);
                }

                @Override
                public void onCancel(long callId, String reason) {
                    caller.onError(callId, reason, WAMPTools.EMPTY_DICT, null, null);
                }

                @Override
                public boolean onResult(long callId, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                    caller.onResult(callId, details, args, argsKw);
                    return true;
                }

                @Override
                public void onError(long callId, String error, Map<String, Object> details, List args, Map<String, Object> argsKw) {
                    caller.onError(callId, error, details, args, argsKw);
                }
            });
            return true;
        }
        return false;
    }

    public boolean call2(String procedure, List args, Map<String, Object> argsKw, final SimpleCallListener caller) throws WAMPException {
        return this.call(procedure, args, argsKw, (CallListener) caller);
    }

}
