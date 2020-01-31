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
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.WAMPSession.WAMPSessionExtendedListener;
import ssg.lib.wamp.flows.WAMPMessagesFlow;
import ssg.lib.wamp.flows.WAMPPublishingFlow;
import ssg.lib.wamp.flows.WAMPRPCFlow;
import ssg.lib.wamp.flows.WAMPSessionFlow;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMPMessageType;
import ssg.lib.wamp.rpc.impl.dealer.WAMPRPCDealer;
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
public abstract class WAMPSession implements Serializable, Cloneable {

    public static final WAMPSession NO_SESSION = new WAMPSession() {
        @Override
        public void onSend(WAMPMessage msg) throws WAMPException {
            throw new UnsupportedOperationException("NO SESSION");
        }
    };

    private AtomicLong nextID = new AtomicLong(1);

    WAMPRealm realm;
    long id = 0; // no session: id MUST be > 0
    WAMPSessionState state = WAMPSessionState.open;
    WAMPParty local;
    WAMPParty remote;

    Map<Long, WAMPMessageType> pending = WAMPTools.createSynchronizedMap(false);
    private List<WAMPMessagesFlow> flows = WAMPTools.createList();

//    List<WAMPSessionListener> listeners = WAMPTools.createSynchronizedList();
//    List<WAMPSessionExtendedListener> xListeners = WAMPTools.createSynchronizedList();
    LS<WAMPSessionListener> listeners = new LS<>(new WAMPSessionListener[0]);
    LS<WAMPSessionExtendedListener> xListeners = new LS<>(new WAMPSessionExtendedListener[0]);

    WAMPStatistics statistics;

    private WAMPSession() {
    }

    public WAMPSession(WAMPRealm realm, Role... localRoles) throws WAMPException {
        this.realm = realm;
        local = new WAMPParty(null, localRoles);
        flows.add(new WAMPSessionFlow());
        if (hasLocalRole(Role.broker) || hasLocalRole(Role.publisher) || hasLocalRole(Role.subscriber)) {
            flows.add(new WAMPPublishingFlow());
        }
        if (hasLocalRole(Role.dealer) || hasLocalRole(Role.caller) || hasLocalRole(Role.callee)) {
            flows.add(new WAMPRPCFlow());
        }
        adjustFeatureSets();
    }

