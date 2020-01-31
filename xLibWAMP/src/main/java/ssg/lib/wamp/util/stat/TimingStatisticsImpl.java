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
package ssg.lib.wamp.util.stat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 *
 * @author sesidoro
 */
public class TimingStatisticsImpl extends StatisticsBase implements TimingStatistics {

    public static final int duration = 0;
    public static final int durationCount = 1;
    public static final int minDuration = 2;
    public static final int maxDuration = 3;
    public static final int avgDuration = 4;
    public static String[] names = new String[]{
        "Duration",
        "countDuration",
        "minDuration",
        "avgDuration",
        "maxDuration"
    };

    private TimeUnit unit = TimeUnit.MILLISECONDS;

    /**
     * Disable timing data if no timing events...
     *
     * @param idx
     * @return
     */
    @Override
    public boolean validDumpIndex(int idx) {
        boolean r = super.validIndex(idx);
        if (r) {
            idx -= getGroupOffset();
            r = idx == 0 && (get(getGroupOffset() + durationCount) > 0);
        }
        return r;
    }

    @Override
    public void onDuration(long dur) {
        if (dur == 0) {
            return;
        }
        if (getParent() instanceof TimingStatistics) {
            ((TimingStatistics) getParent()).onDuration(dur);
        }
        touch();
        int off = getGroupOffset();
        AtomicLongArray ala = getData().counters();
        synchronized (ala) {
            if (get(off + durationCount) == 0) {
                ala.set(off + duration, dur);
                ala.set(off + durationCount, 1);
                ala.set(off + minDuration, dur);
                ala.set(off + maxDuration, dur);
                ala.set(off + avgDuration, dur);
            } else {
                ala.addAndGet(off + duration, dur);
                ala.incrementAndGet(off + durationCount);
                ala.set(off + minDuration, Math.min(ala.get(off + minDuration), dur));
                ala.set(off + maxDuration, Math.max(ala.get(off + maxDuration), dur));
                ala.set(off + avgDuration, (ala.get(off + avgDuration) + dur) / 2);
            }
        }
    }

    @Override
    public int getGroupSize() {
        return 5;
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

        if (idx == off) {
            sb.append(names[0]);
            boolean single = get(idx + duration) < 2;
            if (!single) {
                sb.append('(');
                for (int i = 2; i < names.length; i++) {
                    if (i > 2) {
                        sb.append('/');
                    }
                    sb.append(names[i].replace(names[0], ""));
                }
                sb.append(')');
            }
            sb.append("=");
            sb.append(toTimeValue(get(idx + duration)));

            if (!single) {
                sb.append(" (");
                boolean first = true;
                for (long l : new long[]{
                    //get(idx + durationCount),
                    get(idx + minDuration),
                    get(idx + avgDuration),
                    get(idx + maxDuration)
                }) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append("/");
                    }
                    sb.append(toTimeValue(l));
                }
                sb.append(')');
            }
            sb.append(toTimeStr());
        } else {
            sb.append(name(idx));
            sb.append('=');
            sb.append(get(idx));
        }

        return sb.toString();
    }

    /**
     * @return the unit
     */
    public TimeUnit getUnit() {
        return unit;
    }

    /**
     * @param unit the unit to set
     */
    public void setUnit(TimeUnit unit) {
        this.unit = unit;
    }

    public String toTimeValue(Long value) {
        if (value == null) {
            return null;
        }
        switch (unit) {
            case NANOSECONDS:
                return "" + (value / 1000000f);
            case MICROSECONDS:
                return "" + (value / 1000f);
            case MILLISECONDS:
                return "" + (value);
            case SECONDS:
            case MINUTES:
            case HOURS:
            case DAYS:
        }
        return null;
    }

    public String toTimeStr() {
        switch (unit) {
            case NANOSECONDS:
            case MICROSECONDS:
            case MILLISECONDS:
                return "ms";
            case SECONDS:
                return "sec";
            case MINUTES:
                return "min";
            case HOURS:
                return "h";
            case DAYS:
                return "d";
        }
        return null;
    }
}
