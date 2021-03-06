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
package ssg.lib.wamp.rpc.impl.callee;

import java.util.Map;
import java.util.concurrent.Future;
import ssg.lib.wamp.util.WAMPException;
import ssg.lib.wamp.WAMPSession;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_CALL_TIMEOUT;
import ssg.lib.wamp.rpc.impl.Call;
import ssg.lib.wamp.stat.WAMPCallStatistics;
import static ssg.lib.wamp.rpc.WAMPRPCConstants.RPC_PROGRESSIVE_CALL_REQUEST_KEY;

/**
 * Callee call context binds procedure descriptor and execution instance for a
 * session.
 *
 * @author 000ssg
 */
public class CalleeCall extends Call {

    CalleeProcedure proc;
    WAMPSession session;
    Future future;
    DelayedCall delayed;

    String procedure;
    Map<String, Object> details;
    WAMPCallStatistics callStatistics;

    public CalleeCall(CalleeProcedure proc, Map<String, Object> details) {
        this.proc = proc;
        this.details = details;
        if (details.containsKey(RPC_PROGRESSIVE_CALL_REQUEST_KEY) && (Boolean) details.get(RPC_PROGRESSIVE_CALL_REQUEST_KEY)) {
            setProgressiveResult(true);
        }
        if (proc != null && proc.getStatistics() != null) {
            proc.getStatistics().onCall();
        }
        if (details.get(RPC_CALL_TIMEOUT) instanceof Number) {
            Number n = (Number) details.get(RPC_CALL_TIMEOUT);
            setTimeout(n.longValue());
        }
    }

    public boolean hasDelayed() {
        return delayed != null;
    }

    public void runDelayed() throws WAMPException {
        try {
            future = delayed.invoke();
        } finally {
            delayed = null;
        }
    }

    public String getAgent() {
        return session.getLocal().getAgent() + "-" + session.getRemote().getAgent();
    }

    public static interface DelayedCall {

        Future invoke() throws WAMPException;
    }

    public WAMPCallStatistics getStatistics() {
        return callStatistics != null ? callStatistics : proc != null ? proc.getStatistics() : null;
    }

    @Override
    public String toString() {
        return (getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName())
                + "{"
                + "id=" + getId()
                + ", started=" + getStarted()
                + "proc=" + proc
                + ", session=" + session
                + ", future=" + future
                + ", delayed=" + delayed
                + '}';

    }

}
