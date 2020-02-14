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

import java.util.List;
import java.util.Map;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.WAMPActor;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.flows.WAMPMessagesFlow;
import ssg.lib.wamp.messages.WAMPMessageType;
import ssg.lib.wamp.messages.WAMP_DT;
import ssg.lib.wamp.WAMPConstantsBase;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPRealm;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.rpc.WAMPDealer;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_CALL_TIMEOUT;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_CANCEL_OPT_MODE_KEY;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_CANCEL_OPT_MODE_KILL;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_CANCEL_OPT_MODE_KILLNOWAIT;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_CANCEL_OPT_MODE_SKIP;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_INVOCATION_PROCEDURE_EXACT_KEY;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_PROGRESSIVE_CALL_REQUEST_KEY;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_PROGRESSIVE_CALL_PROGRESS_KEY;
import ssg.lib.wamp.rpc.impl.Call;
import ssg.lib.wamp.rpc.impl.WAMPRPC;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.wamp.stat.WAMPCallStatistics;

/**
 *
 * @author 000ssg
 */
public class WAMPRPCDealer extends WAMPRPC implements WAMPDealer {

    public static final WAMPFeature[] supports = new WAMPFeature[]{
        WAMPFeature.call_canceling,
        WAMPFeature.progressive_call_results,
        WAMPFeature.call_timeout
    };

    // session id -> (invocationId -> call)
    Map<Long, Map<Long, Call>> calls = WAMPTools.createSynchronizedMap();

    // RPC registrations
    WAMPRPCRegistrations registrations = new WAMPRPCRegistrations();

    public WAMPRPCDealer() {
        getFeatures().add(WAMPFeature.registration_meta_api);
    }

    public WAMPRPCDealer(WAMPFeature... features) {
        super(new Role[]{Role.dealer}, WAMPFeature.intersection(WAMPFeature.merge(supports, WAMPRPCRegistrations.supports), features));
    }

