/*
 * The MIT License
 *
 * Copyright 2021 sesidoro.
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

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import ssg.lib.wamp.auth.WAMPAuth;
import ssg.lib.wamp.flows.WAMPMessagesFlow;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMPMessageType;
import ssg.lib.wamp.stat.WAMPStatistics;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author 000ssg
 */
public interface WAMPSession extends Cloneable, Serializable {

    void addWAMPSessionListener(WAMPSessionListener l);

    void adjustFeatureSets(Map<WAMPFeature, WAMPFeatureProvider> featureProviders);

    void close() throws WAMPException;

    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////// outsourced handling
    ////////////////////////////////////////////////////////////////////////////
    /**
     * Actual message sending over transport channel is here.
     *
     * @param msg
     * @throws WAMPException
     */
    void doSend(WAMPMessage msg) throws WAMPException;

    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////// authentications
    ////////////////////////////////////////////////////////////////////////////
    /**
     * @return the auth
     */
    WAMPAuth getAuth();

    /**
     * Returns transport auth (if any). may differ from auth if explicit
     * authentication method or no auth in use.
     *
     * @return the transport auth
     */
    WAMPAuth getTransportAuth();

    Map<Long, WAMPAuth> getVirtualAuths();

    void killVirtualAuths(Long... ids);

    /**
     * @return the closeReason
     */
    String getCloseReason();

    <T extends WAMPMessagesFlow> T getFlow(Class cl);

    /**
     * @return the flows
     */
    List<WAMPMessagesFlow> getFlows();

    long getId();

    WAMPParty getLocal();

    long getNextRequestId();

    WAMPMessageType getPending(long request, boolean reset);

    int getPendingCount();

    WAMPRealm getRealm();

    WAMPParty getRemote();

    WAMPSessionState getState();

    /**
     * @return the statistics
     */
    WAMPStatistics getStatistics();

    boolean hasLocalRole(WAMP.Role role);

    boolean hasRemoteRole(WAMP.Role role);

    /**
     * Additional error handling, non by default.
     *
     * @param msg
     */
    void onError(WAMPMessage msg);

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////// caller auths
    ////////////////////////////////////////////////////////////////////////////
    /**
     * Register to session remote session's WAMPAuth
     *
     * @param sessionId
     * @param auth
     */
    void onRemoteAuthAdd(Long sessionId, WAMPAuth auth);

    /**
     * Register to session remote session's WAMPAuth based on "on join" event...
     *
     * @param session
     * @param msg
     */
    void onRemoteAuthAdd(WAMPMessage msg);

    /**
     * Unregister remote session's WAMPAuth based on "on leave" event.
     *
     * @param msg
     */
    void onRemoteAuthRemove(WAMPMessage msg);

    /**
     * Unregister remote session's WAMPAuth based on remote session id.
     *
     * @param msg
     */
    void onRemoteAuthRemove(Long remoteSessionId);

    /**
     * Returns WAMPAuth of remote caller if provided in options.
     *
     * @param session
     * @param msg
     * @return
     */
    WAMPAuth remoteAuth(WAMPMessage msg);

    /**
     * Returns WAMPAuth for remote caller id (requests it if missing and can
     * call router...
     *
     * @param session
     * @param msg
     * @return
     */
    WAMPAuth remoteAuth(Long remoteSessionId);

    /**
     * Return read-only set of known remote sessions' WAMPAuths
     *
     * @return
     */
    Map<Long, WAMPAuth> remoteAuths();

    void removeWAMPSessionListener(WAMPSessionListener l);

    void send(WAMPMessage message) throws WAMPException;

    /**
     * @param auth the auth to set
     */
    void setAuth(WAMPAuth auth);

    /**
     * 
     * @param remoteAuth 
     */
    void setTransportAuth(WAMPAuth remoteAuth);

    /**
     * @param closeReason the closeReason to set
     */
    void setCloseReason(String closeReason);

    /**
     * @param flows the flows to set
     */
    void setFlows(List<WAMPMessagesFlow> flows);

    void setId(long id);

    void setRemote(WAMPParty remote);

    void setState(WAMPSessionState state);

    /**
     * @param statistics the statistics to set
     */
    void setStatistics(WAMPStatistics statistics);

    boolean supportsFeature(WAMPFeature feature);

    /**
     * Returns HELLO message (USED IN MULTI-AUTH HANDLING...)
     *
     * NOTE: valid only for Router-side session!!! Used to enable iterate
     * authentication methods...
     *
     * @return
     */
    WAMPMessage helloMessage();

    /**
     * Represents session local or remote side with corresponding basic info and
     * evaluated set of mutually supported features.
     */
    public static class WAMPParty {

