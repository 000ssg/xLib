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

import java.util.List;
import java.util.Map;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.flows.WAMPMessagesFlow;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.util.WAMPException;

/**
 *
 * @author 000ssg
 */
public interface WAMPBroker {

    WAMPMessagesFlow.WAMPFlowStatus publish(WAMPSession session, WAMPMessage msg) throws WAMPException;

    WAMPMessagesFlow.WAMPFlowStatus subscribe(WAMPSession session, WAMPMessage msg) throws WAMPException;

    WAMPMessagesFlow.WAMPFlowStatus unsubscribe(WAMPSession session, WAMPMessage msg) throws WAMPException;

    /**
     * Utility add-on method ???
     *
     * @param session
     * @param request
     * @param topic
     * @param publicationId
     * @param details
     * @param args
     * @param argsKw
     * @throws WAMPException
     */
    void doEvent(
            WAMPSession session,
            long request,
            String topic,
            long publicationId,
            Map<String, Object> details,
            List args,
            Map<String, Object> argsKw
    ) throws WAMPException;
}
