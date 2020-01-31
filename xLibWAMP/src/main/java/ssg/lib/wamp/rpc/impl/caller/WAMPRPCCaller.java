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
package ssg.lib.wamp.rpc.impl.caller;

import ssg.lib.wamp.rpc.impl.WAMPRPCListener;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.WAMPConstantsBase;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.flows.WAMPMessagesFlow;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMPMessageType;
import ssg.lib.wamp.messages.WAMPMessageTypeAdvanced;
import static ssg.lib.wamp.messages.WAMPMessageTypeAdvanced.RPC_CANCEL_OPT_MODE_SKIP;
import ssg.lib.wamp.rpc.impl.caller.CallerCall.CallListener;
import ssg.lib.wamp.rpc.WAMPCaller;
import ssg.lib.wamp.rpc.impl.WAMPRPC;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.wamp.stat.WAMPCallStatistics;

/**
 *
 * @author 000ssg
 */
public class WAMPRPCCaller extends WAMPRPC implements WAMPCaller {

    Map<Long, CallerCall> calls = WAMPTools.createSynchronizedMap();
    private int maxConcurrentCalls = 50;
    Map<String, WAMPCallStatistics> callStats = WAMPTools.createSynchronizedMap();

    public WAMPRPCCaller() {
    }

    public WAMPRPCCaller(WAMPFeature... features) {
        super(new Role[]{Role.caller}, features);
    }

    ////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////// actions
    ////////////////////////////////////////////////////////////////////////
    @Override
    public void call(WAMPSession session, WAMPRPCListener caller) throws WAMPException {
        caller.onCall(this.call(session, caller.getOptions(), caller.getProcedure(), caller.getArgs(), caller.getArgsKw(), caller));
    }

    public synchronized long call(WAMPSession session, Map<String, Object> options, String procedure, List args, Map<String, Object> argsKw, CallListener caller) throws WAMPException {
        boolean isRouter = session.hasLocalRole(WAMP.Role.router);
        if (isRouter) {
            throw new WAMPException("Only client can call, not router.");
        }
        if (getMaxConcurrentCalls() > 0 && calls.size() > getMaxConcurrentCalls()) {
            return WAMPRPC.TOO_MANY_CONCURRENT_CALLS;
        }
        if (!session.hasLocalRole(WAMP.Role.caller)) {
            throw new WAMPException("Only client with role 'caller' can call procedure.");
        }
        CallerCall call = new CallerCall();
        call.setId(session.getNextRequestId());
        call.caller = caller;
        call.procedure = procedure;
        WAMPCallStatistics pcs = callStats.get(procedure);
        if (pcs == null) {
            if (getStatistics() == null && session.getStatistics() != null && session.getStatistics().getCallStatistics() != null) {
                setStatistics(session.getStatistics().getCallStatistics());
            }
            pcs = (getStatistics() != null)
                    ? getStatistics().createChild(null, procedure)
                    : null;
            if (pcs != null) {
                callStats.put(procedure, pcs);
            }
        }
        if (pcs != null) {
            call.callStatistics = pcs;
            pcs.onCall();
        }
        calls.put(call.getId(), call);
        if (argsKw != null) {
            session.send(WAMPMessage.call(call.getId(), options, procedure, args, argsKw));
        } else if (args != null) {
            session.send(WAMPMessage.call(call.getId(), options, procedure, args));
        } else {
            session.send(WAMPMessage.call(call.getId(), options, procedure));
        }
        return call.getId();
    }

