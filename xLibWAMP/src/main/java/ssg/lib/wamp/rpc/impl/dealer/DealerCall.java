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
import java.util.concurrent.atomic.AtomicInteger;
import ssg.lib.wamp.WAMPSession;
import ssg.lib.wamp.rpc.impl.Call;
import ssg.lib.wamp.util.WAMPTools;
import ssg.lib.wamp.stat.WAMPCallStatistics;

/**
 * Dealer call binds caller request and callee invocation providing bypassing
 * parameters, results, and error.
 *
 * @author 000ssg
 */
public class DealerCall extends Call {

    // from caller
    WAMPSession session;
    long request;
    Map<String, Object> options;
    List args;
    Map<String, Object> argsKw;
    boolean cancelable = false;
    String interrupted = null;

    // on dealer
    DealerProcedure proc;
    private WAMPCallStatistics statistics;

    // to/from callee
    // long invocationId;
    long[] invocationIds;
    AtomicInteger activeCalls = new AtomicInteger();

    Map<String, Object> details = WAMPTools.createDict(null);

    public DealerCall(DealerProcedure proc) {
        this.proc = proc;
        this.statistics = proc.getStatistics();
        if (statistics != null) {
            statistics.onCall();
        }
        invocationIds = new long[(proc instanceof DealerMultiProcedure) ? ((DealerMultiProcedure) proc).procs.length : 1];
    }

    public DealerCall(DealerProcedure proc, WAMPCallStatistics stat) {
        this.proc = proc;
        if (stat != null) {
            this.statistics = stat;
        } else {
            statistics = proc.getStatistics();
        }
        if (statistics != null) {
            statistics.onCall();
        }
        invocationIds = new long[(proc instanceof DealerMultiProcedure) ? ((DealerMultiProcedure) proc).procs.length : 1];
    }

    /**
     * @return the statistics
     */
    public WAMPCallStatistics getStatistics() {
        return statistics;
    }

    public int getProceduresCount() {
        return (proc instanceof DealerMultiProcedure) ? ((DealerMultiProcedure) proc).procs.length : 1;
    }

    public DealerProcedure getProcedure(int procIdx) {
        return (proc instanceof DealerMultiProcedure) ? ((DealerMultiProcedure) proc).procs[procIdx] : proc;
    }

    public void setInvocationId(int procIdx, long invocationId) {
        invocationIds[procIdx] = invocationId;
    }

    public long getInvocationId(int procIdx) {
        return invocationIds[procIdx];
    }

    /**
     * Returns index of completed procedure
     *
     * @param invocationId
     * @return
     */
    public int completed(long invocationId) {
        for (int i = 0; i < invocationIds.length; i++) {
            if (invocationIds[i] == invocationId) {
                invocationIds[i] = 0;
                activeCalls.decrementAndGet();
                return i;
            }
        }
        return -1;
    }
//
//    /**
//     * Wrapper for dealer multi procedure
//     */
//    public static class DealerMultiCall extends DealerCall {
//
//        long[] invocationIds;
//        AtomicInteger activeCalls = new AtomicInteger();
//
//        public DealerMultiCall(DealerMultiProcedure proc) {
//            super(proc);
//            invocationIds = new long[proc.procs.length];
//        }
//
//        public DealerMultiCall(DealerMultiProcedure proc, WAMPCallStatistics stat) {
//            super(proc, stat);
//            invocationIds = new long[proc.procs.length];
//        }
//
//        public int getProceduresCount() {
//            return ((DealerMultiProcedure) proc).procs.length;
//        }
//
//        public DealerProcedure getProcedure(int procIdx) {
//            return ((DealerMultiProcedure) proc).procs[procIdx];
//        }
//
//        public long getInvocationId(int procIdx) {
//            return invocationIds[procIdx];
//        }
//
//        /**
//         * Returns index of completed procedure
//         *
//         * @param invocationId
//         * @return
//         */
//        public int completed(long invocationId) {
//            for (int i = 0; i < invocationIds.length; i++) {
//                if (invocationIds[i] == invocationId) {
//                    invocationIds[i] = 0;
//                    activeCalls.decrementAndGet();
//                    return i;
//                }
//            }
//            return -1;
//        }
//    }
}
