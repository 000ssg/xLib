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
package ssg.lib.wamp.flows;

import java.util.Arrays;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMPMessageType;
import static ssg.lib.wamp.messages.WAMPMessageType.T_CALL;
import static ssg.lib.wamp.messages.WAMPMessageType.T_ERROR;
import static ssg.lib.wamp.messages.WAMPMessageType.T_INVOCATION;
import static ssg.lib.wamp.messages.WAMPMessageType.T_REGISTER;
import static ssg.lib.wamp.messages.WAMPMessageType.T_REGISTERED;
import static ssg.lib.wamp.messages.WAMPMessageType.T_RESULT;
import static ssg.lib.wamp.messages.WAMPMessageType.T_UNREGISTER;
import static ssg.lib.wamp.messages.WAMPMessageType.T_UNREGISTERED;
import static ssg.lib.wamp.messages.WAMPMessageType.T_YIELD;
import static ssg.lib.wamp.messages.WAMPMessageTypeAdvanced.T_CANCEL;
import static ssg.lib.wamp.messages.WAMPMessageTypeAdvanced.T_INTERRUPT;
import ssg.lib.wamp.messages.WAMP_DT;
import ssg.lib.wamp.rpc.WAMPCallee;
import ssg.lib.wamp.rpc.WAMPCaller;
import ssg.lib.wamp.rpc.WAMPDealer;

/**
 *
 * @author 000ssg
 */
public class WAMPRPCFlow implements WAMPMessagesFlow {

    // per-role incoming message types sorted by ID!
    static int[] typesDealer = new int[]{T_ERROR, T_CALL, T_REGISTER, T_UNREGISTER, T_CANCEL, T_YIELD};
    static int[] typesCaller = new int[]{T_ERROR, T_RESULT};
    static int[] typesCallee = new int[]{T_ERROR, T_REGISTERED, T_UNREGISTERED, T_INVOCATION, T_INTERRUPT};

    @Override
    public boolean canHandle(WAMPSession session, WAMPMessage msg) {
        int type = msg.getType().getId();

        boolean r = false;

        if (session.hasLocalRole(WAMP.Role.dealer)) {
            r = Arrays.binarySearch(typesDealer, type) >= 0;
        }
        if (!r && session.hasLocalRole(WAMP.Role.caller)) {
            r = Arrays.binarySearch(typesCaller, type) >= 0;
        }
        if (!r && session.hasLocalRole(WAMP.Role.callee)) {
            r = Arrays.binarySearch(typesCallee, type) >= 0;
        }

        return r;
    }

    @Override
    public WAMPFlowStatus handle(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPFlowStatus.failed;
        }
        WAMPMessageType type = msg.getType();
        boolean isDealer = session.hasLocalRole(WAMP.Role.dealer);
        boolean isCaller = session.hasLocalRole(WAMP.Role.caller);
        boolean isCallee = session.hasLocalRole(WAMP.Role.callee);

        switch (type.getId()) {
            case T_REGISTER:
                if (isDealer) {
                    String topic = msg.getUri(2);
                    if (!validateProcedure(session, topic)) {
                        return WAMPFlowStatus.failed;
                    }
                    return ((WAMPDealer) session.getRealm().getActor(WAMP.Role.dealer)).register(session, msg);
                } else {
                    return WAMPFlowStatus.failed;
                }
            case T_REGISTERED:
                if (isCallee) {
                    long request = msg.getId(0);
                    WAMPMessageType rmt = session.getPending(request, false);
                    if (rmt != null && rmt.getId() == WAMPMessageType.T_REGISTER) {
                        session.getPending(request, true); // mark as responded...
                        return ((WAMPCallee) session.getRealm().getActor(WAMP.Role.callee)).registered(session, msg);
                    } else {
                        return WAMPFlowStatus.failed;
                    }
                } else {
                    return WAMPFlowStatus.failed;
                }
            case T_UNREGISTER:
                if (isDealer) {
                    return ((WAMPDealer) session.getRealm().getActor(WAMP.Role.dealer)).unregister(session, msg);
                } else {
                    return WAMPFlowStatus.failed;
                }
            case T_UNREGISTERED:
                if (isCallee) {
                    long request = msg.getId(0);
                    WAMPMessageType rmt = session.getPending(request, false);
                    if (rmt != null && rmt.getId() == WAMPMessageType.T_UNREGISTER) {
                        session.getPending(request, true); // mark as responded...
                        return ((WAMPCallee) session.getRealm().getActor(WAMP.Role.callee)).unregistered(session, msg);
                    } else {
                        return WAMPFlowStatus.failed;
                    }
                } else {
                    return WAMPFlowStatus.failed;
                }
            case T_CALL:
                if (isDealer) {
                    return ((WAMPDealer) session.getRealm().getActor(WAMP.Role.dealer)).call(session, msg);
                } else {
                    return WAMPFlowStatus.failed;
                }
            case T_RESULT:
                if (isCaller) {
                    long request = msg.getId(0);
                    WAMPMessageType rmt = session.getPending(request, false);
                    if (rmt != null && rmt.getId() == WAMPMessageType.T_CALL) {
                        return ((WAMPCaller) session.getRealm().getActor(WAMP.Role.caller)).result(session, msg);
                    } else {
                        return WAMPFlowStatus.failed;
                    }
                } else {
                    return WAMPFlowStatus.failed;
                }
            case T_CANCEL:
                if (isDealer) {
                    return ((WAMPDealer) session.getRealm().getActor(WAMP.Role.dealer)).cancel(session, msg);
                } else {
                    return WAMPFlowStatus.failed;
                }
            case T_INVOCATION:
                if (isCallee) {
                    return ((WAMPCallee) session.getRealm().getActor(WAMP.Role.callee)).invocation(session, msg);
                } else {
                    return WAMPFlowStatus.failed;
                }
            case T_INTERRUPT:
                if (isCallee) {
                    return ((WAMPCallee) session.getRealm().getActor(WAMP.Role.callee)).interrupt(session, msg);
                } else {
                    return WAMPFlowStatus.failed;
                }
            case T_YIELD:
                if (isDealer) {
                    return ((WAMPDealer) session.getRealm().getActor(WAMP.Role.dealer)).yield(session, msg);
                } else {
                    return WAMPFlowStatus.failed;
                }
            case T_ERROR:
                WAMPFlowStatus r = WAMPFlowStatus.failed;
                try {
                    if (isDealer) {
                        r = ((WAMPDealer) session.getRealm().getActor(WAMP.Role.dealer)).error(session, msg);
                    } else if (isCallee) {
                        r = ((WAMPCallee) session.getRealm().getActor(WAMP.Role.callee)).error(session, msg);
                    } else if (isCaller) {
                        r = ((WAMPCaller) session.getRealm().getActor(WAMP.Role.caller)).error(session, msg);
                    } else {
                        r = WAMPFlowStatus.failed;
                    }
                    return r;
                } finally {
                    switch (r) {
                        case handled:
                        case failed:
                            // remove from pending to ensure errors do not block messaging...
                            long request = msg.getId(1);
                            session.getPending(request, true);
                            break;
                        case busy:
                            // is it OK to be busy when handling error?
                            break;
                        case ignored:
                            // it is OK, may be other actor will handle it.
                            break;
                    }
                }
        }

        return WAMPFlowStatus.ignored;
    }

    public boolean validateProcedure(WAMPSession session, String topic) {
        return topic != null && WAMP_DT.uri.validate(topic);
    }
}
