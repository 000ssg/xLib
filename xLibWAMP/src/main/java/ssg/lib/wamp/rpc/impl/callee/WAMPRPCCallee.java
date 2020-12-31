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
package ssg.lib.wamp.rpc.impl.callee;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.WAMPActor;
import static ssg.lib.wamp.WAMPConstantsAdvanced.ERROR_Unavailable;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPRealm;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.WAMPSessionState;
import ssg.lib.wamp.auth.WAMPAuth;
import ssg.lib.wamp.flows.WAMPMessagesFlow;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMPMessageType;
import ssg.lib.wamp.rpc.impl.callee.CalleeProcedure.Callee;
import ssg.lib.wamp.rpc.WAMPCallee;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_CALLER_ID_DISCLOSE_CALLER;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_CALLER_ID_KEY;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_INVOCATION_PROCEDURE_EXACT_KEY;
import ssg.lib.wamp.rpc.impl.Procedure;
import ssg.lib.wamp.rpc.impl.WAMPRPC;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.wamp.stat.WAMPCallStatistics;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_PROGRESSIVE_CALL_REQUEST_KEY;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_PROGRESSIVE_CALL_PROGRESS_KEY;

/**
 *
 * @author 000ssg
 */
public class WAMPRPCCallee extends WAMPRPC implements WAMPCallee {

    public static final WAMPFeature[] supports = new WAMPFeature[]{
        WAMPFeature.shared_registration,
        WAMPFeature.sharded_registration,
        WAMPFeature.progressive_call_results,
        WAMPFeature.call_timeout,
        WAMPFeature.call_reroute
    };

    Map<Long, CalleeCall> calls = WAMPTools.createSynchronizedMap();
    Map<Long, Procedure> procedures = WAMPTools.createSynchronizedMap();
    Map<String, Long> procedureNames = WAMPTools.createSynchronizedMap();

    ScheduledExecutorService pool;
    boolean ownedPool = false;
    private int maxConcurrentTasks = 10;
    private int maxQueuedTasks = maxConcurrentTasks * 2;
    Map<Long, CalleeProcedure> pending = WAMPTools.createSynchronizedMap(false);
    AtomicInteger callsWIP = new AtomicInteger();
    Map<String, WAMPCallStatistics> callStats = WAMPTools.createSynchronizedMap();

    ScheduledFuture<?> runner;

    public WAMPRPCCallee() {
    }

    public WAMPRPCCallee(ScheduledExecutorService pool) {
        this.pool = pool;
    }

    public WAMPRPCCallee(WAMPFeature... features) {
        super(new Role[]{Role.callee}, WAMPFeature.intersection(supports, features));
    }

    public WAMPRPCCallee(ScheduledExecutorService pool, WAMPFeature... features) {
        super(new Role[]{Role.callee}, WAMPFeature.intersection(supports, features));
        this.pool = pool;
    }

    public void setScheduledExecutor(ScheduledExecutorService pool) {
        if (!isRunning()) {
            this.pool = pool;
            ownedPool = false;
        }
    }

    @Override
    public <T extends WAMPActor> T done(WAMPSession... sessions) {
        super.done();
        stop();
        return (T) this;
    }

    @Override
    public <T extends WAMPActor> T init(WAMPRealm realm) {
        super.init(realm);
        start();
        return (T) this;
    }

