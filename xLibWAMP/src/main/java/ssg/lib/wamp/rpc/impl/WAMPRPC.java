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
package ssg.lib.wamp.rpc.impl;

import java.util.Collection;
import java.util.Map;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.WAMPActor;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPFeatureProvider;
import ssg.lib.wamp.WAMPRealm;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.stat.SimpleStatisticsData;
import ssg.lib.wamp.stat.WAMPCallStatistics;
import ssg.lib.wamp.stat.WAMPCallStatisticsImpl;

/**
 *
 * @author 000ssg
 */
public class WAMPRPC implements WAMPActor {

    public static final long TOO_MANY_CONCURRENT_CALLS = -1L;

    private Collection<WAMPFeature> features = WAMPTools.createSet(true);
    private Map<WAMPFeature, WAMPFeatureProvider> featureProviders = WAMPTools.createMap(true);
    Map<String, WAMPCallStatistics> notFoundCalls = WAMPTools.createSynchronizedMap();
    private WAMPCallStatistics statistics;

    public WAMPRPC() {
    }

    public WAMPRPC(Role[] roles, WAMPFeature... features) {
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

    public <T extends WAMPRPC> T configure(WAMP.Role[] roles, WAMPFeature feature, WAMPFeatureProvider provider) {
        if (feature != null) {
            if (provider == null) {
                if (featureProviders.containsKey(feature)) {
                    featureProviders.remove(feature);
                }
                features.add(feature);
            } else {
                if (roles != null) {
                    for (Role role : roles) {
                        if (Role.hasRole(role, feature.scope())) {
                            featureProviders.put(feature, provider);
                            if (!features.contains(feature)) {
                                features.add(feature);
                            }
                            break;
                        }
                    }
                }
            }
        }
        return (T) this;
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
    public void initFeatures(WAMP.Role[] roles, Map<WAMPFeature, WAMPFeatureProvider> featureProviders) {
        if (featureProviders != null) {
            for (Map.Entry<WAMPFeature, WAMPFeatureProvider> entry : featureProviders.entrySet()) {
                configure(roles, entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public Collection<WAMPFeature> features() {
        return getFeatures();
    }

    public <T extends WAMPFeatureProvider> T getFeatureProvider(WAMPFeature feature) {
        return feature != null && featureProviders.containsKey(feature) ? (T) featureProviders.get(feature) : null;
    }

    public Map<String, WAMPCallStatistics> getNotFoundCalls() {
        return notFoundCalls;
    }

    public String notFoundCallsInfo() {
        StringBuilder sb = new StringBuilder();
        if (!notFoundCalls.isEmpty()) {
            synchronized (notFoundCalls) {
                Object[] oos = notFoundCalls.entrySet().toArray();
                sb.append("\n  not found calls[" + oos.length + "]:");
                for (Object oo : oos) {
                    Map.Entry<String, WAMPCallStatistics> ee = (Map.Entry<String, WAMPCallStatistics>) oo;
                    sb.append("\n    " + ee.getKey() + "    " + ee.getValue().dumpStatistics(true));
                }
            }
        }
        return sb.toString();
    }

    public WAMPCallStatistics getStatisticsForNotFound(String name, boolean createMissing) {
        WAMPCallStatistics r = null;
        synchronized (notFoundCalls) {
            r = notFoundCalls.get(name);
            if (r == null && createMissing) {
                if (getStatistics() != null) {
                    r = getStatistics().createChild(null, name);
                } else {
                    r = new WAMPCallStatisticsImpl();
                    r.init(new SimpleStatisticsData(r.getGroupSize()));
                }
                if (r != null) {
                    notFoundCalls.put(name, r);
                }
            }
        }
        return r;
    }

    /**
     * @return the features
     */
    public Collection<WAMPFeature> getFeatures() {
        return features;
    }

    /**
     * @return the statistics
     */
    public WAMPCallStatistics getStatistics() {
        return statistics;
    }

    /**
     * @param statistics the statistics to set
     */
    public void setStatistics(WAMPCallStatistics statistics) {
        this.statistics = statistics;
    }
}
