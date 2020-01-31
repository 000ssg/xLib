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
package ssg.lib.wamp.rpc.impl.dealer;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMPConstantsBase;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.events.WAMPBroker;
import ssg.lib.wamp.flows.WAMPMessagesFlow.WAMPFlowStatus;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMPMessageType;
import ssg.lib.wamp.messages.WAMP_DT;
import ssg.lib.wamp.rpc.impl.Procedure;
import ssg.lib.wamp.rpc.impl.dealer.WAMPRPCRegistrations.RPCMeta.InvocationPolicy;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.wamp.stat.WAMPCallStatistics;

/**
 *
 * @author 000ssg
 */
public class WAMPRPCRegistrations {

    AtomicLong nextProcedureId = new AtomicLong(1);

    Map<Long, DealerProcedure> procedures = WAMPTools.createSynchronizedMap();
    public static final String TIME_FORMAT_8601 = "yyyy-MM-dd'T'HH:mm:ssX";

    // Subscriptions
    public static final String TOPIC_ON_CREATE = "wamp.registration.on_create";
    public static final String TOPIC_ON_REGISTER = "wamp.registration.on_register";
    public static final String TOPIC_ON_UNREGISTER = "wamp.registration.on_unregister";
    public static final String TOPIC_ON_DELETE = "wamp.registration.on_delete";

    //////////////////////////////////////////////////////////////////////// RPC
    // RPC names
    public static final String RPC_LIST = "wamp.registration.list";
    public static final String RPC_LOOKUP = "wamp.registration.lookup";
    public static final String RPC_MATCH = "wamp.registration.match";
    public static final String RPC_GET = "wamp.registration.get";
    public static final String RPC_LIST_CALLEES = "wamp.registration.list_callees";
    public static final String RPC_COUNT_CALLEES = "wamp.registration.count_callees";