    @Override
    public synchronized void cancel(WAMPSession session, long callId, String mode) throws WAMPException {
        boolean isRouter = session.hasLocalRole(WAMP.Role.router);
        if (isRouter) {
            throw new WAMPException("Only client can cancel a call, not router.");
        }
        if (!session.hasLocalRole(WAMP.Role.caller)) {
            throw new WAMPException("Only client with role 'caller' can cancel called procedure.");
        }

        CallerCall call = (CallerCall) calls.get(callId);
        if (call == null) {
            throw new WAMPException("No call to cancel: " + callId);
        }
        if (call.callStatistics != null) {
            call.callStatistics.onCancel();
            call.callStatistics.onDuration(call.durationNano());
        }
        if (call.cancelled != null) {
            // ignore subsequent cancels... hopefully will get result (or error) yet...
        } else {
            if (mode == null || !session.supportsFeature(WAMPFeature.call_canceling)) {
                mode = RPC_CANCEL_OPT_MODE_SKIP;
            }
            session.send(new WAMPMessage(WAMPMessageTypeAdvanced.CANCEL, callId, mode));
        }
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////// reactions
    ////////////////////////////////////////////////////////////////////////
    @Override
    public WAMPMessagesFlow.WAMPFlowStatus result(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        WAMPMessagesFlow.WAMPFlowStatus r = validateSession(session, msg);
        if (r != WAMPMessagesFlow.WAMPFlowStatus.handled) {
            return r;
        }
        long request = msg.getId(0);
        Map<String, Object> details = msg.getDict(1);
        Boolean inProgress = (Boolean) details.get("progress");
        if (inProgress == null) {
            inProgress = false;
        }
        List args = (msg.getData().length > 2) ? msg.getList(2) : null;
        Map<String, Object> argsKw = (msg.getData().length > 3) ? msg.getDict(3) : null;
        CallerCall call = (inProgress) ? calls.get(request) : calls.remove(request);
        if (call == null) {
            r = WAMPMessagesFlow.WAMPFlowStatus.ignored;
        }
        if (call.callStatistics != null) {
            call.callStatistics.onDuration(call.durationNano());
        }
        if (WAMPMessagesFlow.WAMPFlowStatus.handled == r) {
            call.caller.onResult(call.getId(), details, args, argsKw);
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
        WAMPMessagesFlow.WAMPFlowStatus r = validateSession(session, msg);
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
            case WAMPMessageType.T_CALL:
                CallerCall call = calls.remove(req);
                if (call != null) {
                    if (call.callStatistics != null) {
                        call.callStatistics.onError();
                        call.callStatistics.onDuration(call.durationNano());
                    }
                    session.onError(msg);
                    call.caller.onError(call.getId(), error, details, args, argsKw);
                    if (WAMPConstantsBase.NoSuchProcedure.equals(error)) {
                        Map<String, WAMPCallStatistics> nfcs = getNotFoundCalls();
                        synchronized (nfcs) {
                            WAMPCallStatistics cs = nfcs.get(call.procedure);
                            if (cs == null) {
                                cs = getStatisticsForNotFound(call.procedure, true);
                                //nfcs.put(call.procedure, cs);
                            }
                            if (cs != null) {
                                cs.onCall();
                                cs.onError();
                            }
                        }
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

    public WAMPMessagesFlow.WAMPFlowStatus validateSession(WAMPSession session, WAMPMessage msg) {
        boolean isCaller = session.hasLocalRole(WAMP.Role.caller);
        if (!isCaller) {
            return WAMPMessagesFlow.WAMPFlowStatus.ignored;
        }
        if (session == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        return WAMPMessagesFlow.WAMPFlowStatus.handled;
    }

    /**
     * @return the maxConcurrentCalls
     */
    public int getMaxConcurrentCalls() {
        return maxConcurrentCalls;
    }

    /**
     * @param maxConcurrentCalls the maxConcurrentCalls to set
     */
    public void setMaxConcurrentCalls(int maxConcurrentCalls) {
        this.maxConcurrentCalls = maxConcurrentCalls;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append("{");
        sb.append("\n  features=" + this.getFeatures());
        sb.append("\n  maxConcurrentCalls=" + maxConcurrentCalls);
        sb.append("\n  calls[" + calls.size() + "]=" + calls.keySet());
        sb.append("\n  call statistics=" + getStatistics());
        try {
            Object[] oos = callStats.entrySet().toArray();
            for (Object oo : oos) {
                Entry<String, WAMPCallStatistics> ee = (Entry<String, WAMPCallStatistics>) oo;
                sb.append("\n    " + ee.getKey());
                sb.append("\t ");
                sb.append(ee.getValue());
            }
        } catch (Throwable th) {
        }
        if (!getNotFoundCalls().isEmpty()) {
            sb.append(notFoundCallsInfo());
            sb.append("\n");
        }
        sb.append('}');
        return sb.toString();

    }

}