    public void start() {
        if (runner == null) {
            if (pool == null) {
                pool = Executors.newScheduledThreadPool(getMaxConcurrentTasks());
                ownedPool = true;
            }
            runner = pool.schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        onStarted();
                        while (runner != null && !runner.isCancelled()) {
                            try {
                                runCycle();
                                Thread.sleep(5);
                            } catch (Throwable th) {
                                //th.printStackTrace();
                                onError(th);
                            }
                        }
                    } finally {
                        runner = null;
                        try {
                            onStopped();
                        } finally {
                            if (ownedPool) {
                                pool.shutdown();
                                pool = null;
                            }
                        }
                    }
                }
            },
                    1,
                    TimeUnit.NANOSECONDS
            );
        }
    }

    public boolean isRunning() {
        return runner != null;
    }

    public void stop() {
        if (runner != null) {
            if (!runner.isCancelled()) {
                runner.cancel(false);
            }
        }
    }

    public void onStarted() {
        System.out.println(getClass().getSimpleName() + ".onStarted");
    }

    public void onStopped() {
        System.out.println(getClass().getSimpleName() + ".onStopped");
    }

    public void onError(Throwable th) {
        System.out.println(getClass().getSimpleName() + ".onError: " + th);
    }

    public long getProcedureRegistrationId(String name) {
        Long id = procedureNames.get(name);
        if (id == null) {
            id = 0L;
        }
        return id;
    }

    ////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////// actions
    ////////////////////////////////////////////////////////////////////////
    //public void subscribe(WAMPSession session, Map<String, Object> options, String topic) throws WAMPException {
    @Override
    public void register(WAMPSession session, Map<String, Object> options, String procedure, Callee callee) throws WAMPException {
        boolean isRouter = session.hasLocalRole(WAMP.Role.router);
        if (isRouter) {
            throw new WAMPException("Only client can register, not router.");
        }
        if (!session.hasLocalRole(WAMP.Role.callee)) {
            throw new WAMPException("Only client with role 'callee' can register procedure.");
        }
        CalleeProcedure proc = new CalleeProcedure(procedure, options, callee);
        if (getStatistics() != null && proc.getStatistics() == null) {
            proc.setStatistics(getStatistics().createChild(null, procedure));
        }
        if (WAMPSessionState.established == session.getState()) {
            long request = session.getNextRequestId();
            pending.put(request, proc);
            session.send(WAMPMessage.register(request, options, procedure));
        } else {
            throw new WAMPException("Cannot register: session is not established: " + session.getState());
        }
    }

    @Override
    public void unregister(WAMPSession session, long registrationId) throws WAMPException {
        boolean isRouter = session.hasLocalRole(WAMP.Role.router);
        if (isRouter) {
            throw new WAMPException("Only client can unregister, not router.");
        }
        if (!session.hasLocalRole(WAMP.Role.callee)) {
            throw new WAMPException("Only client with role 'callee' can unregister procedure.");
        }
        CalleeProcedure proc = (CalleeProcedure) procedures.get(registrationId);
        if (proc == null) {
            throw new WAMPException("No procedure found with id=" + registrationId);
        }
        long request = session.getNextRequestId();
        pending.put(request, proc);
        session.send(WAMPMessage.unregister(request, registrationId));
    }

    @Override
    public void yield_(WAMPSession session, long invocationId, boolean lastFragment, List args, Map<String, Object> argsKw) throws WAMPException {
        boolean isRouter = session.hasLocalRole(WAMP.Role.router);
        if (isRouter) {
            throw new WAMPException("Only client can yield, not router.");
        }
        if (!session.hasLocalRole(WAMP.Role.callee)) {
            throw new WAMPException("Only client with role 'callee' can yield (return procedure execution results).");
        }
        CalleeCall call = (CalleeCall) (lastFragment ? calls.remove(invocationId) : calls.get(invocationId));
        if (call == null) {
            throw new WAMPException("No call found with id=" + invocationId);
        }
        Map<String, Object> details = WAMPTools.createDict(null);
        if (!lastFragment) {
            //if (getFeatures().contains(WAMPFeature.progressive_call_results) && call.isProgressiveResult()) {
            if (session.supportsFeature(WAMPFeature.progressive_call_results) && call.isProgressiveResult()) {
                details.put(RPC_PROGRESSIVE_CALL_PROGRESS_KEY, true);
            } else {
                throw new WAMPException("Progressive result is not allowed in this call" + ((getFeatures().contains(WAMPFeature.progressive_call_results)) ? "" : "(missing feature " + WAMPFeature.progressive_call_results + ")") + ": " + call + ", " + args + ", " + argsKw);
            }
        }
        if (argsKw != null) {
            session.send(WAMPMessage.yield_(invocationId, details, args, argsKw));
        } else if (args != null) {
            session.send(WAMPMessage.yield_(invocationId, details, args));
        } else {
            session.send(WAMPMessage.yield_(invocationId, details));
        }

        if (call.proc != null && call.proc.getStatistics() != null) {
            call.proc.getStatistics().onDuration(call.durationNano());
        }

    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////// reactions
    ////////////////////////////////////////////////////////////////////////
    @Override
    public WAMPMessagesFlow.WAMPFlowStatus registered(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        WAMPMessagesFlow.WAMPFlowStatus r = validateSession(session, msg);
        if (r != WAMPMessagesFlow.WAMPFlowStatus.handled) {
            return r;
        }
        long request = msg.getId(0);
        long registrationId = msg.getId(1);
        CalleeProcedure proc = pending.remove(request);
        if (proc == null) {
            r = WAMPMessagesFlow.WAMPFlowStatus.ignored;
        }
        if (WAMPMessagesFlow.WAMPFlowStatus.handled == r) {
            proc.setId(registrationId);
            synchronized (procedures) {
                procedures.put(proc.getId(), proc);
                procedureNames.put(proc.getName(), proc.getId());
            }
        }
        return r;
    }

    @Override
    public WAMPMessagesFlow.WAMPFlowStatus unregistered(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        WAMPMessagesFlow.WAMPFlowStatus r = validateSession(session, msg);
        if (r != WAMPMessagesFlow.WAMPFlowStatus.handled) {
            return r;
        }
        long request = msg.getId(0);
        CalleeProcedure proc = pending.remove(request);
        if (proc == null) {
            r = WAMPMessagesFlow.WAMPFlowStatus.ignored;
        }
        if (WAMPMessagesFlow.WAMPFlowStatus.handled == r) {
            procedures.remove(proc.getId());
        }
        return r;
    }

    @Override
    public WAMPMessagesFlow.WAMPFlowStatus invocation(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null || pool == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        if (getMaxQueuedTasks() > 0 && calls.size() > getMaxQueuedTasks()) {
            //System.out.println("invoke busy[" + session.getId() + "  " + callsWIP.get() + "/" + calls.size() + "]: " + msg.toList());
            if (session.supportsFeature(WAMPFeature.call_reroute)) {
                session.send(WAMPMessage.error(msg.getType().getId(), msg.getInt(0), WAMPTools.EMPTY_DICT, ERROR_Unavailable));
                return WAMPMessagesFlow.WAMPFlowStatus.handled;
            } else {
                return WAMPMessagesFlow.WAMPFlowStatus.busy;
            }
        }
        WAMPMessagesFlow.WAMPFlowStatus r = validateSession(session, msg);
        if (r != WAMPMessagesFlow.WAMPFlowStatus.handled) {
            return r;
        }
        long request = msg.getId(0);
        long registrationId = msg.getId(1);
        Map<String, Object> details = msg.getDict(2);
        List args = (msg.getData().length > 3) ? msg.getList(3) : null;
        Map<String, Object> argsKw = (msg.getData().length > 4) ? msg.getDict(4) : null;
        CalleeProcedure proc = (CalleeProcedure) procedures.get(registrationId);
        if (proc == null) {
            Map<String, WAMPCallStatistics> nfcs = getNotFoundCalls();
            synchronized (nfcs) {
                String pn = ((details.containsKey(RPC_INVOCATION_PROCEDURE_EXACT_KEY)) ? (String) details.get(RPC_INVOCATION_PROCEDURE_EXACT_KEY) : proc.getName());
                WAMPCallStatistics cs = nfcs.get(pn);
                if (cs == null) {
                    cs = getStatisticsForNotFound(pn, true);
                    //nfcs.put(pn, cs);
                }
                cs.onCall();
                cs.onError();
            }
            r = WAMPMessagesFlow.WAMPFlowStatus.ignored;
        } else if (proc != null) {
            if (getStatistics() == null && session.getStatistics() != null && session.getStatistics().getCallStatistics() != null) {
                setStatistics(session.getStatistics().getCallStatistics());
            }
            if (proc.getStatistics() == null && getStatistics() != null) {
                proc.setStatistics(getStatistics().createChild(null, "callee." + proc.getName()));
            }
        }
        if (WAMPMessagesFlow.WAMPFlowStatus.handled == r) {
            CalleeCall call = new CalleeCall(proc, details);

            { // ensure caller authentication info is initialized/available if required or provided
                boolean needAuth = proc.getOptions().containsKey(RPC_CALLER_ID_DISCLOSE_CALLER) && (Boolean) proc.getOptions().get(RPC_CALLER_ID_DISCLOSE_CALLER);
                Long callerId = details.containsKey(RPC_CALLER_ID_KEY) ? ((Number) details.get(RPC_CALLER_ID_KEY)).longValue() : null;

                WAMPAuth auth = (callerId != null) ? session.remoteAuth(msg) : null;
                if (needAuth && callerId != null && auth == null || callerId != null && auth == null) {
                    auth = session.remoteAuth(callerId);
                }
            }

            String procedure = (details.containsKey(RPC_INVOCATION_PROCEDURE_EXACT_KEY)) ? (String) details.get(RPC_INVOCATION_PROCEDURE_EXACT_KEY) : proc.getName();

            // if can - run immediately, otherwise prepare for delayed call
            if ((callsWIP.get() > getMaxConcurrentTasks())) {
                final CalleeCall ccall = call;
                ccall.delayed = () -> {
                    callsWIP.incrementAndGet();
                    // TODO: find stardard for actual procedure name evaluation, now rely on own "procedure" in details...
                    return (Future) proc.callee.invoke(ccall, pool, procedure, args, argsKw);
                };
            } else {
                callsWIP.incrementAndGet();
            }
            call.session = session;
            call.setId(request);
            call.proc = proc;
            //if (getFeatures().contains(WAMPFeature.progressive_call_results)) {
            call.setProgressiveResult(false);
            if (session.supportsFeature(WAMPFeature.progressive_call_results)) {
                if (details.containsKey(RPC_PROGRESSIVE_CALL_REQUEST_KEY)) {
                    call.setProgressiveResult((Boolean) details.get(RPC_PROGRESSIVE_CALL_REQUEST_KEY));
                }
            }

            WAMPCallStatistics pcs = callStats.get(procedure);
            if (pcs == null) {
                pcs = (proc.getStatistics() != null)
                        ? proc.getStatistics().createChild(null, procedure)
                        : null;
                if (pcs != null) {
                    callStats.put(procedure, pcs);
                }
            }
            if (pcs != null) {
                call.callStatistics = pcs;
                pcs.onCall();
            }

            synchronized (calls) {
                proc.wip.add(request);
                // TODO: find stardard for actual procedure name evaluation, now rely on own "procedure" in details...
                call.future = (call.hasDelayed()) ? null : proc.callee.invoke(call, pool, procedure, args, argsKw);
                calls.put(request, call);
            }
        }
        return r;
    }

    @Override
    public WAMPMessagesFlow.WAMPFlowStatus interrupt(WAMPSession session, WAMPMessage msg) throws WAMPException {
        boolean isRouter = session.hasLocalRole(WAMP.Role.router);
        if (isRouter) {
            throw new WAMPException("Only client can yield, not router.");
        }
        if (!session.hasLocalRole(WAMP.Role.callee)) {
            throw new WAMPException("Only client with role 'callee' can yield (return procedure execution results).");
        }

        long invocationId = msg.getId(0);
        CalleeCall call = (CalleeCall) calls.remove(invocationId);
        if (call == null) {
            // ignore, assuming it might be completed.
            return WAMPMessagesFlow.WAMPFlowStatus.handled;
        }

        // cancel or prevent running
        if (call.future != null) {
            call.future.cancel(false);
        } else {
            call.delayed = null;
            if (call.getStatistics() != null) {
                call.getStatistics().onCancel();
                call.getStatistics().onDuration(call.durationNano());
            }
        }
        return WAMPMessagesFlow.WAMPFlowStatus.handled;
    }

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////// errors
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public WAMPMessagesFlow.WAMPFlowStatus error(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        WAMPMessagesFlow.WAMPFlowStatus r = validateSession(session, msg);
        if (r != WAMPMessagesFlow.WAMPFlowStatus.handled) {
            return r;
        }
        long op = msg.getId(0);
        long req = msg.getId(1);
        switch ((int) op) {
            case WAMPMessageType.T_REGISTER:
            case WAMPMessageType.T_UNREGISTER:
                CalleeProcedure call = (CalleeProcedure) procedures.remove(req);
                if (call != null) {
                    call.wip.remove(req);
                    session.onError(msg);
                } else {
                    call = (CalleeProcedure) pending.remove(req);
                    // TODO: error ? exception ?
                    r = (call != null) ? WAMPMessagesFlow.WAMPFlowStatus.handled : WAMPMessagesFlow.WAMPFlowStatus.ignored;
                }
                break;
            default:
                r = WAMPMessagesFlow.WAMPFlowStatus.ignored;
        }
        return r;
    }

    public WAMPMessagesFlow.WAMPFlowStatus validateSession(WAMPSession session, WAMPMessage msg) {
        boolean isBroker = session.hasLocalRole(WAMP.Role.callee);
        if (!isBroker) {
            return WAMPMessagesFlow.WAMPFlowStatus.ignored;
        }
        if (session == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        return WAMPMessagesFlow.WAMPFlowStatus.handled;
    }

    public void runCycle() throws WAMPException {
        if (!calls.isEmpty()) {
            Collection<CalleeCall> wip = WAMPTools.createList();
            synchronized (this.calls) {
                wip.addAll(calls.values());
            }
            if (wip != null) {
                for (CalleeCall call : wip) {
                    if (call == null) {
                        continue;
                    }
                    if (call.future != null) {
                        boolean forcedTimeout = false;
                        if (call.hasTimeout() && call.isOvertime() && !call.future.isDone()) {
                            // cancel task if timeout is exceeded and is configured
                            if (call.session.supportsFeature(WAMPFeature.call_timeout)) {
                                call.future.cancel(true);
                                forcedTimeout = true;
                            }
                        }
                        if (call.future.isCancelled()) {
                            callsWIP.decrementAndGet();
                            this.calls.remove(call.getId());
                            if (call.getStatistics() != null) {
                                call.getStatistics().onError();
                                call.getStatistics().onDuration(call.durationNano());
                            }
                            call.session.send(WAMPMessage.error(WAMPMessageType.T_INVOCATION, call.getId(), WAMPTools.EMPTY_DICT, forcedTimeout ? "timeout.invocation.error" : "cancelled.invocation.error"));
                        } else if (call.future.isDone()) {
                            callsWIP.decrementAndGet();
                            call.proc.wip.remove(call.getId());
                            try {
                                try {
                                    Object result = call.future.get();
                                    yield_(call.session, call.getId(), true, resultArgs(result), resultArgsKw(result));
                                    if (call.getStatistics() != null) {
                                        call.getStatistics().onDuration(call.durationNano());
                                    }
                                } catch (InterruptedException iex) {
                                    if (call.getStatistics() != null) {
                                        call.getStatistics().onError();
                                        call.getStatistics().onDuration(call.durationNano());
                                    }
                                    call.session.send(WAMPMessage.error(WAMPMessageType.T_INVOCATION, call.getId(), WAMPTools.EMPTY_DICT, "interrupted.invocation.error"));
                                } catch (WAMPException wex) {
                                    if (call.getStatistics() != null) {
                                        call.getStatistics().onError();
                                        call.getStatistics().onDuration(call.durationNano());
                                    }
                                    call.session.send(WAMPMessage.error(
                                            WAMPMessageType.T_INVOCATION,
                                            call.getId(),
                                            WAMPTools.EMPTY_DICT,
                                            getClass().getName() + ".invocation.error",
                                            WAMPTools.EMPTY_LIST,
                                            WAMPTools.createDict("stacktrace", WAMPTools.getStackTrace(wex))
                                    ));
                                } catch (ExecutionException eex) {
                                    if (call.getStatistics() != null) {
                                        call.getStatistics().onError();
                                        call.getStatistics().onDuration(call.durationNano());
                                    }
                                    call.session.send(WAMPMessage.error(
                                            WAMPMessageType.T_INVOCATION,
                                            call.getId(),
                                            WAMPTools.EMPTY_DICT,
                                            getClass().getName() + ".invocation.error",
                                            WAMPTools.EMPTY_LIST,
                                            WAMPTools.createDict("stacktrace", WAMPTools.getStackTrace(eex))
                                    ));
                                }
                            } catch (Throwable th) {
                                th.printStackTrace();
                                int a = 0;
                            } finally {
                                this.calls.remove(call.getId());
                            }
                        } else {
                            // WIP...
                            int a = 0;
                        }
                    } else if (call.hasDelayed()) {
                        if (getMaxConcurrentTasks() <= 0 || callsWIP.get() < getMaxConcurrentTasks()) {
                            call.runDelayed();
                        } else {
                            int a = 0;
                        }
                    } else {
                        callsWIP.decrementAndGet();
                        this.calls.remove(call.getId());
                        call.proc.wip.remove(call.getId());
                        if (call.getStatistics() != null) {
                            call.getStatistics().onError();
                            call.getStatistics().onDuration(call.durationNano());
                        }
                        call.session.send(WAMPMessage.error(WAMPMessageType.T_INVOCATION, call.getId(), WAMPTools.EMPTY_DICT, "no_executor.invocation.error"));
                    }
                }
            }
        }
    }

    public List resultArgs(Object value) {
        return (value instanceof List)
                ? (List) value
                : (value instanceof Enumeration)
                        ? Collections.list((Enumeration) value)
                        : (value != null && value.getClass().isArray())
                        ? Arrays.asList(value)
                        : value instanceof Map
                                ? Collections.emptyList()
                                : (value != null)
                                        ? Collections.singletonList(value)
                                        : Collections.emptyList();
    }

    public Map<String, Object> resultArgsKw(Object obj) {
        if (obj instanceof Map) {
            return (Map) obj;
        } else {
            return null;
        }
    }

    public String dumpInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName() + "{");
        sb.append("calls/WIP=" + this.calls.size() + "/" + this.callsWIP.get());
        StringBuilder sbp = new StringBuilder();
        for (Procedure p : procedures.values()) {
            WAMPCallStatistics cs = p.getStatistics();
            if (cs != null && cs.hasCalls()) {
                sbp.append("\n  " + p.getName() + "  " + cs.toString());
            }
        }
        if (sbp.length() > 0) {
            sb.append(sbp);
            sb.append("\n");
        }

        sb.append('}');
        return sb.toString();
    }

    /**
     * @return the maxConcurrentTasks
     */
    public int getMaxConcurrentTasks() {
        return maxConcurrentTasks;
    }

    /**
     * @param maxConcurrentTasks the maxConcurrentTasks to set
     */
    public void setMaxConcurrentTasks(int maxConcurrentTasks) {
        this.maxConcurrentTasks = maxConcurrentTasks;
    }

    /**
     * @return the maxQueuedTasks
     */
    public int getMaxQueuedTasks() {
        return maxQueuedTasks;
    }

    /**
     * @param maxQueuedTasks the maxQueuedTasks to set
     */
    public void setMaxQueuedTasks(int maxQueuedTasks) {
        this.maxQueuedTasks = maxQueuedTasks;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append("{");
        sb.append("\n  features=" + this.getFeatures());
        sb.append("\n  calls[" + callsWIP.get() + "/" + calls.size() + "]=" + calls.keySet());
        sb.append("\n  call statistics=" + getStatistics());
        if (!callStats.isEmpty()) {
            try {
                Object[] oos = callStats.entrySet().toArray();
                for (Object oo : oos) {
                    Entry<String, WAMPCallStatistics> ee = (Entry<String, WAMPCallStatistics>) oo;
                    sb.append("\n    " + ee.getKey());
                    sb.append("\t ");
                    sb.append(ee.getValue().dumpStatistics(true));
                }
            } catch (Throwable th) {
            }
        }
        sb.append("\n  procedureNames[" + procedureNames.size() + "]=" + procedureNames.keySet());
        sb.append("\n  procedures[" + procedures.size() + "]=" + "");
        try {
            Object[] oos = procedures.entrySet().toArray();
            for (Object oo : oos) {
                Entry<Long, Procedure> ee = (Entry<Long, Procedure>) oo;
                sb.append("\n    " + ee.getKey());
                sb.append("\t ");
                sb.append(ee.getValue().getName());
                sb.append("\t ");
                sb.append(ee.getValue().getStatistics());
            }
        } catch (Throwable th) {
        }
        sb.append("\n  pool=" + (pool != null) + ", own=" + ownedPool);
        sb.append("\n  maxQueuedTasks/queued=" + maxQueuedTasks + "/" + this.calls.size());
        sb.append("\n  maxConcurrentTasks/WIP=" + maxConcurrentTasks + "/" + this.callsWIP.get());
        sb.append("\n  pending[" + pending.size() + "]=" + pending);
        sb.append("\n  runner=" + runner);
        if (!getNotFoundCalls().isEmpty()) {
            sb.append(notFoundCallsInfo());
            sb.append("\n");
        }
        sb.append('}');
        return sb.toString();

    }
}
