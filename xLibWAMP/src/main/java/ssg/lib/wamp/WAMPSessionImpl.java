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
package ssg.lib.wamp;

import ssg.lib.wamp.util.WAMPException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.auth.WAMPAuth;
import ssg.lib.wamp.events.WAMPBroker;
import ssg.lib.wamp.events.impl.WAMPSubscription;
import ssg.lib.wamp.features.WAMP_FP_SessionMetaAPI;
import static ssg.lib.wamp.features.WAMP_FP_SessionMetaAPI.SM_EVENT_ON_LEAVE;
import static ssg.lib.wamp.features.WAMP_FP_SessionMetaAPI.SM_RPC_SESSION_GET;
import ssg.lib.wamp.flows.WAMPMessagesFlow;
import ssg.lib.wamp.flows.WAMPPublishingFlow;
import ssg.lib.wamp.flows.WAMPRPCFlow;
import ssg.lib.wamp.flows.WAMPSessionFlow;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMPMessageType;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_CALLER_ID_KEY;
import ssg.lib.wamp.rpc.impl.caller.CallerCall.SimpleCallListener;
import ssg.lib.wamp.rpc.impl.caller.WAMPRPCCaller;
import ssg.lib.wamp.util.LS;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.wamp.stat.WAMPStatistics;

/**
 * WAMP session holds all realm/connection bound info, including id, realm,
 * roles, call ids. Runtime info (like subscriptions, calls) is managed within
 * appropriate realm's WAMP actors per session. Messages that require response
 * are stored as "pending" and are accessible/cleared with "getPending" method.
 *
 * Session binds Local and Remote parties both represented with WAMPParty
 * object. Local is pre-constructed while remote is build on request (router
 * side) or response (client side).
 *
 * Session provides "send" method to send data to other party. Messages are read
 * and processed by WAMPNode implementation using session data (realm, IDs,
 * etc.). Actual data transfer (sending) must be implemented in "onSend" method.
 *
 * WAMP session fires onEstablished and onClosed events for registered session
 * listeners (e.g. for WAMP node).
 *
 * Session provides method to actualize set of features ("adjustFeatures")
 * before being established (to announce supported ones), and after established
 * (to reduce features to set supported both on client and router sides).
 *
 * Session stores session-related stuff that is not maintained but is just
 * available via sessions, like message statistics.
 *
 * @author 000ssg
 */
public abstract class WAMPSessionImpl implements WAMPSession {

    public static final WAMPSession NO_SESSION = new WAMPSessionImpl() {
        @Override
        public void doSend(WAMPMessage msg) throws WAMPException {
            throw new UnsupportedOperationException("NO SESSION");
        }
    };

    private AtomicLong nextID = new AtomicLong(1);

    WAMPRealm realm;
    long id = 0; // no session: id MUST be > 0
    WAMPSessionState state = WAMPSessionState.open;
    WAMPParty local;
    WAMPParty remote;
    // WAMP authentication (client)
    private WAMPAuth auth;
    // remote auths
    Map<Long, WAMPAuth> sessionAuths = WAMPTools.createSynchronizedMap();

    Map<Long, WAMPMessageType> pending = WAMPTools.createSynchronizedMap(false);
    private List<WAMPMessagesFlow> flows = WAMPTools.createList();

//    List<WAMPSessionListener> listeners = WAMPTools.createSynchronizedList();
//    List<WAMPSessionExtendedListener> xListeners = WAMPTools.createSynchronizedList();
    LS<WAMPSessionListener> listeners = new LS<>(new WAMPSessionListener[0]);
    LS<WAMPSessionExtendedListener> xListeners = new LS<>(new WAMPSessionExtendedListener[0]);

    WAMPStatistics statistics;
    private String closeReason;

    private WAMPSessionImpl() {
    }

