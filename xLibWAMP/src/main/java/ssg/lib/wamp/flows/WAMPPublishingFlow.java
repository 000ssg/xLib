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
import ssg.lib.wamp.events.WAMPBroker;
import ssg.lib.wamp.events.WAMPPublisher;
import ssg.lib.wamp.events.WAMPSubscriber;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMPMessageType;
import static ssg.lib.wamp.messages.WAMPMessageType.T_ERROR;
import static ssg.lib.wamp.messages.WAMPMessageType.T_EVENT;
import static ssg.lib.wamp.messages.WAMPMessageType.T_PUBLISH;
import static ssg.lib.wamp.messages.WAMPMessageType.T_PUBLISHED;
import static ssg.lib.wamp.messages.WAMPMessageType.T_SUBSCRIBE;
import static ssg.lib.wamp.messages.WAMPMessageType.T_SUBSCRIBED;
import static ssg.lib.wamp.messages.WAMPMessageType.T_UNSUBSCRIBE;
import static ssg.lib.wamp.messages.WAMPMessageType.T_UNSUBSCRIBED;
import ssg.lib.wamp.messages.WAMP_DT;

/**
 *
 * @author 000ssg
 */
public class WAMPPublishingFlow implements WAMPMessagesFlow {

    // per-role incoming message types sorted by ID!
    static int[] typesBroker = new int[]{T_PUBLISH, T_SUBSCRIBE, T_UNSUBSCRIBE};
    static int[] typesPublisher = new int[]{T_ERROR, T_PUBLISHED};
    static int[] typesSubscriber = new int[]{T_ERROR, T_SUBSCRIBED, T_UNSUBSCRIBED, T_EVENT};

    public WAMPPublishingFlow() {
    }

    @Override
    public boolean canHandle(WAMPSession session, WAMPMessage msg) {
        int type = msg.getType().getId();

        boolean r = false;

        if (session.hasLocalRole(WAMP.Role.broker)) {
            r = Arrays.binarySearch(typesBroker, type) >= 0;
        }
        if (!r && session.hasLocalRole(WAMP.Role.publisher)) {
            r = Arrays.binarySearch(typesPublisher, type) >= 0;
        }
        if (!r && session.hasLocalRole(WAMP.Role.subscriber)) {
            r = Arrays.binarySearch(typesSubscriber, type) >= 0;
        }

        return r;
    }

    @Override
    public WAMPFlowStatus handle(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPFlowStatus.failed;
        }
        WAMPMessageType type = msg.getType();
        boolean isRouter = session.hasLocalRole(WAMP.Role.router);

        switch (type.getId()) {
            case T_SUBSCRIBE:
                if (isRouter) {
                    String topic = msg.getUri(2);
                    if (!validateTopic(session, topic)) {
                        return WAMPFlowStatus.failed;
                    }
                    return ((WAMPBroker) session.getRealm().getActor(WAMP.Role.broker)).subscribe(session, msg);
                } else {
                    return WAMPFlowStatus.failed;
                }
            case T_SUBSCRIBED:
                if (!isRouter) {
                    long request = msg.getId(0);
                    WAMPMessageType rmt = session.getPending(request, false);
                    if (rmt != null && rmt.getId() == WAMPMessageType.T_SUBSCRIBE) {
                        session.getPending(request, true);
                        return ((WAMPSubscriber) session.getRealm().getActor(WAMP.Role.subscriber)).subscribed(session, msg);
                    } else {
                        return WAMPFlowStatus.failed;
                    }
                } else {
                    return WAMPFlowStatus.failed;
                }
            case T_UNSUBSCRIBE:
                if (isRouter) {
                    return ((WAMPBroker) session.getRealm().getActor(WAMP.Role.broker)).unsubscribe(session, msg);
                } else {
                    return WAMPFlowStatus.failed;
                }
            case T_UNSUBSCRIBED:
                if (!isRouter) {
                    long request = msg.getId(0);
                    WAMPMessageType rmt = session.getPending(request, false);
                    if (rmt != null && rmt.getId() == WAMPMessageType.T_UNSUBSCRIBE) {
                        session.getPending(request, true);
                        return ((WAMPSubscriber) session.getRealm().getActor(WAMP.Role.subscriber)).unsubscribed(session, msg);
                    } else {
                        return WAMPFlowStatus.failed;
                    }
                } else {
                    return WAMPFlowStatus.failed;
                }
            case T_PUBLISH:
                if (isRouter) {
                    return ((WAMPBroker) session.getRealm().getActor(WAMP.Role.broker)).publish(session, msg);
                } else {
                    return WAMPFlowStatus.failed;
                }
            case T_PUBLISHED:
                if (!isRouter) {
                    long request = msg.getId(0);
                    WAMPMessageType rmt = session.getPending(request, false);
                    if (rmt != null && rmt.getId() == WAMPMessageType.T_PUBLISH) {
                        session.getPending(request, true);
                        return ((WAMPPublisher) session.getRealm().getActor(WAMP.Role.publisher)).published(session, msg);
                    } else {
                        return WAMPFlowStatus.failed;
                    }
                } else {
                    return WAMPFlowStatus.failed;
                }
            case T_EVENT:
                if (!isRouter) {
                    return ((WAMPSubscriber) session.getRealm().getActor(WAMP.Role.subscriber)).event(session, msg);
                } else {
                    return WAMPFlowStatus.failed;
                }
            case T_ERROR:
                if (!isRouter) {
                    long op = msg.getId(0);
                    long req = msg.getId(1);
                    WAMPMessageType rmt = session.getPending(req, false);
                    if (rmt == null || rmt.getId() != op) {
                        return WAMPFlowStatus.ignored;
                    }
                    switch ((int) op) {
                        case T_SUBSCRIBE:
                        case T_UNSUBSCRIBE:
                            session.getPending(req, true);
                            return ((WAMPSubscriber) session.getRealm().getActor(WAMP.Role.subscriber)).error(session, msg);
                        case T_PUBLISH:
                            session.getPending(req, true);
                            return ((WAMPPublisher) session.getRealm().getActor(WAMP.Role.publisher)).error(session, msg);
                        default:
                            return WAMPFlowStatus.ignored;
                    }
                } else {
                    return WAMPFlowStatus.failed;
                }
        }

        return WAMPFlowStatus.ignored;
    }

    public boolean validateTopic(WAMPSession session, String topic) {
        return topic != null && WAMP_DT.uri.validate(topic);
    }
}
