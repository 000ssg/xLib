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

/**
 *
 * @author 000ssg
 */
public class StatisticsGroup extends StatisticsBase {

    Statistics[] groups;
    private int[] offsets;

    public StatisticsGroup(String name, Statistics... groups) {
        super(name);
        setGroups(groups);
    }

    @Override
    public int getGroupSize() {
        return (offsets != null) ? offsets[offsets.length - 1] + groups[offsets.length - 1].getGroupSize() : 0;
    }

    @Override
    public String name(int idx) {
        int gIdx = getGroupIndex(idx);
        if (gIdx == -1) {
            return "";
        }
        return groups[gIdx].name(idx);
    }

    /**
     * @return the groups
     */
    public Statistics[] getGroups() {
        return groups;
    }

    /**
     * @param groups the groups to set
     */
    public void setGroups(Statistics... groups) {
        this.groups = groups;
        if (groups == null || groups.length == 0) {
            offsets = null;
        } else {
            int offset = getGroupOffset();
            offsets = new int[groups.length];
            for (int i = 0; i < offsets.length - 1; i++) {
                groups[i].setGroupOffset(offset + offsets[i]);
                offsets[i + 1] = offset + offsets[i] + groups[i].getGroupSize();
            }
            groups[groups.length - 1].setGroupOffset(offset + offsets[groups.length - 1]);
        }
    }

    @Override
    public void setData(StatisticsData data) {
        super.setData(data);
        for (Statistics group : groups) {
            group.setData(data);
        }
    }

    @Override
    public <T extends Statistics> T fixChildOnCreation(T copy) {
        StatisticsGroup stat = super.fixChildOnCreation((StatisticsGroup) copy);
        Statistics[] cg = new Statistics[stat.groups.length];
        for (int i = 0; i < cg.length; i++) {
            cg[i] = groups[i].createChild(this, stat.getGroupName());
        }
        stat.setGroups(cg);

        return copy;
    }

    @Override
    public String dumpStatistics(int idx, boolean compact) {
        int gIdx = getGroupIndex(idx);
        if (gIdx == -1) {
            return "";
        }
        if (groups[gIdx].validDumpIndex(idx)) {
            String s = groups[gIdx].dumpStatistics(idx, compact);
            if (compact) {
                return s;
            } else {
                return "  " + s.replace("\n", "\n  ");
            }
        } else {
            return "";
        }
    }

    @Override
    public boolean validDumpIndex(int idx) {
        if (groups == null || groups.length == 0) {
            return super.validDumpIndex(idx);
        }
        int gIdx = getGroupIndex(idx);
        return groups[gIdx].validDumpIndex(idx);
    }

    int getGroupIndex(int idx) {
        int r = -1;
        if (idx < getGroupOffset() || idx >= getGroupOffset() + getGroupSize()) {
            return r;
        }

        r = 0;
        if (offsets != null) {
            for (int i = 1; i < offsets.length; i++) {
                if (idx < offsets[i]) {
                    break;
                }
                r = i;
            }
        }
        return r;
    }

    @Override
    public String dumpSeparator(int lastIdx, int idx, boolean compact, boolean first) {
        String s = "";
        if (groups != null && groups.length > 0) {
            int gIdx = getGroupIndex(idx);
            if (lastIdx == -1 || gIdx != getGroupIndex(lastIdx)) {
                s = ((compact)
                        ? " #"
                        : "\n  #");
                if (groups[gIdx].getGroupName() != null) {
                    s += groups[gIdx].getGroupName() + "/";
                }
                s += (groups[gIdx].getClass().isAnonymousClass()) ? groups[gIdx].getClass().getName() : groups[gIdx].getClass().getSimpleName();
                s += " ";
                first = true;
            }
        }
        return s + super.dumpSeparator(lastIdx, idx, compact, first);
    }

    @Override
    public void setGroupOffset(int offset) {
        super.setGroupOffset(offset);
        setGroups(getGroups());
    }

    public <T extends Statistics> T getCompatible(Class ref) {
        if (ref == null) {
            return null;
        }
        for (Statistics group : groups) {
            if (group != null && ref.isAssignableFrom(group.getClass())) {
                return (T) group;
            }
        }
        return null;
    }

    public <T extends Statistics> T getCompatibleParent(Class ref) {
        if (ref == null) {
            return null;
        }
        if (getParent() != null) {
            if (ref.isAssignableFrom(getParent().getClass())) {
                return getParent();
            } else if (getParent() instanceof StatisticsGroup) {
                return ((StatisticsGroup) getParent()).getCompatible(ref);
            }
        }
        return null;
    }

}
