/*
 * The MIT License
 *
 * Copyright 2020 sesidoro.
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
package ssg.lib.api.wamp_cs.db;

import java.util.concurrent.TimeUnit;
import ssg.lib.wamp.util.stat.StatisticsBase;
import ssg.lib.wamp.util.stat.StatisticsGroup;
import ssg.lib.wamp.util.stat.TimingStatistics;
import ssg.lib.wamp.util.stat.TimingStatisticsImpl;

/**
 *
 * @author 000ssg
 */
public class DBStatistics extends StatisticsGroup {

    private DBCountStat counts = new DBCountStat();
    private TimingStatistics alloc = new TimingStatisticsImpl("alloc").unit(TimeUnit.NANOSECONDS);
    private TimingStatistics exec = new TimingStatisticsImpl("exec").unit(TimeUnit.NANOSECONDS);
    private TimingStatistics dealloc = new TimingStatisticsImpl("dealloc").unit(TimeUnit.NANOSECONDS);

    public DBStatistics() {
        super("DB stat");
        setGroups(counts, alloc, exec, dealloc);
        init(null);
    }

    public DBCountStat counters() {
        return counts;
    }

    public TimingStatistics alloc() {
        return alloc;
    }

    public TimingStatistics exec() {
        return exec;
    }

    public TimingStatistics dealloc() {
        return dealloc;
    }

    public static class DBCountStat extends StatisticsBase {

        static final int idxGet = 0;
        static final int idxGot = 1;
        static final int idxCreate = 2;
        static final int idxUnget = 3;
        static final int idxClose = 4;

        String[] names = {"get", "got", "create", "unget", "close"};

        public DBCountStat() {
            super("DB counters");
        }

        @Override
        public int getGroupSize() {
            return 5;
        }

        @Override
        public String name(int idx) {
            idx -= getGroupOffset();
            if (idx >= 0 && idx < getGroupSize()) {
                return names[idx];
            }
            return null;
        }

        public void onGet() {
            touch();
            getData().counters().incrementAndGet(getGroupOffset() + idxGet);
            if (getParent() instanceof DBCountStat) {
                ((DBCountStat) getParent()).onGet();
            }
        }

        public void onGot() {
            touch();
            getData().counters().incrementAndGet(getGroupOffset() + idxGot);
            if (getParent() instanceof DBCountStat) {
                ((DBCountStat) getParent()).onGot();
            }
        }

        public void onCreate() {
            touch();
            getData().counters().incrementAndGet(getGroupOffset() + idxCreate);
            if (getParent() instanceof DBCountStat) {
                ((DBCountStat) getParent()).onCreate();
            }
        }

        public void onUnget() {
            touch();
            getData().counters().incrementAndGet(getGroupOffset() + idxUnget);
            if (getParent() instanceof DBCountStat) {
                ((DBCountStat) getParent()).onUnget();
            }
        }

        public void onClose() {
            touch();
            getData().counters().incrementAndGet(getGroupOffset() + idxClose);
            if (getParent() instanceof DBCountStat) {
                ((DBCountStat) getParent()).onClose();
            }
        }
    }
}
