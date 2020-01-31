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

import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.messages.WAMPMessage;

/**
 * WAMP message handler allows to encapsulate messages flow logic for distinct
 * areas, like session management, publishing, RPC etc.
 *
 * Handler "consumes" messages that it can handle and may produce them sending
 * via provided context. In success returns "handled", if error - "failed". Some
 * messages may be handled depending on context (e.g. ERROR message context
 * depends on type of responded call). In this case if message is not
 * processable due to context, it returns "ignored" status. Status "busy"
 * indicates message would be handled, but later.
 *
 * @author 000ssg
 */
public interface WAMPMessagesFlow {

    public static enum WAMPFlowStatus {
        handled,
        busy,
        ignored,
        failed
    }

    boolean canHandle(WAMPSession session, WAMPMessage msg);

    WAMPFlowStatus handle(WAMPSession session, WAMPMessage msg) throws WAMPException;
}