    public WAMPSessionImpl(WAMPRealm realm, Role... localRoles) throws WAMPException {
        this.realm = realm;
        local = new WAMPParty(null, localRoles);
        flows.add(new WAMPSessionFlow());
        if (hasLocalRole(Role.broker) || hasLocalRole(Role.publisher) || hasLocalRole(Role.subscriber)) {
            flows.add(new WAMPPublishingFlow());
        }
        if (hasLocalRole(Role.dealer) || hasLocalRole(Role.caller) || hasLocalRole(Role.callee)) {
            flows.add(new WAMPRPCFlow());
        }
        adjustFeatureSets(realm.featureProviders);
    }

    @Override
    public void adjustFeatureSets(Map<WAMPFeature, WAMPFeatureProvider> featureProviders) {
        // evaluate features...
        // router-only set: initial
        if (local.isRouter() && remote == null) {
            boolean isBroker = hasLocalRole(Role.broker);
            boolean isDealer = hasLocalRole(Role.dealer);
            Collection<WAMPFeature> brokerFeatures = null;
            Collection<WAMPFeature> dealerFeatures = null;
            if (isBroker && realm.getActor(Role.broker) != null) {
                brokerFeatures = realm.getActor(Role.broker).features();
                for (WAMPFeature f : brokerFeatures) {
                    if (Role.hasRole(Role.broker, f.scope)) {
                        local.features.add(f);
                    }
                }
            }
            if (isDealer && realm.getActor(Role.dealer) != null) {
                dealerFeatures = realm.getActor(Role.dealer).features();
                for (WAMPFeature f : dealerFeatures) {
                    if (Role.hasRole(Role.dealer, f.scope)) {
                        local.features.add(f);
                    }
                }
                // special case: registration_api_meta requires broker role!
                if (dealerFeatures.contains(WAMPFeature.registration_meta_api)) {
                    if (!isBroker) {
                        local.features().remove(WAMPFeature.registration_meta_api);
                    } else {
                    }
                }
            }
        } else if (!local.isRouter() && remote == null) {
            for (Role role : local.getRoles().keySet()) {
                WAMPActor actor = realm.getActor(role);
                if (actor != null && actor.features() != null) {
                    for (WAMPFeature f : actor.features()) {
                        boolean canHave = false;
                        for (Role r : local.getRoles().keySet()) {
                            if (Role.hasRole(r, f.scope)) {
                                canHave = true;
                                break;
                            }
                        }
                        if (canHave) {
                            local.features.add(f);
                        }
                    }
                }
            }
        }

        // preform final feature adjustments
        if (remote != null) {
            for (WAMPFeature lf : local.features().toArray(new WAMPFeature[local.features().size()])) {
                // check if local feature depends on presence on remote
                if (Role.hasRole(Role.client, lf.scope())) {
                    if (!remote.features().contains(lf)) {
                        local.features().remove(lf);
                    }
                }
            }
            for (WAMPFeature rf : remote.features().toArray(new WAMPFeature[remote.features().size()])) {
                // check if remote feature depends on presence on local
                if (Role.hasRole(Role.client, rf.scope())) {
                    if (!local.features().contains(rf)) {
                        remote.features().remove(rf);
                    }
                }
            }
            if (featureProviders != null && !featureProviders.isEmpty()) {
                // register feature providers (e.g. feature methods etc.)...
                for (WAMP.Role role : this.local.getRoles().keySet()) {
                    WAMPActor actor = realm.getActor(role);
                    actor.initFeatures(new WAMP.Role[]{role}, featureProviders);
                }
                for (Entry<WAMPFeature, WAMPFeatureProvider> entry : featureProviders.entrySet()) {
                    if (local.features().contains(entry.getKey())) try {
                        entry.getValue().prepareFeature(this);
                    } catch (WAMPException wex) {
                        wex.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    public WAMPRealm getRealm() {
        return realm;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    @Override
    public WAMPParty getLocal() {
        return local;
    }

    @Override
    public WAMPParty getRemote() {
        return remote;
    }

    @Override
    public void setRemote(WAMPParty remote) {
        this.remote = remote;
        adjustFeatureSets(realm.featureProviders);
    }

    @Override
    public WAMPSessionState getState() {
        return state;
    }

    @Override
    public void setState(WAMPSessionState state) {
        WAMPSessionState old = this.state;
        this.state = state;
        if (old != state) {
            switch (state) {
                case established:
                    for (WAMPSessionListener l : listeners.get()) {
                        l.onEstablished(this);
                    }
                    break;
                case closing:
                    if (hasLocalRole(Role.client)) {
                        realm.close();
                    } else if (hasLocalRole(Role.router)) {
                        realm.close(this);
                    }
                    break;
                case closed:
                    for (WAMPSessionListener l : listeners.get()) {
                        l.onClosed(this);
                    }
                    if (hasLocalRole(Role.client)) {
                        realm.close();
                    } else if (hasLocalRole(Role.router)) {
                        realm.close(this);
                    }
                    break;
            }
        }
    }

    @Override
    public boolean hasLocalRole(Role role) {
        return Role.hasRole(role, local.getRoles().keySet());
    }

    @Override
    public boolean hasRemoteRole(Role role) {
        return remote != null && Role.hasRole(role, remote.getRoles().keySet());
    }

    @Override
    public boolean supportsFeature(WAMPFeature feature) {
        return feature != null
                && local != null
                && remote != null
                && local.features().contains(feature)
                && remote.features().contains(feature);
    }

    @Override
    public long getNextRequestId() {
        return nextID.getAndIncrement();
    }

    @Override
    public void send(WAMPMessage message) throws WAMPException {
        if (message != null && WAMPMessageType.isRequestMessageType(message.getType())) {
            pending.put(message.getId(0), message.getType());
        }
        if (WAMPMessageType.T_GOODBYE == message.getType().getId()) {
            if (WAMPSessionState.established == getState()) {
                if (getCloseReason() == null) {
                    setCloseReason(message.getUri(1));
                }
                setState(WAMPSessionState.closing);
            }
        }
        //System.out.println("WAMPSession["+this.getId()+"]:send:"+message.toString().replace("\n", "\n  "));
        Throwable error = null;
        try {
            doSend(message);
        } catch (WAMPException wex) {
            error = wex;
            throw wex;
        } catch (Throwable th) {
            error = th;
            throw new WAMPException("WAMPMessage send error", th);
        } finally {
            for (WAMPSessionExtendedListener l : xListeners.get()) {
                l.onSent(this, message, error);
            }
        }
    }

    @Override
    public int getPendingCount() {
        return pending.size();
    }

    @Override
    public WAMPMessageType getPending(long request, boolean reset) {
        return (reset) ? pending.remove(request) : pending.get(request);
    }

    @Override
    public void addWAMPSessionListener(WAMPSessionListener l) {
        listeners.add(l);
        if (l instanceof WAMPSessionExtendedListener) {
            xListeners.add((WAMPSessionExtendedListener) l);
        }
    }

    @Override
    public void removeWAMPSessionListener(WAMPSessionListener l) {
        listeners.remove(l);
        if (l instanceof WAMPSessionExtendedListener) {
            xListeners.remove((WAMPSessionExtendedListener) l);
        }
    }

    @Override
    public void close() throws WAMPException {
        setState(WAMPSessionState.closed);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append("{");
        sb.append("realm=" + (realm != null ? realm.getName() : "<no realm>"));
        sb.append(", id=" + id);
        sb.append(", state=" + state);
        if (getCloseReason() != null) {
            sb.append(", closed=" + getCloseReason());
        }
        sb.append(", pending=" + pending.size());
        sb.append(", flows=" + getFlows().size());
        sb.append(", listeners/x=" + listeners.get().length + "/" + xListeners.get().length);
        if (local != null) {
            sb.append("\n  local=" + local.toString().replace("\n", "\n  "));
        }
        if (remote != null) {
            sb.append("\n  remote=" + remote.toString().replace("\n", "\n  "));
        }
        if (local != null || remote != null) {
            if (getAuth() != null) {
                sb.append("\n  auth=" + getAuth().toString().replace("\n", "\n  "));
            }
            if (!sessionAuths.isEmpty()) {
                sb.append("\n  remoteAuths[" + sessionAuths.size() + "]=" + sessionAuths);
            }
            sb.append('\n');
        }
        if (getStatistics() != null) {
            String st = getStatistics().dumpStatistics(false);
            if (st.contains("\n")) {
                if (sb.charAt(sb.length() - 1) != '\n') {
                    sb.append('\n');
                }
                sb.append("  " + st.replace("\n", "\n    "));
                sb.append('\n');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * Additional error handling, non by default.
     *
     * @param msg
     */
    @Override
    public void onError(WAMPMessage msg) {
    }

    /**
     * @return the statistics
     */
    @Override
    public WAMPStatistics getStatistics() {
        return statistics;
    }

    /**
     * @param statistics the statistics to set
     */
    @Override
    public void setStatistics(WAMPStatistics statistics) {
        this.statistics = statistics;
    }

    /**
     * @return the flows
     */
    @Override
    public List<WAMPMessagesFlow> getFlows() {
        return flows;
    }

    /**
     * @param flows the flows to set
     */
    @Override
    public void setFlows(List<WAMPMessagesFlow> flows) {
        this.flows = flows;
    }

    @Override
    public <T extends WAMPMessagesFlow> T getFlow(Class cl) {
        for (WAMPMessagesFlow f : flows) {
            if (cl.isAssignableFrom(f.getClass())) {
                return (T) f;
            }
        }
        return null;
    }

    /**
     * @return the closeReason
     */
    @Override
    public String getCloseReason() {
        return closeReason;
    }

    /**
     * @param closeReason the closeReason to set
     */
    @Override
    public void setCloseReason(String closeReason) {
        this.closeReason = closeReason;
    }

    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////// authentications
    ////////////////////////////////////////////////////////////////////////////
    /**
     * @return the auth
     */
    @Override
    public WAMPAuth getAuth() {
        return auth;
    }

    /**
     * @param auth the auth to set
     */
    @Override
    public void setAuth(WAMPAuth auth) {
        this.auth = auth;
    }

    @Override
    public Map<Long, WAMPAuth> getVirtualAuths() {
        return sessionAuths;
    }

    @Override
    public void killVirtualAuths(Long... ids) {
        if (ids != null) {
            WAMPBroker broker = getRealm().getActor(WAMP.Role.broker);
            for (Long id : ids) {
                if (id != null && sessionAuths.containsKey(id)) {
                    WAMPAuth auth = sessionAuths.remove(id);

                    if (broker != null && auth != null) try {
                        synchronized (this) {
                            broker.doEvent(null,
                                    0,
                                    SM_EVENT_ON_LEAVE,
                                    getId(),
                                    WAMPTools.EMPTY_DICT,
                                    WAMPTools.createList(
                                            id,
                                            auth.getAuthid(),
                                            auth.getRole()
                                    ),
                                    null);
                        }
                    } catch (WAMPException wex) {
                        wex.printStackTrace();
                    }
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////// caller auths
    ////////////////////////////////////////////////////////////////////////////
    /**
     * Register to session remote session's WAMPAuth
     *
     * @param sessionId
     * @param auth
     */
    @Override
    public void onRemoteAuthAdd(Long sessionId, WAMPAuth auth) {
        if (sessionId != getId() & auth != null) {
            sessionAuths.put(sessionId, auth);
        }
    }

    /**
     * Register to session remote session's WAMPAuth based on "on join" event...
     *
     * @param session
     * @param msg
     */
    @Override
    public void onRemoteAuthAdd(WAMPMessage msg) {
        if (hasLocalRole(Role.callee) && msg != null && msg.getDataLength() > 4 && WAMPMessageType.T_EVENT == msg.getType().getId()) {
            try {
                WAMPSubscription ws = getRealm().getActor(Role.subscriber);
                if (ws != null) {
                    String topic = ws.getTopic(this, msg);
                    if (topic != null && WAMP_FP_SessionMetaAPI.SM_EVENT_ON_JOIN.equals(topic)) {
                        sessionAuths.put(msg.getInt(1), new WAMPAuth(msg.getDict(4)));
                    }
                }
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    /**
     * Unregister remote session's WAMPAuth based on "on leave" event.
     *
     * @param msg
     */
    @Override
    public void onRemoteAuthRemove(WAMPMessage msg) {
        if (hasLocalRole(Role.callee) && msg != null && WAMPMessageType.T_EVENT == msg.getType().getId()) {
            try {
                WAMPSubscription ws = getRealm().getActor(Role.subscriber);
                if (ws != null) {
                    String topic = ws.getTopic(this, msg);
                    if (topic != null && WAMP_FP_SessionMetaAPI.SM_EVENT_ON_LEAVE.equals(topic)) {
                        sessionAuths.remove(msg.getInt(1));
                    }
                }
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    /**
     * Unregister remote session's WAMPAuth based on remote session id.
     *
     * @param msg
     */
    @Override
    public void onRemoteAuthRemove(Long remoteSessionId) {
        if (hasLocalRole(Role.callee) && remoteSessionId != null && sessionAuths.containsKey(remoteSessionId)) {
            sessionAuths.remove(remoteSessionId);
        }
    }

    /**
     * Return read-only set of known remote sessions' WAMPAuths
     *
     * @return
     */
    @Override
    public Map<Long, WAMPAuth> remoteAuths() {
        return Collections.unmodifiableMap(sessionAuths);
    }

    /**
     * Returns WAMPAuth of remote caller if provided in options.
     *
     * @param session
     * @param msg
     * @return
     */
    @Override
    public WAMPAuth remoteAuth(WAMPMessage msg) {
        if (msg != null && WAMPMessageType.T_INVOCATION == msg.getType().getId()) {
            Map<String, Object> opt = msg.getDict(2);
            if (opt != null && opt.containsKey(RPC_CALLER_ID_KEY)) {
                Long cid = (Long) opt.get(RPC_CALLER_ID_KEY);
                return sessionAuths.get(cid);
            }
        }
        return null;
    }

    /**
     * Returns WAMPAuth for remote caller id (requests it if missing and can
     * call router...
     *
     * @param session
     * @param msg
     * @return
     */
    @Override
    public synchronized WAMPAuth remoteAuth(Long remoteSessionId) {
        final WAMPAuth[] r = new WAMPAuth[1];
        final boolean[] done = new boolean[]{false};
        if (remoteSessionId != null) {
            if (sessionAuths.containsKey(remoteSessionId)) {
                r[0] = sessionAuths.get(remoteSessionId);
            }
            if (hasLocalRole(Role.caller)) {
                final WAMPRPCCaller wc = getRealm().getActor(WAMP.Role.caller);
                final WAMPSessionImpl localSession = this;
                try {
                    wc.call(localSession, WAMPTools.createDict(RPC_CALLER_ID_KEY, localSession.getId()), SM_RPC_SESSION_GET, WAMPTools.createList(remoteSessionId), null, new SimpleCallListener() {
                        @Override
                        public void onResult(Object result, String error) {
                            if (result instanceof Map && localSession.getId() != remoteSessionId) {
                                localSession.onRemoteAuthAdd(remoteSessionId, new WAMPAuth((Map<String, Object>) result));
                            } else {
                                localSession.onRemoteAuthAdd(remoteSessionId, WAMPAuth.Anonymous);
                            }
                            r[0] = sessionAuths.get(remoteSessionId);
                            done[0] = true;
                        }
                    });
                    long timeout = System.currentTimeMillis() + 1000 * 10;
                    while (!done[0] && System.currentTimeMillis() < timeout) {
                        Thread.sleep(1);
                    }
                } catch (WAMPException wex) {
                    wex.printStackTrace();
                } catch (InterruptedException iex) {
                    iex.printStackTrace();
                }
            }
        }
        return r[0];
    }
}
