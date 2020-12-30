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
package ssg.lib.wamp.events;

import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.flows.WAMPMessagesFlow.WAMPFlowStatus;
import ssg.lib.wamp.messages.WAMPMessage;

/**
 *
 * @author 000ssg
 */
public interface WAMPSubscriber {

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////// actions
    ////////////////////////////////////////////////////////////////////////////
    void subscribe(WAMPSession session, WAMPEventListener wel) throws WAMPException;

    void unsubscribe(WAMPSession session, long subscriptionId) throws WAMPException;

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////// re-actions
    ////////////////////////////////////////////////////////////////////////////
    WAMPFlowStatus subscribed(WAMPSession session, WAMPMessage msg) throws WAMPException;

    WAMPFlowStatus unsubscribed(WAMPSession session, WAMPMessage msg) throws WAMPException;

    WAMPFlowStatus event(WAMPSession session, WAMPMessage msg) throws WAMPException;

    WAMPFlowStatus error(WAMPSession session, WAMPMessage msg) throws WAMPException;

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////// utilities
    ////////////////////////////////////////////////////////////////////////////
    String getTopic(WAMPSession session, WAMPMessage msg);
}
