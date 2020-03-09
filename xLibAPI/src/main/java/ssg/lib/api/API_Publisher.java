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
package ssg.lib.api;

import java.util.ArrayList;
import ssg.lib.api.util.APIException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import ssg.lib.api.util.APISearchable.APIMatcher;
import ssg.lib.api.util.APISearchable.APIMatcher.API_MATCH;

/**
 * Caches callables per name and evaluates best matching (resolves overloaded
 * methods).
 *
 * @author 000ssg
 */
public class API_Publisher {

    private static final Object NO_CONTEXT = new Object();

    API api;
    APIMatcher restrictions;
    Map<String, Map<Object, APICallable>> callables = Collections.synchronizedMap(new HashMap<>());
    Collection<String> missing = new HashSet<>();
    Object defaultContext = NO_CONTEXT;

    public <T extends API_Publisher> T configure(API api) {
        this.api = api;
        return (T) this;
    }

    public <T extends API_Publisher> T configureRestrictions(APIMatcher restrictions) {
        this.restrictions = restrictions;
        return (T) this;
    }

    public <T extends API_Publisher> T configureContext(Object defaultContext) {
        this.defaultContext = (defaultContext != null) ? defaultContext : NO_CONTEXT;
        return (T) this;
    }

    public <T extends API> T getAPI() {
        return (T) api;
    }

    public <T extends APICallable> T getCallable(String name, Object context) {
        Map<Object, APICallable> map = callables.get(name);
        if (map == null) {
            if (missing.contains(name)) {
                return null;
            }
        }

        APICallable caller = null;
        synchronized (callables) {
            Collection<APIProcedure> procs = null;
            if (map == null) {
                procs = api.find((item) -> {
                    if (restrictions != null && restrictions.matches(item) == API_MATCH.none) {
                        return API_MATCH.none;
                    }
                    if (item instanceof APIProcedure) {
                        if (name.equals(item.fqn())) {
                            return API_MATCH.exact;
                        }
                    } else if (name.startsWith(item.fqn())) {
                        return API_MATCH.partial;
                    }
                    return API_MATCH.none;
                }, APIProcedure.class, null);

                if (procs == null || procs.isEmpty()) {
                    missing.add(name);
                    return null;
                }
            }

            if (context == null) {
                context = defaultContext;
            }
            caller = (map != null) ? map.get(context) : null;
            if (caller == null) {
                if (map == null) {
                    map = Collections.synchronizedMap(new LinkedHashMap<>());
                    callables.put(name, map);
                }
                caller = createCallable(context, procs);
                map.put(context, caller);
            }
        }

        return (T) caller;
    }

    public APICallable createCallable(Object context, Collection<APIProcedure> procs) {
        if (procs != null) {
            if (procs.size() == 1) {
                return api.createCallable(procs.iterator().next(), context);
            } else if (procs.size() > 1) {
                return new MultiCallable(api, procs.toArray(new APIProcedure[procs.size()]), context);
            }
        }
        return null;
    }

    public static class MultiCallable implements APICallable {

        APIProcedure[] procs;
        APICallable[] callables;

        public MultiCallable(API api, APIProcedure[] procs, Object context) {
            this.procs = procs;
            callables = new APICallable[procs.length];
            for (int i = 0; i < procs.length; i++) {
                callables[i] = api.createCallable(procs[i], context);
            }
        }

        @Override
        public <T> T call(Map<String, Object> params) throws APIException {
            int best = best(params);
            if (best == -1) {
                throw new APIException("Parameters mismatch for '" + procs[0].fqn() + "': " + params);
            }
            return callables[best].call(params);
        }

        public APICallable bestCallable(Map<String, Object> params) {
            int best = best(params);
            if (best >= 0) {
                return callables[best];
            } else {
                return null;
            }
        }

        public int best(Map<String, Object> params) {
            float[] match = new float[procs.length];
            int best = 0;
            for (int i = 0; i < match.length; i++) {
                match[i] = procs[i].testParameters(params);
            }
            // best from +
            for (int i = 0; i < match.length; i++) {
                if (match[i] > match[best]) {
                    best = i;
                }
            }
            for (int i = 0; i < match.length; i++) {
                if (Math.abs(match[i]) > match[best]) {
                    best = i;
                }
            }
            return procs[best].testParameters(params) != 0 ? best : -1;
        }

