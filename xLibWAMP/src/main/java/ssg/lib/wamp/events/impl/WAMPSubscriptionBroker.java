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
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import ssg.lib.wamp.WAMP;
import ssg.lib.wamp.WAMP.Role;
import ssg.lib.wamp.WAMPActor;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.WAMPFeature;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.WAMPSessionState;
import ssg.lib.wamp.events.WAMPBroker;
import ssg.lib.wamp.flows.WAMPMessagesFlow;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMPMessageType;
import ssg.lib.wamp.messages.WAMP_DT;
import ssg.lib.wamp.util.WAMPTools;

/**
 *
 * @author 000ssg
 */
public class WAMPSubscriptionBroker extends WAMPSubscription implements WAMPBroker {

    AtomicLong nextSubscriptionId = new AtomicLong(1);
    AtomicLong nextPublicationId = new AtomicLong(1);
    Map<WAMPSession, Map<String, Object>> subscribers = WAMPTools.createSynchronizedMap();

    public WAMPSubscriptionBroker() {
    }

    public WAMPSubscriptionBroker(WAMPFeature... features) {
        super(new Role[]{Role.broker}, features);
    }

    @Override
    public WAMPMessagesFlow.WAMPFlowStatus subscribe(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        WAMPMessagesFlow.WAMPFlowStatus r = validateSession(session);
        if (r != WAMPMessagesFlow.WAMPFlowStatus.handled) {
            return r;
        }
        long request = msg.getId(0);
        Map<String, Object> options = msg.getDict(1);
        String topic = msg.getUri(2);
        if (!validateTopic(session, topic)) {
            r = WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        if (r == WAMPMessagesFlow.WAMPFlowStatus.handled) {
            BrokerSubscription sub = new BrokerSubscription();
            sub.topic = topic;
            sub.options = options;
            sub.session = session;
            sub.id = nextSubscriptionId.getAndIncrement();
            subscriptions.put(sub.id, sub);
            List<Subscription> subs = topics.get(topic);
            if (subs == null) {
                subs = WAMPTools.createSynchronizedList();
                topics.put(topic, subs);
            }
            subs.add(sub);
            session.send(WAMPMessage.subscribed(request, sub.id));
        }
        return r;
    }

    @Override
    public WAMPMessagesFlow.WAMPFlowStatus unsubscribe(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        WAMPMessagesFlow.WAMPFlowStatus r = validateSession(session);
        if (r != WAMPMessagesFlow.WAMPFlowStatus.handled) {
            return r;
        }
        long request = msg.getId(0);
        long subscriptionId = msg.getId(1);
        synchronized (subscriptions) {
            BrokerSubscription sub = (BrokerSubscription) subscriptions.remove(subscriptionId);
            if (sub != null) {
                subscriptions.remove(request);
                List<Subscription> subs = topics.get(sub.topic);
                if (subs != null) {
                    synchronized (subs) {
                        subs.remove(sub);
                    }
                }
                session.send(WAMPMessage.unsubscribed(request));
            } else {
                session.send(WAMPMessage.error(WAMPMessageType.T_UNSUBSCRIBE, request, WAMPTools.EMPTY_DICT, "not.subscribed"));
            }
        }
        return r;
    }

    @Override
    public WAMPMessagesFlow.WAMPFlowStatus publish(WAMPSession session, WAMPMessage msg) throws WAMPException {
        if (msg == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        WAMPMessagesFlow.WAMPFlowStatus r = validateSession(session);
        if (r != WAMPMessagesFlow.WAMPFlowStatus.handled) {
            return r;
        }
        long request = msg.getId(0);
        Map<String, Object> options = msg.getDict(1);
        boolean needResponse = Boolean.TRUE.equals(options.get("acknowledge"));
        String topic = msg.getUri(2);
        String error = null;
        if (!validateTopic(session, topic)) {
            r = WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        if (r == WAMPMessagesFlow.WAMPFlowStatus.handled) {
            List args = (msg.getData().length > 3) ? msg.getList(3) : null;
            Map<String, Object> argsKw = (msg.getData().length > 4) ? msg.getDict(4) : null;
            long publicationId = nextPublicationId.getAndIncrement();
            this.doEvent(session, (needResponse) ? request : 0, topic, publicationId, WAMPTools.EMPTY_DICT, args, argsKw);
        } else {
            if (needResponse) {
                session.send(WAMPMessage.error(WAMPMessageType.T_PUBLISH, request, WAMPTools.EMPTY_DICT, error));
            }
        }
        return r;
    }

    public void doEvent(
            WAMPSession session,
            long request,
            String topic,
            long publicationId,
            Map<String, Object> details,
            List args,
            Map<String, Object> argsKw
    ) throws WAMPException {
        List<Subscription> subs = topics.get(topic);
        if (subs == null) {
            int a = 0;
        } else {
            for (Subscription sub : subs.toArray(new Subscription[subs.size()])) {
                BrokerSubscription bsub = (BrokerSubscription) sub;
                if (bsub.session.getState() == WAMPSessionState.established) {
                    if (argsKw != null) {
                        bsub.session.send(WAMPMessage.event(sub.id, publicationId, details, args, argsKw));
                    } else if (args != null) {
                        bsub.session.send(WAMPMessage.event(sub.id, publicationId, details, args));
                    } else {
                        bsub.session.send(WAMPMessage.event(sub.id, publicationId, details));
                    }
                } else {
                    // clean-up?
                    int a = 0;
                }
            }
            if (session != null && WAMP_DT.id.validate(request)) {
                session.send(WAMPMessage.published(request, publicationId));
            }
        }
    }

    public WAMPMessagesFlow.WAMPFlowStatus validateSession(WAMPSession session) {
        boolean isBroker = session.hasLocalRole(WAMP.Role.broker);
        if (!isBroker) {
            return WAMPMessagesFlow.WAMPFlowStatus.ignored;
        }
        if (session == null) {
            return WAMPMessagesFlow.WAMPFlowStatus.failed;
        }
        return WAMPMessagesFlow.WAMPFlowStatus.handled;
    }

    public boolean validateTopic(WAMPSession session, String topic) {
        return topic != null && WAMP_DT.uri.validate(topic);
    }

    @Override
    public <T extends WAMPActor> T done(WAMPSession... sessions) {
        if (sessions != null) {
            for (WAMPSession session : sessions) {
                if (subscriptions.containsKey(session)) {
                    subscriptions.remove(session);
                }
            }
        }
        return super.done(sessions);
    }

    @Override
    public String toStringBlockExt() {
        if (!subscribers.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            {
                // add inherited text if present
                String s = super.toStringBlockExt();
                if (s != null) {
                    sb.append(s);
                }
            }

            sb.append("\n  Subscribers[" + subscribers.size() + "]");
            try {
                Object[] oos = null;
                synchronized (subscribers) {
                    oos = subscribers.entrySet().toArray();
                }
                for (Object oo : oos) {
                    Entry<WAMPSession, Map<String, Object>> ee = (Entry<WAMPSession, Map<String, Object>>) oo;
                    Map<String, Object> s = ee.getValue();
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
        return ", subscribers=" + subscribers.size() + ", nextPublicationId=" + nextPublicationId.get() + ", nextSubscriptionId=" + nextSubscriptionId;
    }

}
