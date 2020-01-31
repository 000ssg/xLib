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
package ssg.lib.wamp.events.impl;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.WAMPActor;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPRealm;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.util.WAMPTools;

/**
 * Represents subscription functionality base. Forked into Broker and Client
 * implementations to provide related actions/reactions.
 *
 * All operations need session and are validated with session role(s).
 *
 * @author sesidoro
 */
public class WAMPSubscription implements WAMPActor {

    // binds topics and subscriptions
    Map<String, List<Subscription>> topics = WAMPTools.createSynchronizedMap();
    // subscription by id
    Map<Long, Subscription> subscriptions = WAMPTools.createSynchronizedMap();
    // collection for registration of supported features
    Collection<WAMPFeature> features = new LinkedHashSet<>();

    public WAMPSubscription() {
    }

    public WAMPSubscription(Role[] roles, WAMPFeature... features) {
        if (features != null) {
            for (WAMPFeature f : features) {
                if (!this.features.contains(f)) {
                    if (roles != null) {
                        for (Role role : roles) {
                            if (Role.hasRole(role, f.scope())) {
                                this.features.add(f);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public <T extends WAMPActor> T init(WAMPRealm realm) {
        return (T) this;
    }

    @Override
    public <T extends WAMPActor> T done(WAMPSession... sessions) {
        return (T) this;
    }

    @Override
    public Collection<WAMPFeature> features() {
        return features;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (getClass().isAnonymousClass()) {
            sb.append(getClass().getName());
        } else {
            sb.append(getClass().getSimpleName());
        }
        sb.append('{');
        sb.append("topics=" + topics.size());
        sb.append(", subscriptions=" + subscriptions.size());
        sb.append(", features=" + features);
        sb.append(toStringInlineExt());
        if (!topics.isEmpty()) {
            sb.append("\n  Topics[" + topics.size() + "] " + topics.keySet());
            Object[] oos = null;
            synchronized (topics) {
                oos = topics.entrySet().toArray();
            }
            for (Object oo : oos) {
                Entry<String, List<Subscription>> ee = (Entry<String, List<Subscription>>) oo;
                List<Subscription> lst = ee.getValue();
                sb.append("\n  '" + ee.getKey() + "' [" + ((lst != null) ? lst.size() + "]: " : "] <none>"));
                if (lst != null) {
                    sb.append('[');
                    boolean first = true;
                    for (Subscription s : lst) {
                        if (first) {
                            first = false;
                        } else {
                            sb.append(", ");
                        }
                        sb.append(s.id);
                    }
                    sb.append(']');
                }
            }
        }
        if (!subscriptions.isEmpty()) {
            sb.append("\n  Subscriptions[" + subscriptions.size() + "]");
            Object[] oos = null;
            synchronized (subscriptions) {
                oos = subscriptions.entrySet().toArray();
            }
            for (Object oo : oos) {
                Entry<Long, Subscription> ee = (Entry<Long, Subscription>) oo;
                Subscription s = ee.getValue();
                sb.append("\n  " + ee.getKey() + ":\t " + s.toString().replace("\n", "\n  "));
            }
        }

        String ext = toStringBlockExt();
        if (ext != null) {
            sb.append("\n  " + ext.replace("\n", "\n  "));
        }

        if (!topics.isEmpty() || !subscriptions.isEmpty() || ext != null) {
            sb.append('\n');
        }
        sb.append('}');
        return sb.toString();
    }

    public String toStringInlineExt() {
        return "";
    }

    public String toStringBlockExt() {
        return null;
    }

}