    WAMPCallStatistics statistics;
    // RPC implementations
    DealerLocalProcedure rpcList = new DealerLocalProcedure(RPC_LIST) {
        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                WAMPRPCDealer dealer = session.getRealm().getActor(WAMP.Role.dealer);

                Map<String, Object> m = WAMPTools.createDict(null);
                for (Entry<String, MatchPolicy> entry : policies.entrySet()) {
                    m.put(entry.getKey(), entry.getValue().all());
                }
                session.send(WAMPMessage.result(msg.getId(0), WAMPTools.EMPTY_DICT, WAMPTools.EMPTY_LIST, m));
                return true;
            } else {
                return false;
            }
        }
    };
    DealerLocalProcedure rpcLookup = new DealerLocalProcedure(RPC_LOOKUP) {
        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                WAMPRPCDealer dealer = session.getRealm().getActor(WAMP.Role.dealer);

                String procedure = msg.getUri(0);
                Map<String, Object> options = msg.getDict(1);
                RPCMeta meta = lookup(procedure, options);
                session.send(WAMPMessage.result((meta != null) ? meta.registrations.get(0) : 0, WAMPTools.EMPTY_DICT, null, null));
                return true;
            } else {
                return false;
            }
        }
    };
    DealerLocalProcedure rpcMatch = new DealerLocalProcedure(RPC_MATCH) {
        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            return rpcLookup.doResult(session, msg);
        }
    };
    DealerLocalProcedure rpcGet = new DealerLocalProcedure(RPC_GET) {
        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            if (session.hasLocalRole(WAMP.Role.dealer) && session.getRealm().getActor(WAMP.Role.dealer) instanceof WAMPRPCDealer) {
                WAMPRPCDealer dealer = session.getRealm().getActor(WAMP.Role.dealer);

                String procedure = msg.getUri(0);
                Map<String, Object> options = msg.getDict(1);
                RPCMeta rpc = lookup(procedure, options);

                Map<String, Object> details = rpc.details();
                session.send(WAMPMessage.result((rpc != null) ? rpc.registrations.get(0) : 0, rpc != null ? rpc.details() : WAMPTools.EMPTY_DICT, null, null));
                return true;
            } else {
                return false;
            }
        }
    };
    DealerLocalProcedure rpcListCallees = new DealerLocalProcedure(RPC_LIST_CALLEES) {
        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            return false;
        }
    };
    DealerLocalProcedure rpcCountCallees = new DealerLocalProcedure(RPC_COUNT_CALLEES) {
        @Override
        public boolean doResult(WAMPSession session, WAMPMessage msg) throws WAMPException {
            return false;
        }
    };

    // Registrations
    public static final String MATCH_EXACT = "exact";
    public static final String MATCH_PREFIX = "prefix";
    public static final String MATCH_WILDCARD = "wildcard";

    Map<String, MatchPolicy> policies = WAMPTools.createMap(true);

    public WAMPRPCRegistrations() {
        policies.put(MATCH_EXACT, new ExactMatchPolicy());
        policies.put(MATCH_PREFIX, new PrefixMatchPolicy());
        policies.put(MATCH_WILDCARD, new WildcardMatchPolicy());
    }

    /**
     * Returns set of local methods to register for the feature.
     *
     * @param feature
     * @return
     */
    public DealerProcedure[] getFeatureMethods(WAMPFeature feature) {
        if (WAMPFeature.registration_meta_api.equals(feature)) {
            return new DealerProcedure[]{
                rpcList,
                rpcLookup,
                rpcMatch,
                rpcGet,
                rpcListCallees,
                rpcCountCallees
            };
        }
        return null;
    }

    /**
     * Registers unregistered methods for provided set of features, if any.
     *
     * @param features
     */
    public void registerFeatureMethods(Collection<WAMPFeature> features) {
        MatchPolicy mp = policies.get(MATCH_EXACT);
        // ensure built-in methods registration is atomic operation
        synchronized (mp) {
            List<DealerProcedure> procs = WAMPTools.createList();
            if (features != null) {
                for (WAMPFeature f : features) {
                    DealerProcedure[] fprocs = getFeatureMethods(f);
                    if (fprocs != null) {
                        for (DealerProcedure proc : fprocs) {
                            if (!procs.contains(proc)) {
                                procs.add(proc);
                            }
                        }
                    }
                }
            }

            for (DealerProcedure proc : procs) {
                if (!WAMP_DT.id.validate(proc.getId())) {
                    RPCMeta rpc = mp.all().get(proc.getName());
                    if (rpc == null) {
                        rpc = new RPCMeta(proc.getName(), null);
                        if (statistics != null) {
                            rpc.statistics = statistics.createChild(null, proc.getName());
                        }
                        mp.all().put(proc.getName(), rpc);
                    }

                    long registrationId = nextProcedureId.getAndIncrement();
                    proc.setId(registrationId);

                    try {
                        rpc.add(registrationId);
                    } catch (WAMPException wex) {
                        wex.printStackTrace();
                    }
                    procedures.put(registrationId, proc);
                }
            }
        }
    }

    public WAMPFlowStatus onRegister(WAMPSession session, WAMPMessage msg) throws WAMPException {
        long request = msg.getId(0);
        Map<String, Object> options = msg.getDict(1);
        String procedure = msg.getUri(2);
        String match = (String) options.get("match");
        String invocation = (String) options.get("invoke");
        MatchPolicy mp = policies.get(MATCH_EXACT);
        if (match != null) {
            if (policies.containsKey(match)) {
                mp = policies.get(match);
            }
        }

        RPCMeta rpc = null;
        long registrationId = 0;

        synchronized (mp) {
            rpc = mp.all().get(procedure);
            if (rpc == null) {
                rpc = new RPCMeta(procedure, (invocation != null) ? InvocationPolicy.valueOf(invocation) : null);
                if (statistics != null) {
                    rpc.statistics = statistics.createChild(null, procedure);
                }
                mp.all().put(procedure, rpc);
            }
            if (rpc.count() == 1 && !(session.getLocal().features().contains(WAMPFeature.shared_registration) || session.getLocal().features().contains(WAMPFeature.sharded_registration))) {
                session.send(WAMPMessage.error(WAMPMessageType.T_REGISTER, request, WAMPTools.EMPTY_DICT, WAMPConstantsBase.ProcedureAlreadyExists));
                return WAMPFlowStatus.failed;
            }

            registrationId = nextProcedureId.getAndIncrement();
            DealerProcedure proc = new DealerProcedure(rpc, procedure, options, session);
            proc.setStatistics(rpc.getStatistics(procedure) != null ? rpc.getStatistics(procedure).createChild(null, procedure) : null);

            proc.setId(registrationId);

            rpc.add(registrationId);
            procedures.put(registrationId, proc);
        }
        session.send(WAMPMessage.registered(request, registrationId));

        if (session.getLocal().features().contains(WAMPFeature.registration_meta_api)) {
            WAMPBroker broker = session.getRealm().getActor(WAMP.Role.broker);

            Map<String, Object> details = rpc.details();

            if (rpc.count() == 1) {
                // EVENT created
                broker.doEvent(null, session.getNextRequestId(), TOPIC_ON_CREATE, session.getId(), details, null, null);
            }
            // EVENT registered
            broker.doEvent(null, session.getNextRequestId(), TOPIC_ON_REGISTER, session.getId(), details, Collections.singletonList(registrationId), null);
        }

        return WAMPFlowStatus.handled;
    }

    public WAMPFlowStatus onUnregister(WAMPSession session, WAMPMessage msg) throws WAMPException {
        long request = msg.getId(0);
        long registrationId = msg.getId(1);
        return onUnregister(session, request, registrationId, msg);
    }

    /**
     * "Universal" unregister. If request is invalid id (not >0), no response
     * message is sent. If msg is null - no error is thrown if registrationId is
     * not found.
     *
     * @param session
     * @param request
     * @param registrationId
     * @param msg
     * @return
     * @throws WAMPException
     */
    public WAMPFlowStatus onUnregister(WAMPSession session, long request, long registrationId, WAMPMessage msg) throws WAMPException {
        boolean done = false;
        RPCMeta rpc = null;
        for (MatchPolicy mp : policies.values()) {
            for (Entry<String, RPCMeta> entry : mp.all().entrySet()) {
                if (entry.getValue().registrations.contains(registrationId)) {
                    rpc = entry.getValue();
                    rpc.remove(registrationId);
                    if (rpc.count() == 0) {
                        mp.all().remove(entry.getKey());
                    }
                    done = true;
                    break;
                }
            }
        }
        if (!done) {
            if (msg != null) {
                throw new WAMPException("No RPC meta to unregister for " + msg.toList());
            }
        } else {
            synchronized (procedures) {
                DealerProcedure proc = (DealerProcedure) procedures.remove(registrationId);
                if (proc != null) {
                    procedures.remove(registrationId);
                    if (WAMP_DT.id.validate(request)) {
                        session.send(WAMPMessage.unregistered(request));
                    }
                } else {
                    if (WAMP_DT.id.validate(request)) {
                        session.send(WAMPMessage.error(WAMPMessageType.T_UNREGISTER, request, WAMPTools.EMPTY_DICT, WAMPConstantsBase.NoSuchRegistration));
                    }
                }
            }

            if (session.getLocal().features().contains(WAMPFeature.registration_meta_api)) {
                WAMPBroker broker = session.getRealm().getActor(WAMP.Role.broker);
                // EVENT send unregister
                boolean mayAck = WAMP_DT.id.validate(request);
                broker.doEvent(null, (mayAck) ? session.getNextRequestId() : 0, TOPIC_ON_UNREGISTER, session.getId(), WAMPTools.EMPTY_DICT, Collections.singletonList(registrationId), null);
                if (rpc.count() == 0) {
                    // EVENT deleted
                    broker.doEvent(null, (mayAck) ? session.getNextRequestId() : 0, TOPIC_ON_DELETE, session.getId(), WAMPTools.EMPTY_DICT, Collections.singletonList(registrationId), null);
                }
            }

            // scan policies to remove realted stuff
            for (Entry<String, MatchPolicy> entry : policies.entrySet()) {
                Collection<String> removeNames = new HashSet<>();
                for (Entry<String, RPCMeta> rpcEntry : entry.getValue().all().entrySet()) {
                    List<Long> regs = rpcEntry.getValue().registrations;
                    if (regs != null && regs.contains(registrationId)) {
                        regs.remove(registrationId);
                    }
                    if (regs.isEmpty()) {
                        removeNames.add(rpcEntry.getKey());
                        break;
                    }
                }
                if (!removeNames.isEmpty()) {
                    for (String name : removeNames) {
                        entry.getValue().all().remove(name);
                    }
                }
            }
        }
        return WAMPFlowStatus.handled;
    }

    public DealerProcedure onCall(WAMPSession session, WAMPMessage msg) throws WAMPException {
        long request = msg.getId(0);
        Map<String, Object> options = msg.getDict(1);
        String procedure = msg.getUri(2);
        RPCMeta rpc = lookup(procedure, options);
        Long registeredId = (rpc != null) ? rpc.get() : null;
        //System.out.println("assigned CALL "+registeredId+" from "+rpc.registrations);
        DealerProcedure proc = (DealerProcedure) procedures.get(registeredId);
        if (rpc != null && proc == null) {
            rpc.registrations.remove(registeredId);
            while (!rpc.registrations.isEmpty() && rpc.registrations.size() > 1) {
                proc = (DealerProcedure) procedures.get(registeredId);
                if (proc == null) {
                    rpc.registrations.remove(registeredId);
                }
            }
            int a = 0;
        }
        return proc;
    }

    public synchronized RPCMeta lookup(String procedure, Map<String, Object> options) {
        for (Entry<String, MatchPolicy> entry : policies.entrySet()) {
            RPCMeta rpc = entry.getValue().match(procedure);
            if (rpc != null) {
                return rpc;
            }
        }
        return null;
    }

    public synchronized void close(WAMPSession... sessions) {
        for (WAMPSession session : sessions) {
            for (Entry<Long, DealerProcedure> entry : procedures.entrySet().toArray(new Entry[procedures.size()])) {
                try {
                    if (entry.getValue() != null && (entry.getValue() == null || entry.getValue().session == session)) {
                        try {
                            this.onUnregister(session, 0, entry.getKey(), null);
                        } catch (WAMPException wex) {
                            wex.printStackTrace();
                        }
                    }
                } catch (Throwable th) {
                    th.printStackTrace();
                }
            }
        }

        Collection<Long> ids = new HashSet<>();
        for (Entry<Long, DealerProcedure> entry : procedures.entrySet()) {
            for (WAMPSession session : sessions) {
                if (entry.getValue() != null && (entry.getValue() == null || entry.getValue().session == session)) {
                    ids.add(entry.getKey());
                }
            }
        }
        if (!ids.isEmpty()) {
            for (long id : ids) {
                procedures.remove(id);
            }
        }
        // scan policies to remove realted stuff
        for (Entry<String, MatchPolicy> entry : policies.entrySet()) {
            Collection<String> removeNames = new HashSet<>();
            for (Entry<String, RPCMeta> rpcEntry : entry.getValue().all().entrySet()) {
                List<Long> regs = rpcEntry.getValue().registrations;
                if (regs != null) {
                    for (long reg : ids) {
                        if (regs.contains(reg)) {
                            regs.remove(reg);
                        }
                    }
                }
                if (regs.isEmpty()) {
                    removeNames.add(rpcEntry.getKey());
                }
            }
            if (!removeNames.isEmpty()) {
                for (String name : removeNames) {
                    entry.getValue().all().remove(name);
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append("{");
        sb.append("nextProcedureId=");
        sb.append(nextProcedureId.get());
        sb.append(", procedures[" + procedures.size() + "]");
        if (statistics != null) {
            sb.append("\n  statistics=" + statistics.toString().replace("\n", "\n  "));
        }
        for (Entry<String, MatchPolicy> entry : policies.entrySet()) {
            sb.append("\n  policy=" + entry.getKey() + " -> " + entry.getValue().all().size());
            for (Object obj : entry.getValue().all().entrySet().toArray()) {
                Entry<String, RPCMeta> rpcE = (Entry<String, RPCMeta>) obj;
                sb.append("\n    " + rpcE.getKey() + ": " + rpcE.getValue().statistics);
                if (!rpcE.getValue().nameStatistics.isEmpty()) {
                    for (Object obj2 : rpcE.getValue().nameStatistics.entrySet().toArray()) {
                        Entry<String, WAMPCallStatistics> cs = (Entry<String, WAMPCallStatistics>) obj2;
                        sb.append("\n      " + cs.getValue().toString() + "\t " + cs.getKey());
                    }
                }
                sb.append("\n        ");
                sb.append(rpcE.getValue().registrations);
                sb.append("\n        ");
                sb.append(rpcE.getValue().details().toString().replace("\n", "\n        "));
            }
        }
        //sb.append(", name2procedureId=");
        //sb.append(name2procedureId);
        if (!procedures.isEmpty()) {
            sb.append("\n  procedures[" + procedures.size() + "]:");
            for (Object proct : procedures.values().toArray()) {
                Procedure proc = (Procedure) proct;
                sb.append("\n    ");
                sb.append(proc.toString().replace("\n", "\n    "));
            }
            sb.append('\n');
        }
        //", rpcList=" + rpcList + ", rpcLookup=" + rpcLookup + ", rpcMatch=" + rpcMatch + ", rpcGet=" + rpcGet + ", rpcListCallees=" + rpcListCallees + ", rpcCountCallees=" + rpcCountCallees + ", policies=" + policies + '}';
        sb.append('\n');
        sb.append('}');
        return sb.toString();
    }

    public static class RPCMeta {

        public static enum InvocationPolicy {
            single,
            roundrobin,
            random,
            first,
            last
        }
        String name;
        long created = System.currentTimeMillis();
        String match;
        InvocationPolicy invocation = InvocationPolicy.single;
        List<Long> registrations = WAMPTools.createSynchronizedList();
        int lastRR = -1;
        WAMPCallStatistics statistics;
        Map<String, WAMPCallStatistics> nameStatistics = WAMPTools.createSynchronizedMap();

        public RPCMeta(String name, InvocationPolicy invocation) {
            this.name = name;
            if (invocation != null) {
                this.invocation = invocation;
            }
        }

        public WAMPCallStatistics getStatistics(String name) {
            if (name == null || name.equals(this.name)) {
                return statistics;
            }
            synchronized (nameStatistics) {
                if (nameStatistics.containsKey(name)) {
                    return nameStatistics.get(name);
                }
                if (statistics != null) {
                    WAMPCallStatistics cs = statistics.createChild(null, name);
                    nameStatistics.put(name, cs);
                    return cs;
                } else {
                    return null;
                }
            }
        }

        public int count() {
            return registrations.size();
        }

        public synchronized long get() throws WAMPException {
            if (!registrations.isEmpty()) {
                switch (invocation) {
                    case single:
                        return registrations.get(0);
                    case roundrobin:
                        synchronized (registrations) {
                            lastRR++;
                            if (lastRR >= registrations.size()) {
                                lastRR = 0;
                            }
                            return registrations.get(lastRR);
                        }
                    case random:
                        synchronized (registrations) {
                            int idx = (int) (Math.random() * registrations.size());
                            if (idx == registrations.size()) {
                                idx = 0;
                            }
                            return registrations.get(idx);
                        }
                    case first:
                        return registrations.get(0);
                    case last:
                        return registrations.get(registrations.size() - 1);
                }
            }
            throw new WAMPException("No registered procedure to invoke in " + this);
        }

        public synchronized void add(long id) throws WAMPException {
            if (!WAMP_DT.id.validate(id)) {
                throw new WAMPException("Invalid procedure registration id when adding RPC meta: " + id + " to " + this);
            }
            switch (invocation) {
                case single:
                    if (!registrations.isEmpty()) {
                        throw new WAMPException("Cannot add extra registration if invocation policy is " + invocation + " for " + this);
                    }
                default:
            }
            if (!registrations.contains(id)) {
                registrations.add(id);
            }
        }

        public synchronized void remove(long id) throws WAMPException {
            if (registrations.contains(id)) {
                registrations.remove(id);
            } else {
                throw new WAMPException("No registration id (" + id + ") to remove from " + this);
            }
        }

        public Map<String, Object> details() {
            Map<String, Object> details = WAMPTools.createDict(null);
            details.put("id", registrations.get(0));
            details.put("created", new SimpleDateFormat(TIME_FORMAT_8601).format(new Date(created))); // to ISO 8601...
            details.put("uri", name);
            if (match != null) {
                details.put("match", match);
            }
            if (invocation != null) {
                details.put("invoke", invocation);
            }
            return details;
        }
    }

    public static interface MatchPolicy {

        Map<String, RPCMeta> all();

        RPCMeta match(String uri);
    }

    public static class ExactMatchPolicy implements MatchPolicy {

        Map<String, RPCMeta> all = WAMPTools.createSynchronizedMap();

        @Override
        public Map<String, RPCMeta> all() {
            return all;
        }

        @Override
        public RPCMeta match(String uri) {
            return (all().containsKey(uri)) ? all.get(uri) : null;
        }
    }

    public static class PrefixMatchPolicy implements MatchPolicy {

        Map<String, RPCMeta> all = WAMPTools.createSynchronizedMap();

        @Override
        public Map<String, RPCMeta> all() {
            return all;
        }

        @Override
        public RPCMeta match(String uri) {
            for (String s : all().keySet()) {
                if (uri.startsWith(s)) {
                    return all.get(s);
                }
            }
            return null;
        }
    }

    public static class WildcardMatchPolicy implements MatchPolicy {

        Map<String, RPCMeta> all = WAMPTools.createSynchronizedMap();

        @Override
        public Map<String, RPCMeta> all() {
            return all;
        }

        @Override
        public RPCMeta match(String uri) {
            String[] us = uri.split("\\.");
            for (String s : all().keySet()) {
                String[] ss = s.split("\\.");
                if (ss.length == us.length) {
                    boolean ok = true;
                    for (int i = 0; i < us.length; i++) {
                        if (ss[i].isEmpty() || ss[i].equals(us[i])) {
                            continue;
                        }
                        ok = false;
                        break;
                    }
                    if (ok) {
                        return all().get(s);
                    }
                }
            }
            return null;
        }
    }
}