    public void adjustFeatureSets() {
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
                        // register registration meta api methods...
                        WAMPRPCDealer dealer = realm.getActor(Role.dealer);
                        dealer.initFeatures();
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
        }
    }

    public WAMPRealm getRealm() {
        return realm;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public WAMPParty getLocal() {
        return local;
    }

    public WAMPParty getRemote() {
        return remote;
    }

    public void setRemote(WAMPParty remote) {
        this.remote = remote;
        adjustFeatureSets();
    }

    public WAMPSessionState getState() {
        return state;
    }

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

    public boolean hasLocalRole(Role role) {
        return Role.hasRole(role, local.getRoles().keySet());
    }

    public boolean hasRemoteRole(Role role) {
        return remote != null && Role.hasRole(role, remote.getRoles().keySet());
    }

    public boolean supportsFeature(WAMPFeature feature) {
        return feature != null
                && local != null
                && remote != null
                && local.features().contains(feature)
                && remote.features().contains(feature);
    }

    public long getNextRequestId() {
        return nextID.getAndIncrement();
    }

    public void send(WAMPMessage message) throws WAMPException {
        if (message != null && WAMPMessageType.isRequestMessageType(message.getType())) {
            pending.put(message.getId(0), message.getType());
        }
        if (WAMPMessageType.T_GOODBYE == message.getType().getId()) {
            if (WAMPSessionState.established == getState()) {
                setState(WAMPSessionState.closing);
            }
        }
        //System.out.println("WAMPSession["+this.getId()+"]:send:"+message.toString().replace("\n", "\n  "));
        Throwable error = null;
        try {
            onSend(message);
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

    public int getPendingCount() {
        return pending.size();
    }

    public WAMPMessageType getPending(long request, boolean reset) {
        return (reset) ? pending.remove(request) : pending.get(request);
    }

    public void addWAMPSessionListener(WAMPSessionListener l) {
        listeners.add(l);
        if (l instanceof WAMPSessionExtendedListener) {
            xListeners.add((WAMPSessionExtendedListener) l);
        }
    }

    public void removeWAMPSessionListener(WAMPSessionListener l) {
        listeners.remove(l);
        if (l instanceof WAMPSessionExtendedListener) {
            xListeners.remove((WAMPSessionExtendedListener) l);
        }
    }

    public void close() throws WAMPException {
        setState(WAMPSessionState.closed);
        //realm.close(this);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append("{");
        sb.append("realm=" + realm.getName());
        sb.append(", id=" + id);
        sb.append(", state=" + state);
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

    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////// outsourced handling
    ////////////////////////////////////////////////////////////////////////////
    /**
     * Actual message sending over transport channel is here.
     *
     * @param msg
     * @throws WAMPException
     */
    public abstract void onSend(WAMPMessage msg) throws WAMPException;

    /**
     * Additional error handling, non by default.
     *
     * @param msg
     */
    public void onError(WAMPMessage msg) {
    }

    /**
     * Represents session local or remote side with corresponding basic info and
     * evaluated set of mutually supported features.
     */
    public static class WAMPParty {

        // role/options
        private Map<Role, Map<String, Object>> roles = WAMPTools.createMap();
        // party name (optional)
        private String agent;
        // cached evaluated indicator of router or client party
        private boolean router;
        // initial (on handshake) and finally effective set of features (once handshake is completed)
        Collection<WAMPFeature> features = new LinkedHashSet<>();

        public WAMPParty() {
        }

        /**
         * Local party constructor.
         *
         * @param agent
         * @param roles
         * @throws WAMPException
         */
        public WAMPParty(String agent, Role... roles) throws WAMPException {
            this.agent = agent;
            for (Role role : roles) {
                // ignore indicator roles
                if (Role.router == role || Role.client == role) {
                    continue;
                }
                this.roles.put(role, WAMPTools.createDict(null, null));
            }
            if (this.roles.isEmpty()) {
                throw new WAMPException("At least 1 WAMP role MUST be defined.");
            }
            router = Role.hasRole(Role.router, this.roles.keySet());
            if (router && Role.hasRole(Role.client, this.roles.keySet())) {
                throw new WAMPException("Either router or client role(s) must be defined, not both.");
            }
        }

        /**
         * Remote party constructor that uses HELLO's roles' options to
         * configure roles/features set.
         *
         * @param agent
         * @param roles
         * @throws WAMPException
         */
        public WAMPParty(String agent, Map<String, Map> roles) throws WAMPException {
            this.agent = agent;
            if (roles != null) {
                for (String key : roles.keySet()) {
                    WAMP.Role role = WAMP.Role.valueOf(key);
                    Map<String, Object> roleMap = roles.get(key);
                    Map<String, Object> map = WAMPTools.createMap();
                    for (String roleKey : roleMap.keySet()) {
                        Object val = roleMap.get(roleKey);
                        if ("features".equals(roleKey)) {
                            Map<String, Object> featuresMap = (Map) val;
                            for (String featureName : featuresMap.keySet()) {
                                Object fv = featuresMap.get(featureName);
                                if (fv instanceof Boolean && ((Boolean) fv)) {
                                    WAMPFeature f = WAMPFeature.find(featureName);
                                    if (f != null && Role.hasRole(role, f.scope)) {
                                        features().add(f);
                                    }
                                }
                            }
                        }
                        map.put(key, val);
                    }
                    this.roles.put(role, map);
                }
            }
            if (this.roles.isEmpty()) {
                throw new WAMPException("At least 1 WAMP role MUST be defined.");
            }
            router = Role.hasRole(Role.router, this.roles.keySet());
            if (router && Role.hasRole(Role.client, this.roles.keySet())) {
                throw new WAMPException("Either router or client role(s) must be defined, not both.");
            }
        }

        /**
         * @return the roles
         */
        public Map<Role, Map<String, Object>> getRoles() {
            return roles;
        }

        /**
         * @return the agent
         */
        public String getAgent() {
            return agent;
        }

        public void setAgent(String agent) {
            this.agent = agent;
        }

        /**
         * @return the router
         */
        public boolean isRouter() {
            return router;
        }

        public Collection<WAMPFeature> features() {
            return features;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
            sb.append("{");
            sb.append("agent=");
            sb.append(agent);
            sb.append(", router=");
            sb.append(router);
            sb.append(", roles=");
            sb.append(Arrays.asList(roles));
            if (!features.isEmpty()) {
                sb.append(", features=");
                for (WAMPFeature f : features) {
                    sb.append(Arrays.asList(f.uri));
                }
            }
            sb.append('}');
            return sb.toString();
        }

    }

    /**
     * Callback on session state change to "established" or "closed".
     */
    public static interface WAMPSessionListener {

        void onEstablished(WAMPSession session);

        void onClosed(WAMPSession session);
    }

    public static interface WAMPSessionExtendedListener extends WAMPSessionListener {

        void onSent(WAMPSession session, WAMPMessage message, Throwable error);
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
     * @return the flows
     */
    public List<WAMPMessagesFlow> getFlows() {
        return flows;
    }

    /**
     * @param flows the flows to set
     */
    public void setFlows(List<WAMPMessagesFlow> flows) {
        this.flows = flows;
    }
}
