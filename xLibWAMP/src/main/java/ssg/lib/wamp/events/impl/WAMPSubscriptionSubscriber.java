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

import java.util.List;
import java.util.Map;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.events.WAMPEventListener;
import ssg.lib.wamp.events.WAMPSubscriber;
import ssg.lib.wamp.flows.WAMPMessagesFlow;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMPMessageType;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author sesidoro
 */
public class WAMPSubscriptionSubscriber extends WAMPSubscription implements WAMPSubscriber {

    Map<Long, SubscriberSubscription> pending = WAMPTools.createSynchronizedMap(false);

    public WAMPSubscriptionSubscriber() {
    }

    public WAMPSubscriptionSubscriber(WAMPFeature... features) {
        super(new Role[]{Role.subscriber}, features);
    }

    ////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////// actions
    ////////////////////////////////////////////////////////////////////////
    public void subscribe(WAMPSession session, WAMPEventListener wel) throws WAMPException {
        boolean isRouter = session.hasLocalRole(WAMP.Role.router);
        if (isRouter) {
            throw new WAMPException("Only client can subscribe, not router.");
        }
        if (!session.hasLocalRole(WAMP.Role.subscriber)) {
            throw new WAMPException("Only client with role 'subscriber' can subscribe.");
        }
        long request = session.getNextRequestId();
        SubscriberSubscription sub = new SubscriberSubscription(wel.getTopic(), wel.getOptions(), wel);
        pending.put(request, sub);
        session.send(WAMPMessage.subscribe(request, wel.getOptions(), wel.getTopic()));
    }

    public void unsubscribe(WAMPSession session, long subscriptionId) throws WAMPException {
        boolean isRouter = session.hasLocalRole(WAMP.Role.router);
        if (isRouter) {
            throw new WAMPException("Only client can subscribe, not router.");
        }
        if (!session.hasLocalRole(WAMP.Role.subscriber)) {
            throw new WAMPException("Only client with role 'subscriber' can unsubscribe.");
        }
        SubscriberSubscription sub = (SubscriberSubscription) subscriptions.get(subscriptionId);
        if (sub == null) {
            throw new WAMPException("No subscription found with id=" + subscriptionId);
        }
        long request = session.getNextRequestId();
        pending.put(request, sub);
        session.send(WAMPMessage.unsubscribe(request, subscriptionId));
    }

    ////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////// reactions
    ////////////////////////////////////////////////////////////////////////
    public WAMPMessagesFlow.WAMPFlowStatus subscribed(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        WAMPMessagesFlow.WAMPFlowStatus r = validateSession(session, msg);
        if (r != WAMPMessagesFlow.WAMPFlowStatus.handled) {
            return r;
        }
        long request = msg.getId(0);
        long subscriptionId = msg.getId(1);
        SubscriberSubscription sub = pending.remove(request);
        if (sub == null) {
            r = WAMPMessagesFlow.WAMPFlowStatus.ignored;
        }
        if (WAMPMessagesFlow.WAMPFlowStatus.handled == r) {
            sub.id = subscriptionId;
            sub.getWAMPEventListener().onSubscribed(subscriptionId);
            subscriptions.put(sub.id, sub);
            List<Subscription> subs = topics.get(sub.topic);
            if (subs == null) {
                subs = WAMPTools.createSynchronizedList();
                topics.put(sub.topic, subs);
            }
            subs.add(sub);
        }
        return r;
    }

    public WAMPMessagesFlow.WAMPFlowStatus unsubscribed(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        WAMPMessagesFlow.WAMPFlowStatus r = validateSession(session, msg);
        if (r != WAMPMessagesFlow.WAMPFlowStatus.handled) {
            return r;
        }
        long request = msg.getId(0);
        SubscriberSubscription sub = pending.remove(request);
        if (sub == null) {
            r = WAMPMessagesFlow.WAMPFlowStatus.ignored;
        }
        if (WAMPMessagesFlow.WAMPFlowStatus.handled == r) {
            subscriptions.remove(sub.id);
            List<Subscription> subs = topics.get(sub.topic);
            if (subs != null) {
                subs.remove(sub);
            }
        }
        return r;
    }

    public WAMPMessagesFlow.WAMPFlowStatus event(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        WAMPMessagesFlow.WAMPFlowStatus r = validateSession(session, msg);
        if (r != WAMPMessagesFlow.WAMPFlowStatus.handled) {
            return r;
        }

        long subscriptionId = msg.getId(0);
        SubscriberSubscription sub = (SubscriberSubscription) subscriptions.get(subscriptionId);

        if (sub != null) {
            long publicationId = msg.getId(1);
            Map<String, Object> options = msg.getDict(2);
            List args = (msg.getData().length > 3) ? msg.getList(3) : null;
            Map<String, Object> argsKw = (msg.getData().length > 4) ? msg.getDict(4) : null;

            sub.getWAMPEventListener().handler().onEvent(subscriptionId, publicationId, options, args, argsKw);
        } else {
            // TODO: ignore if not subscribed or error?
            //throw new WAMPException("Not subscribed to " + subscriptionId);
            //session.onEvent(msg);
        }
        return r;
    }

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
        SubscriberSubscription sub = pending.remove(req);
        switch ((int) op) {
            case WAMPMessageType.T_SUBSCRIBE:
            case WAMPMessageType.T_UNSUBSCRIBE:
                session.onError(msg);
                break;
            default:
                r = WAMPMessagesFlow.WAMPFlowStatus.ignored;
        }
        return r;
    }

    public WAMPMessagesFlow.WAMPFlowStatus validateSession(WAMPSession session, WAMPMessage msg) {
        boolean isClient = session.hasLocalRole(WAMP.Role.subscriber)
                && (msg.getType().getId() == WAMPMessageType.T_EVENT
                || msg.getType().getId() == WAMPMessageType.T_SUBSCRIBED
                || msg.getType().getId() == WAMPMessageType.T_UNSUBSCRIBED
                || msg.getType().getId() == WAMPMessageType.T_ERROR);
        if (!isClient) {
            return WAMPMessagesFlow.WAMPFlowStatus.ignored;
        }
        if (session == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        return WAMPMessagesFlow.WAMPFlowStatus.handled;
    }

    @Override
    public String toStringBlockExt() {
        if (!pending.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            {
                // add inherited text if present
                String s = super.toStringBlockExt();
                if (s != null) {
                    sb.append(s);
                }
            }

            sb.append("\n  Pending publications[" + pending.size() + "]");
            try {
                Object[] oos = null;
                synchronized (pending) {
                    oos = pending.entrySet().toArray();
                }
                for (Object oo : oos) {
                    Map.Entry<Long, SubscriberSubscription> ee = (Map.Entry<Long, SubscriberSubscription>) oo;
                    SubscriberSubscription s = ee.getValue();
                    sb.append("\n  " + ee.getKey() + ":\t " + s.toString().replace("\n", "\n  "));
                }
            } catch (Throwable th) {
                sb.append("\n  ERR: " + th.toString().replace("\n", "\n    "));
            }

            return (sb.length() > 0) ? sb.toString() : null;
        } else {
            return super.toStringBlockExt();
        }
    }

    @Override
    public String toStringInlineExt() {
        return ", pending=" + pending.size();
    }
}
