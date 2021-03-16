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
package ssg.lib.stat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author 000ssg
 */
public abstract class StatisticsBase implements Statistics {

    private Statistics parent;
    //private StatisticsGroup[] groups;
    private StatisticsData data;
    private String name;
    private int offset;
    private long lastModified;
    private StatisticsListener touchListener;

    public StatisticsBase() {
    }

    public StatisticsBase(String name) {
        this.name = name;
    }

    public StatisticsBase(StatisticsData data) {
        this.data = data;
    }

    public StatisticsBase(String name, StatisticsData data) {
        this.name = name;
        this.data = data;
    }

    //        @Override
    //        public int getGroupSize() {
    //            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    //        }
    //        @Override
    //        public String name(int idx) {
    //            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    //        }
    @Override
    public String getGroupName() {
        return name;
    }

    public void setGroupName(String name) {
        this.name = name;
    }

    @Override
    public void setParent(Statistics parent) {
        this.parent = parent;
    }

    @Override
    public <T extends Statistics> T getParent() {
        return (T) parent;
    }

    @Override
    public Statistics getTop() {
        if (parent == null) {
            return this;
        } else {
            return parent.getTop();
        }
    }

    @Override
    public <T extends Statistics> T createChild(Statistics top, String name) {
        try {
            StatisticsBase copy = (StatisticsBase) clone();
            copy.name = name;
            copy.parent = this;
            copy.touchListener = null;
            copy = fixChildOnCreation(copy);
            if (top == null || this == top) {
                copy.setGroupOffset(0);
                copy.init(new SimpleStatisticsData(copy.getGroupSize()));
            }
            return (T) copy;
        } catch (CloneNotSupportedException cnsex) {
            cnsex.printStackTrace();
            return null;
        }
    }

    /**
     * Callback to allow modification of a copy before applying offset/data
     * changes.
     *
     * @param <T>
     * @param copy
     * @return
     */
    public <T extends Statistics> T fixChildOnCreation(T copy) {
        return copy;
    }

    @Override
    public void init(StatisticsData data) {
        if (data == null) {
            data = new SimpleStatisticsData(getGroupSize());
        }
        setData(data);
    }

    @Override
    public long get(int idx) {
        return data.counters().get(idx);// + getGroupOffset());
    }

    public long[] getGroupData() {
        long[] r = new long[getGroupSize()];
        for (int i = 0; i < r.length; i++) {
            r[i] = get(i);
        }
        return r;
    }

    public String[] getGroupNames() {
        String[] r = new String[getGroupSize()];
        for (int i = 0; i < r.length; i++) {
            r[i] = name(i);
        }
        return r;
    }

    public Map<String, Long> getGroupMap() {
        Map<String, Long> r = new LinkedHashMap<>();
        for (int i = 0; i < getGroupSize(); i++) {
            if (validIndex(i)) {
                r.put(name(i), get(i));
            }
        }
        return r;
    }

    @Override
    public String dumpStatistics(boolean compact) {
        StringBuilder sb = new StringBuilder();
        if (!compact) {
            sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        }
        sb.append('{');
        int initialLen = sb.length();
        sb.append(dumpInlineInfo());
        boolean hasInitialInfo = initialLen < sb.length();
        initialLen = sb.length();
        int lastIdx = -1;
        for (int i = 0; i < getGroupSize(); i++) {
            if (validDumpIndex(i)) {
                sb.append(dumpSeparator(lastIdx, i, compact, hasInitialInfo || sb.length() > initialLen));
//                sb.append((compact)
//                        ? (hasInitialInfo || sb.length() > initialLen)
//                        ? ", "
//                        : ""
//                        : "\n  "
//                );
                sb.append(dumpStatistics(i, compact));
                lastIdx = i;
            }
        }
        if (!compact && sb.length() > initialLen) {
            sb.append('\n');
        }
        sb.append('}');
        return sb.toString();
    }

    public String dumpSeparator(int lastIdx, int idx, boolean compact, boolean first) {
        return (compact)
                ? (!first)
                        ? ", "
                        : " "
                : "\n  ";
    }

    @Override
    public boolean validIndex(int idx) {
        return getData() != null && idx >= getGroupOffset() && idx < (getGroupOffset() + getGroupSize()) && idx < getData().counters().length();
    }

    @Override
    public boolean validDumpIndex(int idx) {
        return validIndex(idx);
    }

    @Override
    public String dumpInlineInfo() {
        if (name != null) {
            return "name=" + name;
        } else {
            return "";
        }
    }

    @Override
    public String dumpStatistics(int idx, boolean compact) {
        long v=getData().counters().get(idx);
        return name(idx)+"="+v;
    }

    /**
     * @return the data
     */
    public StatisticsData getData() {
        return data;
    }

    /**
     * @param data the data to set
     */
    @Override
    public void setData(StatisticsData data) {
        this.data = data;
    }

    /**
     */
    public int getGroupOffset() {
        return offset;
    }

    /**
     * @param offset the offset to set
     */
    public void setGroupOffset(int offset) {
        this.offset = offset;
    }

    public void touch() {
        lastModified = System.currentTimeMillis();
        if (touchListener != null) {
            touchListener.onTouched(this);
        }
    }

    public long lastModified() {
        return lastModified;
    }

    /**
     * @return the touchListener
     */
    public StatisticsListener getStatisticsListener() {
        return touchListener;
    }

    /**
     * @param touchListener the touchListener to set
     */
    public void setStatisticsListener(StatisticsListener touchListener) {
        this.touchListener = touchListener;
    }

    /**
     * functional interface to catch statistics updates
     */
    public static interface StatisticsListener {

        void onTouched(Statistics stat);
    }
}
