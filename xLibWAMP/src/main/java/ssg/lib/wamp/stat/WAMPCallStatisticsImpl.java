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
package ssg.lib.wamp.stat;

import java.util.concurrent.TimeUnit;
import ssg.lib.stat.StatisticsGroup;
import ssg.lib.stat.TimingStatisticsImpl;

/**
 *
 * @author 000ssg
 */
public class WAMPCallStatisticsImpl extends TimingStatisticsImpl implements WAMPCallStatistics {

    int off = super.getGroupSize();
    String[] names = new String[]{"call", "cancel", "error"};

    public WAMPCallStatisticsImpl() {
        this.setUnit(TimeUnit.NANOSECONDS);
    }

    @Override
    public boolean validDumpIndex(int idx) {
        boolean r = super.validDumpIndex(idx);
        if (!r) {
            idx -= getGroupOffset();
            if (idx >= off && idx < getGroupSize()) {
                r = hasCalls();
            }
        }
        return r;
    }

    @Override
    public int getGroupSize() {
        return off + 3;
    }

    @Override
    public String name(int idx) {
        if (idx >= off && idx < off + 3) {
            return names[idx - off];
        } else {
            return super.name(idx);
        }
    }

    @Override
    public boolean hasCalls() {
        return getData() != null && getData().counters().get(off) > 0;
    }

    @Override
    public void onDuration(long dur) {
        try {
            super.onDuration(dur); //To change body of generated methods, choose Tools | Templates.
        } finally {
            if (getParent() instanceof StatisticsGroup) {
                WAMPCallStatistics pstat = ((StatisticsGroup) getParent()).getCompatibleParent(WAMPCallStatistics.class);
                if (pstat != null) {
                    pstat.onDuration(dur);
                }
            }
        }
    }

    @Override
    public long onCall() {
        try {
            touch();
            return getData().counters().incrementAndGet(off);
        } finally {
            if (getParent() instanceof WAMPCallStatistics) {
                ((WAMPCallStatistics) getParent()).onCall();
            } else if (getParent() instanceof StatisticsGroup) {
                WAMPCallStatistics pstat = ((StatisticsGroup) getParent()).getCompatibleParent(WAMPCallStatistics.class);
                if (pstat != null) {
                    pstat.onCall();
                }
            }
        }
    }

    @Override
    public long onCancel() {
        try {
            touch();
            return getData().counters().incrementAndGet(off + 1);
        } finally {
            if (getParent() instanceof WAMPCallStatistics) {
                ((WAMPCallStatistics) getParent()).onCancel();
            } else if (getParent() instanceof StatisticsGroup) {
                WAMPCallStatistics pstat = ((StatisticsGroup) getParent()).getCompatibleParent(WAMPCallStatistics.class);
                if (pstat != null) {
                    pstat.onCall();
                }
            }
        }
    }

    @Override
    public long onError() {
        try {
            touch();
            return getData().counters().incrementAndGet(off + 2);
        } finally {
            if (getParent() instanceof WAMPCallStatistics) {
                ((WAMPCallStatistics) getParent()).onError();
            } else if (getParent() instanceof StatisticsGroup) {
                WAMPCallStatistics pstat = ((StatisticsGroup) getParent()).getCompatibleParent(WAMPCallStatistics.class);
                if (pstat != null) {
                    pstat.onError();
                }
            }
        }
    }

    @Override
    public String toString() {
        return this.dumpStatistics(true);
    }

}