    public void initFeatures() {
        registrations.registerFeatureMethods(getFeatures());
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////// registrations
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public WAMPMessagesFlow.WAMPFlowStatus register(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        WAMPMessagesFlow.WAMPFlowStatus r = validateSession(session);
        if (r != WAMPMessagesFlow.WAMPFlowStatus.handled) {
            return r;
        }
        long request = msg.getId(0);
        Map<String, Object> options = msg.getDict(1);
        String procedure = msg.getUri(2);
        if (!validateProcedure(session, procedure)) {
            session.send(WAMPMessage.error(WAMPMessageType.T_REGISTER, request, WAMPTools.EMPTY_DICT, WAMPConstantsBase.InvalidURI));
            r = WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        if (r == WAMPMessagesFlow.WAMPFlowStatus.handled) {
            try {
                r = registrations.onRegister(session, msg);
            } catch (WAMPException wex) {
                session.onError(msg);
                r = WAMPMessagesFlow.WAMPFlowStatus.failed;
            }
        }
        return r;
    }

    @Override
    public WAMPMessagesFlow.WAMPFlowStatus unregister(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        WAMPMessagesFlow.WAMPFlowStatus r = validateSession(session);
        if (r != WAMPMessagesFlow.WAMPFlowStatus.handled) {
            return r;
        }
        long request = msg.getId(0);
        long registrationId = msg.getId(1);
        r = registrations.onUnregister(session, msg);
        return r;
    }

    /**
     * Add-on method used to perform timeout checks and action if configured.
     *
     * By defaulr handles "call_timeout" logic if feature is configured.
     *
     * @param session
     * @throws WAMPException
     */
    @Override
    public void checkTimeout(WAMPSession session) throws WAMPException {
        if (session.supportsFeature(WAMPFeature.call_timeout)) {
            Map<Long, Call> sessionCalls = calls.get(session.getId());
            if (sessionCalls != null && !sessionCalls.isEmpty()) {
                Object[] oo = sessionCalls.values().toArray();
                for (Object o : oo) {
                    if (o instanceof DealerCall) {
                        DealerCall call = (DealerCall) o;
                        if (call.hasTimeout() && call.isOvertime()) {
                            synchronized (call) {
                                int runCount = 0;
                                int timeoutCount = 0;
                                for (int i = 0; i < call.getProceduresCount(); i++) {
                                    DealerProcedure p = call.getProcedure(i);
                                    long id = call.getInvocationId(i);
                                    if (id > 0) {
                                        runCount++;
                                        if (p.session.supportsFeature(WAMPFeature.call_timeout)) {
                                            timeoutCount++;
                                        }
                                    }
                                }

                                // if timeout is not supported by either callee -> do dealer-level timeout, i.e. interrupt ALL others...
                                if (runCount > 0 && timeoutCount < runCount) {
                                    for (int i = 0; i < call.getProceduresCount(); i++) {
                                        DealerProcedure p = call.getProcedure(i);
                                        long id = call.getInvocationId(i);
                                        if (id > 0) {
                                            call.getProcedure(i).session.send(WAMPMessage.interrupt(call.getInvocationId(i), WAMPTools.EMPTY_DICT));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////// invocations
    ////////////////////////////////////////////////////////////////////////////
    /**
     * Keep call info and make invocation
     *
     * @param session
     * @param msg
     * @return
     * @throws WAMPException
     */
    @Override
    public WAMPMessagesFlow.WAMPFlowStatus call(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        WAMPMessagesFlow.WAMPFlowStatus r = validateSession(session);
        if (r != WAMPMessagesFlow.WAMPFlowStatus.handled) {
            return r;
        }
        long request = msg.getId(0);
        Map<String, Object> options = msg.getDict(1);
        String procedure = msg.getUri(2);
        List args = (msg.getData().length > 3) ? msg.getList(3) : null;
        Map<String, Object> argsKw = (msg.getData().length > 4) ? msg.getDict(4) : null;

        if (!validateProcedure(session, procedure)) {
            session.send(WAMPMessage.error(WAMPMessageType.T_CALL, request, WAMPTools.EMPTY_DICT, WAMPConstantsBase.InvalidURI));
            r = WAMPMessagesFlow.WAMPFlowStatus.failed;
        }

        if (r == WAMPMessagesFlow.WAMPFlowStatus.handled) {
            DealerProcedure proc = registrations.onCall(session, msg); // procedures.get(registeredId);

            if (proc == null) {
                session.send(WAMPMessage.error(WAMPMessageType.T_CALL, request, WAMPTools.EMPTY_DICT, WAMPConstantsBase.NoSuchProcedure));
                Map<String, WAMPCallStatistics> nfcs = getNotFoundCalls();
                synchronized (nfcs) {
                    WAMPCallStatistics cs = nfcs.get(procedure);
                    if (cs == null) {
                        cs = getStatisticsForNotFound(procedure, true);
                    }
                    if (cs != null) {
                        cs.onCall();
                        cs.onError();
                    }
                }
                r = WAMPMessagesFlow.WAMPFlowStatus.failed;
            } else if (proc instanceof DealerLocalProcedure) {
                DealerLocalProcedure lproc = (DealerLocalProcedure) proc;
                if (lproc.doResult(session, msg)) {
                    // OK, just for debug...
                    r = WAMPMessagesFlow.WAMPFlowStatus.handled;
                }
            } else {
                DealerCall call = null;
                if (proc instanceof DealerMultiProcedure) {
                    if (session.supportsFeature(WAMPFeature.call_canceling)) {
                        if (options.containsKey(RPC_PROGRESSIVE_CALL_REQUEST_KEY) && (Boolean) options.get(RPC_PROGRESSIVE_CALL_REQUEST_KEY) && session.supportsFeature(WAMPFeature.progressive_call_results)) {
                            call = new DealerCall(proc, options);
                        } else {
                            throw new WAMPException("Cannot perform multi-RPC call if partial call response is not requested/supported.");
                        }
                    } else {
                        throw new WAMPException("Cannot perform multi-RPC call if call cancelation is not supported.");
                    }
                } else {
                    call = new DealerCall(proc, options);
                }
                call.session = session;
                call.request = request;
                call.args = args;
                call.argsKw = argsKw;
                call.cancelable = session.supportsFeature(WAMPFeature.call_canceling);// && call.session.supportsFeature(WAMPFeature.call_canceling);

                // TODO: find standard!!! to ensure procedure name is passed if does not match exactly...
                if (proc != null && !proc.getName().equals(procedure)) {
                    call.details.put(RPC_INVOCATION_PROCEDURE_EXACT_KEY, procedure);
                }

                // enable progressive call results if configured
                if (options.containsKey(RPC_PROGRESSIVE_CALL_REQUEST_KEY) && (Boolean) options.get(RPC_PROGRESSIVE_CALL_REQUEST_KEY) && session.supportsFeature(WAMPFeature.progressive_call_results)) {
                    call.details.put(RPC_PROGRESSIVE_CALL_REQUEST_KEY, true);
                }

                // propagate timeout info
                if (call.hasTimeout()) {
                    call.details.put(RPC_CALL_TIMEOUT, call.getTimesout());
                }

                doInvoke(call);
            }
        }
        return r;
    }

    public void doInvoke(DealerCall call) throws WAMPException {
        for (int i = 0; i < call.getProceduresCount(); i++) {
            if (!WAMP_DT.id.validate(call.getInvocationId(i))) {
                call.setInvocationId(i, call.getProcedure(i).session.getNextRequestId());
            }
            synchronized (call) {
                doInvoke(call, call.getProcedure(i), call.getInvocationId(i));
            }
        }
    }

    public void doInvoke(DealerCall call, DealerProcedure proc, long invocationId) throws WAMPException {
        Map<String, Object> details = call.details;
        List args = call.args;
        Map<String, Object> argsKw = call.argsKw;
        synchronized (calls) {
            Map<Long, Call> sessionCalls = calls.get(proc.session.getId());
            if (sessionCalls == null) {
                sessionCalls = WAMPTools.createSynchronizedMap();
                calls.put(proc.session.getId(), sessionCalls);
            }
            sessionCalls.put(invocationId, call);
            // add support for cancelling, if allowed: session.id -> request -> call
            if (call.cancelable) {
                sessionCalls = calls.get(call.session.getId());
                if (sessionCalls == null) {
                    sessionCalls = WAMPTools.createSynchronizedMap();
                    calls.put(call.session.getId(), sessionCalls);
                }
                sessionCalls.put(call.request, call);
            }
        }
        if (argsKw != null) {
            proc.session.send(WAMPMessage.invocation(invocationId, proc.getId(), details, args, argsKw));
        } else if (args != null) {
            proc.session.send(WAMPMessage.invocation(invocationId, proc.getId(), details, args));
        } else {
            proc.session.send(WAMPMessage.invocation(invocationId, proc.getId(), details));
        }
        call.activeCalls.incrementAndGet();
    }

    /**
     * Cancel or mark as cancelled existing call
     *
     * @param session
     * @param msg
     * @return
     * @throws WAMPException
     */
    @Override
    public WAMPMessagesFlow.WAMPFlowStatus cancel(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        WAMPMessagesFlow.WAMPFlowStatus r = validateSession(session);
        if (r != WAMPMessagesFlow.WAMPFlowStatus.handled) {
            return r;
        }
        long request = msg.getId(0);
        Map<String, Object> options = msg.getDict(1);
        String mode = (String) options.get(RPC_CANCEL_OPT_MODE_KEY);

        if (r == WAMPMessagesFlow.WAMPFlowStatus.handled) {
            Map<Long, Call> sessionCalls = calls.get(session.getId());
            DealerCall call = (DealerCall) sessionCalls.remove(request);
            if (call != null) {
                call.interrupted = mode;
                if (call.getStatistics() != null) {
                    call.getStatistics().onCancel();
                }
                if (RPC_CANCEL_OPT_MODE_SKIP.equals(mode) || (mode != null && !call.proc.session.supportsFeature(WAMPFeature.call_canceling))) {
                    session.send(WAMPMessage.error(WAMPMessageType.T_CALL, call.request, WAMPTools.EMPTY_DICT, "call.canceled.skip"));
                    call.completed(request);
                } else if (RPC_CANCEL_OPT_MODE_KILLNOWAIT.equals(mode)) {
                    for (int i = 0; i < call.getProceduresCount(); i++) {
                        if (call.getInvocationId(i) > 0) {
                            call.getProcedure(i).session.send(WAMPMessage.interrupt(call.getInvocationId(i), options));
                        }
                    }
                    session.send(WAMPMessage.error(WAMPMessageType.T_CALL, call.request, WAMPTools.EMPTY_DICT, "call.canceled.kill"));
                } else if (RPC_CANCEL_OPT_MODE_KILL.equals(mode)) {
                    for (int i = 0; i < call.getProceduresCount(); i++) {
                        if (call.getInvocationId(i) > 0) {
                            call.getProcedure(i).session.send(WAMPMessage.interrupt(call.getInvocationId(i), options));
                        }
                    }
                } else {
                    // unsupported CANCEL mode???
                    r = WAMPMessagesFlow.WAMPFlowStatus.failed;
                }
            } else {
                r = WAMPMessagesFlow.WAMPFlowStatus.failed;
            }
        }

        return r;
    }

    @Override
    public WAMPMessagesFlow.WAMPFlowStatus yield(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        WAMPMessagesFlow.WAMPFlowStatus r = validateSession(session);
        if (r != WAMPMessagesFlow.WAMPFlowStatus.handled) {
            return r;
        }
        long request = msg.getId(0);
        Map<String, Object> options = msg.getDict(1);
        List args = (msg.getData().length > 2) ? msg.getList(2) : null;
        Map<String, Object> argsKw = (msg.getData().length > 3) ? msg.getDict(3) : null;
        Map<String, Object> details = WAMPTools.createDict(null);
        boolean partial = options.containsKey(RPC_PROGRESSIVE_CALL_PROGRESS_KEY) && Boolean.TRUE.equals(options.get(RPC_PROGRESSIVE_CALL_PROGRESS_KEY));
        synchronized (calls) {
            Map<Long, Call> sessionCalls = calls.get(session.getId());
            DealerCall call = (DealerCall) (partial ? sessionCalls.get(request) : sessionCalls.remove(request));
            if (call != null) {
                WAMPMessageType pending = session.getPending(request, true);
                if (call.interrupted == null || RPC_CANCEL_OPT_MODE_KILL.equals(call.interrupted)) {
                    if (!partial) {
                        // this is last response for multi-call item -> close it and check if other open items are present -> may be not last response...
                        if (call.completed(request) >= 0 && call.activeCalls.get() > 0) {
                            partial = true;
                        }
                    }
                    if (partial) {
                        details.put(RPC_PROGRESSIVE_CALL_PROGRESS_KEY, true);
                    }
                    if (argsKw != null) {
                        call.session.send(WAMPMessage.result(call.request, details, args, argsKw));
                    } else if (args != null) {
                        call.session.send(WAMPMessage.result(call.request, details, args));
                    } else {
                        call.session.send(WAMPMessage.result(call.request, details));
                    }
                }
                if (call.getStatistics() != null) {
                    call.getStatistics().onDuration(call.durationNano());
                }
            } else {
                // TODO: error ? exception ?
                r = WAMPMessagesFlow.WAMPFlowStatus.failed;
            }
        }
        return r;
    }

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////// errors
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public WAMPMessagesFlow.WAMPFlowStatus error(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        WAMPMessagesFlow.WAMPFlowStatus r = validateSession(session);
        if (r != WAMPMessagesFlow.WAMPFlowStatus.handled) {
            return r;
        }
        long op = msg.getId(0);
        long req = msg.getId(1);
        Map<String, Object> details = msg.getDict(2);
        String error = msg.getUri(3);
        List args = (msg.getData().length > 4) ? msg.getList(4) : null;
        Map<String, Object> argsKw = (msg.getData().length > 5) ? msg.getDict(5) : null;
        switch ((int) op) {
            case WAMPMessageType.T_INVOCATION:
                Map<Long, Call> sessionCalls = calls.get(session.getId());
                DealerCall call = (sessionCalls != null) ? (DealerCall) sessionCalls.get(req) : null;
                if (call != null) {
                    if (call.activeCalls.get() == 1) {
                        sessionCalls.remove(req);
                    }
                    if (call.interrupted == null || RPC_CANCEL_OPT_MODE_KILL.equals(call.interrupted)) {
                        if (!call.callerErrorSent) {
                            call.callerErrorSent = true;
                            if (argsKw != null) {
                                call.session.send(WAMPMessage.error(WAMPMessageType.T_CALL, call.request, WAMPTools.EMPTY_DICT, error, args, argsKw));
                            } else if (args != null) {
                                call.session.send(WAMPMessage.error(WAMPMessageType.T_CALL, call.request, WAMPTools.EMPTY_DICT, error, args));
                            } else {
                                call.session.send(WAMPMessage.error(WAMPMessageType.T_CALL, call.request, WAMPTools.EMPTY_DICT, error));
                            }
                        }

                        {
                            int procIdx = call.completed(req);
                            if (procIdx != -1 && call.activeCalls.decrementAndGet() > 0) {
                                for (int i = 0; i < call.getProceduresCount(); i++) {
                                    if (i == procIdx) {
                                        continue;
                                    }
                                    if (call.getInvocationId(i) == 0) {
                                        continue;
                                    }
                                    DealerProcedure proc = call.getProcedure(i);
                                    proc.session.send(
                                            WAMPMessage.interrupt(call.getInvocationId(i), WAMPTools.EMPTY_DICT)
                                    );
                                }
                            }
                        }
                    }
                    if (call.getStatistics() != null) {
                        call.getStatistics().onError();
                    }
                } else {
                    // TODO: error ? exception ?
                    r = WAMPMessagesFlow.WAMPFlowStatus.ignored;
                }
                break;
            default:
                r = WAMPMessagesFlow.WAMPFlowStatus.ignored;
        }
        return r;
    }

    public WAMPMessagesFlow.WAMPFlowStatus validateSession(WAMPSession session) {
        boolean isBroker = session.hasLocalRole(WAMP.Role.dealer);
        if (!isBroker) {
            return WAMPMessagesFlow.WAMPFlowStatus.ignored;
        }
        if (session == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        return WAMPMessagesFlow.WAMPFlowStatus.handled;
    }

    public boolean validateProcedure(WAMPSession session, String topic) {
        return topic != null && WAMP_DT.uri.validate(topic);
    }

    @Override
    public <T extends WAMPActor> T init(WAMPRealm realm) {
        if (realm.getStatistics() != null && realm.getStatistics().getCallStatistics() != null) {
            setStatistics(realm.getStatistics().getCallStatistics().createChild(null, "dealer"));
        }
        return super.init(realm);
    }

    @Override
    public void setStatistics(WAMPCallStatistics statistics) {
        super.setStatistics(statistics);
        registrations.statistics = statistics;
    }

    @Override
    public <T extends WAMPActor> T done(WAMPSession... sessions) {
        registrations.close(sessions);
        if (sessions != null) {
            for (WAMPSession session : sessions) {
                if (session != null) {
                    calls.remove(session.getId());
                }
            }
        }
        return super.done(sessions);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append("{");
        sb.append("\n  features=" + this.getFeatures());
        sb.append("\n  calls[" + calls.size() + "]=" + calls.keySet());
        sb.append("\n  meta=");
        sb.append(registrations.toString().replace("\n", "\n  "));
        if (!getNotFoundCalls().isEmpty()) {
            sb.append(notFoundCallsInfo());
            sb.append("\n");
        }
        sb.append('}');
        return sb.toString();
    }

}
