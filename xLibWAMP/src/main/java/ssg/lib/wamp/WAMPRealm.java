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
import ssg.lib.wamp.util.LS;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.auth.WAMPAuth;
import ssg.lib.wamp.auth.WAMPAuthProvider;
import ssg.lib.wamp.stat.WAMPStatistics;

/**
 * WAMP realm represents is named entity used to build role/features specific
 * set of message processors (WAMPActor instances) and their finalization (via
 * close() method).
 *
 * To enable realm use both on router and client side, it allows "partial"
 * closing for selected sessions only, as needed by router.
 *
 * @author 000ssg
 */
public class WAMPRealm implements Serializable, Cloneable {

    private String name;
    private Map<Role, WAMPActor> actors = new EnumMap<Role, WAMPActor>(Role.class);
    private WAMPStatistics statistics;
    LS<WAMPRealmListener> listeners = new LS<>(new WAMPRealmListener[0]);
    Map<WAMPFeature, WAMPFeatureProvider> featureProviders;
    private List<WAMPAuthProvider> authProviders = new ArrayList<>();
    LS<SessionVerifier> sessionVerifiers = new LS<>(new SessionVerifier[0]);

    private WAMPRealm() {
    }

    /**
     * Realm smart creation.
     *
     * @param actorFactory optional, if null, default factory is used
     * @param name
     * @param features
     * @param roles
     * @return
     * @throws WAMPException
     */
    public static WAMPRealm createRealm(WAMPActorFactory actorFactory, String name, WAMPFeature[] features, Map<WAMPFeature, WAMPFeatureProvider> featureProviders, Role... roles) throws WAMPException {
        if (roles == null || Role.hasRole(Role.router, roles) && Role.hasRole(Role.client, roles)) {
            throw new WAMPException("Mixed or no router/client roles are not supported: " + Arrays.asList(roles));
        }

        WAMPRealm r = new WAMPRealm();
        r.setName(name);
        if (roles != null && roles.length > 0 && roles[0] != null) {
            for (Role role : roles) {
                // ignore null or indicating role
                if (role == null || role == Role.client || role == Role.router) {
                    continue;
                }
                WAMPActor actor = (actorFactory != null) ? actorFactory.newActor(role, features) : WAMPActorFactory.createActor(role, features);
                if (actor != null) {
                    r.setActor(actor, role);
                }
            }
        }
        r.featureProviders = featureProviders;
        return r;
    }

    public WAMPRealm addListener(WAMPRealmListener... ls) {
        listeners.add(ls);
        return this;
    }

    public synchronized void removeListener(WAMPRealmListener... ls) {
        listeners.remove(ls);
    }

    public WAMPRealm addSessisonVerifier(SessionVerifier... ls) {
        sessionVerifiers.add(ls);
        return this;
    }

    public WAMPRealm removeSessisonVerifier(SessionVerifier... ls) {
        sessionVerifiers.remove(ls);
        return this;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    public <T extends WAMPActor> T getActor(Role role) {
        return (T) actors.get(role);
    }

    public void setActor(WAMPActor actor, Role... roles) {
        for (Role role : roles) {
            actors.put(role, actor);
        }
    }

    public void init() {
        WAMPRealmListener[] ls = listeners.get();
        for (WAMPActor actor : actors.values()) {
            for (WAMPRealmListener l : ls) {
                l.onActorEvent(WAMPRealmListener.WAMPActorEvent.preInit, actor);
            }
            actor.init(this);
            for (WAMPRealmListener l : ls) {
                l.onActorEvent(WAMPRealmListener.WAMPActorEvent.postInit, actor);
            }
        }
    }

    public List<WAMPAuthProvider> getAuthProviders() {
        return authProviders;
    }

    /**
     * Closes any resources allocated to realm
     */
    public void close() {
        WAMPRealmListener[] ls = listeners.get();
        for (WAMPActor actor : actors.values()) {
            for (WAMPRealmListener l : ls) {
                l.onActorEvent(WAMPRealmListener.WAMPActorEvent.preDone, actor);
            }
            actor.done();
            for (WAMPRealmListener l : ls) {
                l.onActorEvent(WAMPRealmListener.WAMPActorEvent.postDone, actor);
            }
        }
    }

    public void close(WAMPSession... sessions) {
        WAMPRealmListener[] ls = listeners.get();
        for (WAMPActor actor : actors.values()) {
            for (WAMPRealmListener l : ls) {
                l.onActorEvent(WAMPRealmListener.WAMPActorEvent.preDone, actor, sessions);
            }
            actor.done(sessions);
            for (WAMPRealmListener l : ls) {
                l.onActorEvent(WAMPRealmListener.WAMPActorEvent.postDone, actor, sessions);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append("{");
        sb.append("name=");
        sb.append(name);
        sb.append(", actors=");
        sb.append(actors.size());
        if (featureProviders != null) {
            sb.append(", feature providers=");
            sb.append(featureProviders.size());
        }
        sb.append(", auth providers=");
        sb.append(authProviders.size());
        sb.append(", session verifiers=");
        sb.append(sessionVerifiers.size());
        if (!actors.isEmpty()) {
            sb.append("\n  actors:");
            Collection duplicates = new HashSet();
            for (Entry<Role, WAMPActor> entry : actors.entrySet()) {
                sb.append("\n    ");
                sb.append(entry.getKey());
                sb.append(": ");
                if (duplicates.contains(entry.getValue())) {
                    sb.append("#DUP:" + entry.getValue().getClass().getName());
                } else {
                    sb.append(entry.getValue().toString().replace("\n", "\n    "));
                }
                duplicates.add(entry.getValue());
            }
        }

        if (featureProviders != null && !featureProviders.isEmpty()) {
            sb.append("\n  feature providers:");
            for (Entry<WAMPFeature, WAMPFeatureProvider> e : featureProviders.entrySet()) {
                sb.append("\n    " + e.getKey());
                sb.append("\n      " + e.getValue().toString().replace("\n", "\n      "));
            }
        }

        if (!authProviders.isEmpty()) {
            sb.append("\n  auth providers:");
            for (WAMPAuthProvider ap : authProviders) {
                sb.append("\n    " + ap.toString().replace("\n", "\n    "));
            }
        }
        if (!sessionVerifiers.isEmpty()) {
            sb.append("\n  session verifiers:");
            for (SessionVerifier sp : sessionVerifiers.get()) {
                sb.append("\n    " + sp.toString().replace("\n", "\n    "));
            }
        }

        if (getStatistics()
                != null) {
            sb.append("\n  " + getStatistics().dumpStatistics(false).replace("\n", "\n  "));
        }

        sb.append('\n');
        sb.append('}');
        return sb.toString();
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
     * Check if session is valid for given realm. Used to ensure all established
     * session characteristics match realm requirements.
     *
     * Should be/is used just before sending "WELCOME" message on router or
     * anytime when changed context may require session invalidation.
     *
     * @param session
     * @throws WAMPException
     */
    public void verifySession(WAMPSession session, WAMPAuth auth) throws WAMPException {
        if (!sessionVerifiers.isEmpty()) {
            for (SessionVerifier verifier : sessionVerifiers.get()) {
                verifier.verifySession(this, session, auth);

            }
        }
    }

    public static interface WAMPRealmListener {

        public static enum WAMPActorEvent {
            preInit,
            postInit,
            preDone,
            postDone
        }

        void onActorEvent(WAMPActorEvent type, WAMPActor actor, WAMPSession... sessions);
    }

    public static interface SessionVerifier {

        void verifySession(WAMPRealm realm, WAMPSession session, WAMPAuth auth) throws WAMPException;
    }

}
