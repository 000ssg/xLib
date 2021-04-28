/*
 * The MIT License
 *
 * Copyright 2021 000ssg.
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
package ssg.lib.net.stat;

import java.util.concurrent.TimeUnit;
import ssg.lib.stat.StatisticsGroup;
import ssg.lib.stat.TimingStatisticsImpl;

/**
 *
 * @author 000ssg
 */
public class RunnerStatisticsImpl extends TimingStatisticsImpl implements RunnerStatistics {

    int off = super.getGroupSize();
    static String[] names = new String[]{
        "ACCEPT",
        "CONNECT",
        "CONNECTABLE",
        "CONNECTION",
        "READ",
        "WRITE",
        "CLOSE",
        "CHECK"
    };

    public RunnerStatisticsImpl() {
        this.setUnit(TimeUnit.NANOSECONDS);
    }

    @Override
    public boolean validDumpIndex(int idx) {
        boolean r = super.validDumpIndex(idx);
        if (!r) {
            idx -= getGroupOffset();
            if (idx >= off && idx < getGroupSize()) {
                r = true;
            }
        }
        return r;
    }

    @Override
    public int getGroupSize() {
        return off + names.length;
    }

    @Override
    public String name(int idx) {
        if (idx >= off && idx < off + names.length) {
            return names[idx - off];
        } else {
            return super.name(idx);
        }
    }

    ////////////////////////////////////////////////////////// stat calls
    @Override
    public void onDuration(long dur) {
        try {
            super.onDuration(dur);
        } finally {
            if (getParent() instanceof StatisticsGroup) {
                RunnerStatistics pstat = ((StatisticsGroup) getParent()).getCompatibleParent(RunnerStatistics.class);
                if (pstat != null) {
                    pstat.onDuration(dur);
                }
            }
        }
    }

    @Override
    public long onAccept() {
        try {
            touch();
            return getData().counters().incrementAndGet(off);
        } finally {
            if (getParent() instanceof RunnerStatistics) {
                ((RunnerStatistics) getParent()).onAccept();
            } else if (getParent() instanceof StatisticsGroup) {
                RunnerStatistics pstat = ((StatisticsGroup) getParent()).getCompatibleParent(RunnerStatistics.class);
                if (pstat != null) {
                    pstat.onAccept();
                }
            }
        }
    }

    @Override
    public long onConnect() {
        try {
            touch();
            return getData().counters().incrementAndGet(off+1);
        } finally {
            if (getParent() instanceof RunnerStatistics) {
                ((RunnerStatistics) getParent()).onConnect();
            } else if (getParent() instanceof StatisticsGroup) {
                RunnerStatistics pstat = ((StatisticsGroup) getParent()).getCompatibleParent(RunnerStatistics.class);
                if (pstat != null) {
                    pstat.onConnect();
                }
            }
        }
    }

    @Override
    public long onConnectable() {
        try {
            touch();
            return getData().counters().incrementAndGet(off+2);
        } finally {
            if (getParent() instanceof RunnerStatistics) {
                ((RunnerStatistics) getParent()).onConnectable();
            } else if (getParent() instanceof StatisticsGroup) {
                RunnerStatistics pstat = ((StatisticsGroup) getParent()).getCompatibleParent(RunnerStatistics.class);
                if (pstat != null) {
                    pstat.onConnectable();
                }
            }
        }
    }

    @Override
    public long onConnected() {
        try {
            touch();
            return getData().counters().incrementAndGet(off+3);
        } finally {
            if (getParent() instanceof RunnerStatistics) {
                ((RunnerStatistics) getParent()).onConnected();
            } else if (getParent() instanceof StatisticsGroup) {
                RunnerStatistics pstat = ((StatisticsGroup) getParent()).getCompatibleParent(RunnerStatistics.class);
                if (pstat != null) {
                    pstat.onConnected();
                }
            }
        }
    }

    @Override
    public long onRead() {
        try {
            touch();
            return getData().counters().incrementAndGet(off+4);
        } finally {
            if (getParent() instanceof RunnerStatistics) {
                ((RunnerStatistics) getParent()).onRead();
            } else if (getParent() instanceof StatisticsGroup) {
                RunnerStatistics pstat = ((StatisticsGroup) getParent()).getCompatibleParent(RunnerStatistics.class);
                if (pstat != null) {
                    pstat.onRead();
                }
            }
        }
    }

    @Override
    public long onWrite() {
        try {
            touch();
            return getData().counters().incrementAndGet(off+5);
        } finally {
            if (getParent() instanceof RunnerStatistics) {
                ((RunnerStatistics) getParent()).onWrite();
            } else if (getParent() instanceof StatisticsGroup) {
                RunnerStatistics pstat = ((StatisticsGroup) getParent()).getCompatibleParent(RunnerStatistics.class);
                if (pstat != null) {
                    pstat.onWrite();
                }
            }
        }
    }

    @Override
    public long onClose() {
        try {
            touch();
            return getData().counters().incrementAndGet(off+6);
        } finally {
            if (getParent() instanceof RunnerStatistics) {
                ((RunnerStatistics) getParent()).onClose();
            } else if (getParent() instanceof StatisticsGroup) {
                RunnerStatistics pstat = ((StatisticsGroup) getParent()).getCompatibleParent(RunnerStatistics.class);
                if (pstat != null) {
                    pstat.onClose();
                }
            }
        }
    }

    @Override
    public long onCheck() {
        try {
            touch();
            return getData().counters().incrementAndGet(off+6);
        } finally {
            if (getParent() instanceof RunnerStatistics) {
                ((RunnerStatistics) getParent()).onCheck();
            } else if (getParent() instanceof StatisticsGroup) {
                RunnerStatistics pstat = ((StatisticsGroup) getParent()).getCompatibleParent(RunnerStatistics.class);
                if (pstat != null) {
                    pstat.onCheck();
                }
            }
        }
    }
}
