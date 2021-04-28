/*
 * The MIT License
 *
 * Copyright 2020 000ssg.
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
package ssg.lib.httpapi_cs;

import java.util.ArrayList;
import java.util.List;
import ssg.lib.stat.Statistics;
import ssg.lib.stat.StatisticsGroup;

/**
 *
 * @author 000ssg
 */
public class APIStatistics extends StatisticsGroup {

    static final int TRY_INVOKE = 0;
    static final int INVOKE = 1;
    static final int ERROR = 2;
    static final int DONE = 3;

    static final String[] names = new String[]{
        "TRY",
        "INVOKE",
        "ERROR",
        "DONE"
    };

    public APIStatistics(Statistics... groups) {
        super("APIStat", groups);
        init(null);
    }

    @Override
    public int getGroupSize() {
        return 4;
    }

    @Override
    public String name(int idx) {
        idx -= getGroupOffset();
        if (idx >= 0 && idx < names.length) {
            return names[idx];
        } else {
            return null;
        }
    }

    @Override
    public String dumpStatistics(int idx, boolean compact) {
        StringBuilder sb = new StringBuilder();
        int off = getGroupOffset();

        sb.append(name(idx));
        sb.append('=');
        sb.append(get(idx));

        return sb.toString();
    }

    public String dumpStatisticsHierarchy(boolean compact) {
        StringBuilder sb = new StringBuilder();
        List<Statistics> hs = hierarchy();
        StringBuilder off = new StringBuilder();
        for (Statistics h : hs) {
            sb.append(off);
            sb.append(h.dumpStatistics(compact).replace("\n", "\n" + off));
            off.append("  ");
        }
        return sb.toString();
    }

    <Z extends Statistics> List<Z> hierarchy() {
        List<Z> r = new ArrayList<>();
        Statistics c = this;
        while (c != null) {
            r.add(0, (Z) c);
            c = c.getParent();
        }
        return r;
    }

    public void onTryInvoke() {
        int off = getGroupOffset();
        getData().counters().incrementAndGet(off + TRY_INVOKE);

        if (getParent() instanceof APIStatistics) {
            ((APIStatistics) getParent()).onTryInvoke();
        }
    }

    public void onInvoke() {
        int off = getGroupOffset();
        getData().counters().incrementAndGet(off + INVOKE);

        if (getParent() instanceof APIStatistics) {
            ((APIStatistics) getParent()).onInvoke();
        }
    }

    public void onError() {
        int off = getGroupOffset();
        getData().counters().incrementAndGet(off + ERROR);

        if (getParent() instanceof APIStatistics) {
            ((APIStatistics) getParent()).onError();
        }
    }

    public void onDone() {
        int off = getGroupOffset();
        getData().counters().incrementAndGet(off + DONE);

        if (getParent() instanceof APIStatistics) {
            ((APIStatistics) getParent()).onDone();
        }
    }
}
