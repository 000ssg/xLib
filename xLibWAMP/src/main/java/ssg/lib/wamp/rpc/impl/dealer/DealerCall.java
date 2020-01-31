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
    long invocationId;
    Map<String, Object> details = WAMPTools.createDict(null);

    public DealerCall(DealerProcedure proc) {
        this.proc = proc;
        this.statistics = proc.getStatistics();
        if (statistics != null) {
            statistics.onCall();
        }
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
    }

    /**
     * @return the statistics
     */
    public WAMPCallStatistics getStatistics() {
        return statistics;
    }
}