        // role/options
        private Map<WAMP.Role, Map<String, Object>> roles = WAMPTools.createMap();
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
        public WAMPParty(String agent, WAMP.Role... roles) throws WAMPException {
            this.agent = agent;
            for (WAMP.Role role : roles) {
                // ignore indicator roles
                if (WAMP.Role.router == role || WAMP.Role.client == role) {
                    continue;
                }
                this.roles.put(role, WAMPTools.createDict(null, null));
            }
            if (this.roles.isEmpty()) {
                throw new WAMPException("At least 1 WAMP role MUST be defined.");
            }
            router = WAMP.Role.hasRole(WAMP.Role.router, this.roles.keySet());
            if (router && WAMP.Role.hasRole(WAMP.Role.client, this.roles.keySet())) {
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
                                    if (f != null && WAMP.Role.hasRole(role, f.scope)) {
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
            router = WAMP.Role.hasRole(WAMP.Role.router, this.roles.keySet());
            if (router && WAMP.Role.hasRole(WAMP.Role.client, this.roles.keySet())) {
                throw new WAMPException("Either router or client role(s) must be defined, not both.");
            }
        }

        /**
         * @return the roles
         */
        public Map<WAMP.Role, Map<String, Object>> getRoles() {
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

        void toSend(WAMPSession session, WAMPMessage message);

        void onSent(WAMPSession session, WAMPMessage message, Throwable error);
    }

    public static class WAMPSessionWrapper implements WAMPSession {

        WAMPSession base;

        public WAMPSessionWrapper(WAMPSession base) {
            this.base = base;
        }

        @Override
        public void addWAMPSessionListener(WAMPSessionListener l) {
            base.addWAMPSessionListener(l);
        }

        @Override
        public void adjustFeatureSets(Map<WAMPFeature, WAMPFeatureProvider> featureProviders) {
            base.adjustFeatureSets(featureProviders);
        }

        @Override
        public void close() throws WAMPException {
            base.close();
        }

        @Override
        public void doSend(WAMPMessage msg) throws WAMPException {
            base.doSend(msg);
        }

        @Override
        public WAMPAuth getAuth() {
            return base.getAuth();
        }

        @Override
        public WAMPAuth getTransportAuth() {
            return base.getTransportAuth();
        }

        @Override
        public void setTransportAuth(WAMPAuth auth) {
            base.setTransportAuth(auth);
        }

        @Override
        public Map<Long, WAMPAuth> getVirtualAuths() {
            return base.getVirtualAuths();
        }

        @Override
        public void killVirtualAuths(Long... ids) {
            base.killVirtualAuths(ids);
        }

        @Override
        public String getCloseReason() {
            return base.getCloseReason();
        }

        @Override
        public <T extends WAMPMessagesFlow> T getFlow(Class cl) {
            return base.getFlow(cl);
        }

        @Override
        public List<WAMPMessagesFlow> getFlows() {
            return base.getFlows();
        }

        @Override
        public long getId() {
            return base.getId();
        }

        @Override
        public WAMPParty getLocal() {
            return base.getLocal();
        }

        @Override
        public long getNextRequestId() {
            return base.getNextRequestId();
        }

        @Override
        public WAMPMessageType getPending(long request, boolean reset) {
            return base.getPending(request, reset);
        }

        @Override
        public int getPendingCount() {
            return base.getPendingCount();
        }

        @Override
        public WAMPRealm getRealm() {
            return base.getRealm();
        }

        @Override
        public WAMPParty getRemote() {
            return base.getRemote();
        }

        @Override
        public WAMPSessionState getState() {
            return base.getState();
        }

        @Override
        public WAMPStatistics getStatistics() {
            return base.getStatistics();
        }

        @Override
        public boolean hasLocalRole(WAMP.Role role) {
            return base.hasLocalRole(role);
        }

        @Override
        public boolean hasRemoteRole(WAMP.Role role) {
            return base.hasRemoteRole(role);
        }

        @Override
        public void onError(WAMPMessage msg) {
            base.onError(msg);
        }

        @Override
        public void onRemoteAuthAdd(Long sessionId, WAMPAuth auth) {
            base.onRemoteAuthAdd(sessionId, auth);
        }

        @Override
        public void onRemoteAuthAdd(WAMPMessage msg) {
            base.onRemoteAuthAdd(msg);
        }

        @Override
        public void onRemoteAuthRemove(WAMPMessage msg) {
            base.onRemoteAuthRemove(msg);
        }

        @Override
        public void onRemoteAuthRemove(Long remoteSessionId) {
            base.onRemoteAuthRemove(remoteSessionId);
        }

        @Override
        public WAMPAuth remoteAuth(WAMPMessage msg) {
            return base.remoteAuth(msg);
        }

        @Override
        public WAMPAuth remoteAuth(Long remoteSessionId) {
            return base.remoteAuth(remoteSessionId);
        }

        @Override
        public Map<Long, WAMPAuth> remoteAuths() {
            return base.remoteAuths();
        }

        @Override
        public void removeWAMPSessionListener(WAMPSessionListener l) {
            base.removeWAMPSessionListener(l);
        }

        @Override
        public void send(WAMPMessage message) throws WAMPException {
            base.send(message);
        }

        @Override
        public void setAuth(WAMPAuth auth) {
            base.setAuth(auth);
        }

        @Override
        public void setCloseReason(String closeReason) {
            base.setCloseReason(closeReason);
        }

        @Override
        public void setFlows(List<WAMPMessagesFlow> flows) {
            base.setFlows(flows);
        }

        @Override
        public void setId(long id) {
            base.setId(id);
        }

        @Override
        public void setRemote(WAMPParty remote) {
            base.setRemote(remote);
        }

        @Override
        public void setState(WAMPSessionState state) {
            base.setState(state);
        }

        @Override
        public void setStatistics(WAMPStatistics statistics) {
            base.setStatistics(statistics);
        }

        @Override
        public boolean supportsFeature(WAMPFeature feature) {
            return base.supportsFeature(feature);
        }

        @Override
        public WAMPMessage helloMessage() {
            return base.helloMessage();
        }
    }
}
