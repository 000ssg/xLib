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
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.stat.WAMPStatistics;

/**
 * WAMP realm represents is named entity used to build role/features specific
 * set of message processors (WAMPActor instances) and their finalization (via
 * close() method).
 *
 * To enable realm use both on router and client side, it allows "partial"
 * closing for selected sessions only, as needed by router.
 *
 * @author sesidoro
 */
public class WAMPRealm implements Serializable, Cloneable {

    private String name;
    private Map<Role, WAMPActor> actors = new EnumMap<Role, WAMPActor>(Role.class);
    private WAMPStatistics statistics;
    LS<WAMPRealmListener> listeners = new LS<>(new WAMPRealmListener[0]);

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
    public static WAMPRealm createRealm(WAMPActorFactory actorFactory, String name, WAMPFeature[] features, Role... roles) throws WAMPException {
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
        return r;
    }

    public WAMPRealm addListener(WAMPRealmListener... ls) {
        listeners.add(ls);
        return this;
    }

    public synchronized void removeListener(WAMPRealmListener... ls) {
        listeners.remove(ls);
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
        if (!actors.isEmpty()) {
            Collection duplicates = new HashSet();
            for (Entry<Role, WAMPActor> entry : actors.entrySet()) {
                sb.append("\n  ");
                sb.append(entry.getKey());
                sb.append(": ");
                if (duplicates.contains(entry.getValue())) {
                    sb.append("#DUP:" + entry.getValue().getClass().getName());
                } else {
                    sb.append(entry.getValue().toString().replace("\n", "\n  "));
                }
                duplicates.add(entry.getValue());
            }
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

    public static interface WAMPRealmListener {

        public static enum WAMPActorEvent {
            preInit,
            postInit,
            preDone,
            postDone
        }

        void onActorEvent(WAMPActorEvent type, WAMPActor actor, WAMPSession... sessions);
    }

}