        public int best(List params) {
            float[] match = new float[procs.length];
            int best = 0;
            for (int i = 0; i < match.length; i++) {
                match[i] = procs[i].testParameters(params);
            }
            // best from +
            for (int i = 0; i < match.length; i++) {
                if (match[i] > match[best]) {
                    best = i;
                }
            }
            for (int i = 0; i < match.length; i++) {
                if (Math.abs(match[i]) > match[best]) {
                    best = i;
                }
            }
            return procs[best].testParameters(params) != 0 ? best : -1;
        }

        public int best(Object[] params) {
            float[] match = new float[procs.length];
            int best = 0;
            for (int i = 0; i < match.length; i++) {
                match[i] = procs[i].testParameters(params);
            }
            // best from +
            for (int i = 0; i < match.length; i++) {
                if (match[i] > match[best]) {
                    best = i;
                }
            }
            for (int i = 0; i < match.length; i++) {
                if (Math.abs(match[i]) > match[best]) {
                    best = i;
                }
            }
            return procs[best].testParameters(params) != 0 ? best : -1;
        }

        @Override
        public Map<String, Object> toParametersMap(Object[] params) {
            int best = best(params);
            if (best >= 0) {
                return procs[best].toParametersMap(params);
            }
            return null;
        }

        @Override
        public Map<String, Object> toParametersMap(List params) {
            int best = best(params);
            if (best >= 0) {
                return procs[best].toParametersMap(params);
            }
            return null;
        }

        @Override
        public List toParametersList(Map<String, Object> params) {
            int best = best(params);
            if (best >= 0) {
                return procs[best].toParametersList(params);
            }
            return null;
        }

        @Override
        public <T extends APIProcedure> T getAPIProcedure(Object params) {
            int best = -1;
            if (params instanceof Map) {
                best = best((Map) params);
            } else if (params instanceof List) {
                best = best((List) params);
            } else if (params != null && params.getClass().isArray()) {
                if (params.getClass().getComponentType().isPrimitive()) {
                    best = best(new Object[]{params});
                } else {
                    best = best((Object[]) params);
                }
            } else if (params != null) {
                best = best(new Object[]{params});
            } else {
                best = best((List) null);
            }
            if (best >= 0) {
                return (T) procs[best];
            } else {
                return null;
            }
        }
    }

    public static class API_Publishers {

        Map<String, API_Publisher> apis = new LinkedHashMap<>();

        public API_Publishers() {
        }

        public API_Publishers add(String name, API api) {
            if (api != null) {
                if (name == null) {
                    name = api.name;
                }
                apis.put(name, new API_Publisher().configure(api));
            }
            return this;
        }

        public API_Publishers add(String name, API_Publisher api) {
            if (api != null) {
                if (name == null) {
                    name = api.getAPI().name;
                }
                apis.put(name, api);
            }
            return this;
        }

        public Collection<String> getAPINames() {
            return apis.keySet();
        }

        public <T extends API_Publisher> T getAPIPublisher(String name) {
            return (T) apis.get(name);
        }

        public void removeAPIPublisher(String name) {
            if (name != null) {
                apis.remove(name);
            }
        }

        public <T extends API> T getAPI(String name) {
            API_Publisher pub = apis.get(name);
            if (pub != null) {
                return (T) pub.getAPI();
            } else {
                return null;
            }
        }

        public Collection<String> getNames(final String apiName) {
            API api = getAPI(apiName);

            Collection<APIProcedure> alls = api.find((item) -> {
                switch (APIMatcher.matchFQN(item, apiName)) {
                    case exact:
                        return (item instanceof APIProcedure) ? API_MATCH.exact : API_MATCH.partial;
                    case over:
                        return (item instanceof APIProcedure) ? API_MATCH.exact : (item instanceof APIGroup) ? API_MATCH.partial : API_MATCH.none;
                    case partial:
                        return API_MATCH.partial;
                    case none:
                        return API_MATCH.none;
                }
                return API_MATCH.none;
            }, APIProcedure.class, null);

            if (alls == null || alls.isEmpty()) {
                return null;
            }
            List<APIProcedure> all = new ArrayList<>(alls.size());
            all.addAll(alls);
            Collections.sort(all, (p1, p2) -> {
                return p1.fqn().compareTo(p2.fqn());
            });
            Collection<String> procNames = new LinkedHashSet<>();
            for (APIProcedure p : all) {
                procNames.add(p.fqn());
            }
            return procNames;
        }
    }
}
