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
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMPMessageType;
import ssg.lib.wamp.messages.WAMP_DT;
import ssg.lib.wamp.WAMPConstantsBase;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPRealm;
import ssg.lib.wamp.messages.WAMPMessageTypeAdvanced;
import static ssg.lib.wamp.messages.WAMPMessageTypeAdvanced.RPC_CANCEL_OPT_MODE_KILL;
import static ssg.lib.wamp.messages.WAMPMessageTypeAdvanced.RPC_CANCEL_OPT_MODE_KILLNOWAIT;
import static ssg.lib.wamp.messages.WAMPMessageTypeAdvanced.RPC_CANCEL_OPT_MODE_SKIP;
import ssg.lib.wamp.rpc.WAMPDealer;
import ssg.lib.wamp.rpc.impl.Call;
import ssg.lib.wamp.rpc.impl.WAMPRPC;
import ssg.lib.wamp.rpc.impl.dealer.WAMPRPCRegistrations.RPCMeta;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.wamp.stat.WAMPCallStatistics;

/**
 *
 * @author 000ssg
 */
public class WAMPRPCDealer extends WAMPRPC implements WAMPDealer {

    // session id -> (invocationId -> call)
    Map<Long, Map<Long, Call>> calls = WAMPTools.createSynchronizedMap();

    // Registrations registrations
    WAMPRPCRegistrations registrations = new WAMPRPCRegistrations();

    public WAMPRPCDealer() {
        getFeatures().add(WAMPFeature.registration_meta_api);
    }

    public WAMPRPCDealer(WAMPFeature... features) {
        super(new Role[]{Role.dealer}, features);
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
        //Long registeredId = registrations.name2procedureId.get(procedure);
        if (!validateProcedure(session, procedure)) {
            session.send(WAMPMessage.error(WAMPMessageType.T_CALL, request, WAMPTools.EMPTY_DICT, WAMPConstantsBase.InvalidURI));
            r = WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
//        if (registeredId == null) {
//            session.send(WAMPMessage.error(WAMPMessageType.T_CALL, request, new HashMap(), WAMPConstantsBase.NoSuchProcedure));
//            r = WAMPMessagesFlow.WAMPFlowStatus.failed;
//        }

        if (r == WAMPMessagesFlow.WAMPFlowStatus.handled) {
            DealerProcedure proc = registrations.onCall(session, msg); // procedures.get(registeredId);

            if (proc == null) {
                session.send(WAMPMessage.error(WAMPMessageType.T_CALL, request, WAMPTools.EMPTY_DICT, WAMPConstantsBase.NoSuchProcedure));
                Map<String, WAMPCallStatistics> nfcs = getNotFoundCalls();
                synchronized (nfcs) {
                    WAMPCallStatistics cs = nfcs.get(procedure);
                    if (cs == null) {
                        cs = getStatisticsForNotFound(procedure, true);
                        //nfcs.put(procedure, cs);
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
                WAMPCallStatistics cs = (proc.owner instanceof RPCMeta)
                        ? ((RPCMeta) proc.owner).getStatistics(procedure)
                        : proc.getStatistics();
                DealerCall call = new DealerCall(proc, null);//cs);
                call.session = session;
                call.request = request;
                call.options = options;
                call.args = args;
                call.argsKw = argsKw;
                call.cancelable = session.supportsFeature(WAMPFeature.call_canceling) && call.session.supportsFeature(WAMPFeature.call_canceling);

                call.invocationId = proc.session.getNextRequestId();

                // TODO: find standard!!! to ensure procedure name is passed if doe not match exactly...
                if (proc != null && !proc.getName().equals(procedure)) {
                    call.details.put("procedure", procedure);
                }

                synchronized (calls) {
                    Map<Long, Call> sessionCalls = calls.get(proc.session.getId());
                    if (sessionCalls == null) {
                        sessionCalls = WAMPTools.createSynchronizedMap();
                        calls.put(proc.session.getId(), sessionCalls);
                    }
                    sessionCalls.put(call.invocationId, call);
                    // add support for cancelling, if allowed: session.id -> request -> call
                    if (call.cancelable) {
                        sessionCalls = calls.get(session.getId());
                        if (sessionCalls == null) {
                            sessionCalls = WAMPTools.createSynchronizedMap();
                            calls.put(session.getId(), sessionCalls);
                        }
                        sessionCalls.put(call.request, call);
                    }
                }
                if (argsKw != null) {
                    proc.session.send(WAMPMessage.invocation(call.invocationId, proc.getId(), call.details, args, argsKw));
                } else if (args != null) {
                    proc.session.send(WAMPMessage.invocation(call.invocationId, proc.getId(), call.details, args));
                } else {
                    proc.session.send(WAMPMessage.invocation(call.invocationId, proc.getId(), call.details));
                }
            }
        }
        return r;
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
        String mode = (String) options.get("mode");

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
                } else if (RPC_CANCEL_OPT_MODE_KILLNOWAIT.equals(mode)) {
                    session.send(new WAMPMessage(WAMPMessageTypeAdvanced.INTERRUPT, call.invocationId, options));
                    session.send(WAMPMessage.error(WAMPMessageType.T_CALL, call.request, WAMPTools.EMPTY_DICT, "call.canceled.kill"));
                } else if (RPC_CANCEL_OPT_MODE_KILL.equals(mode)) {
                    session.send(new WAMPMessage(WAMPMessageTypeAdvanced.INTERRUPT, call.invocationId, options));
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
        synchronized (calls) {
            Map<Long, Call> sessionCalls = calls.get(session.getId());
            DealerCall call = (DealerCall) sessionCalls.remove(request);
            if (call != null) {
                WAMPMessageType pending = session.getPending(request, true);
                if (call.interrupted == null || RPC_CANCEL_OPT_MODE_KILL.equals(call.interrupted)) {
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
                DealerCall call = (sessionCalls != null) ? (DealerCall) sessionCalls.remove(req) : null;
                if (call != null) {
                    WAMPMessageType pending = session.getPending(req, true);

                    if (call.interrupted == null || RPC_CANCEL_OPT_MODE_KILL.equals(call.interrupted)) {
                        if (argsKw != null) {
                            call.session.send(WAMPMessage.error(WAMPMessageType.T_CALL, call.request, WAMPTools.EMPTY_DICT, error, args, argsKw));
                        } else if (args != null) {
                            call.session.send(WAMPMessage.error(WAMPMessageType.T_CALL, call.request, WAMPTools.EMPTY_DICT, error, args));
                        } else {
                            call.session.send(WAMPMessage.error(WAMPMessageType.T_CALL, call.request, WAMPTools.EMPTY_DICT, error));
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
        if (realm.getStatistics() != null && realm.getStatistics().getCallStatistics()!=null) {
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
