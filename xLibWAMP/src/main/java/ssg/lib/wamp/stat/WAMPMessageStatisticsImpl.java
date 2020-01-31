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

import java.util.Arrays;
import ssg.lib.wamp.messages.WAMPMessage;
import ssg.lib.wamp.messages.WAMPMessageType;
import ssg.lib.wamp.util.stat.StatisticsBase;
import ssg.lib.wamp.util.stat.StatisticsGroup;

/**
 *
 * @author sesidoro
 */
public class WAMPMessageStatisticsImpl extends StatisticsBase implements WAMPMessageStatistics {

    private static int[] typeOffsets;
    private static int[] typeIndices;
    private static int typesCount = 0;
    //int offset;

    private int inputQueueSize;
    private int outputQueueSize;

    public WAMPMessageStatisticsImpl() {
    }

    public WAMPMessageStatisticsImpl(String name) {
        super(name);
    }
    
    public void initTypesCount() {
        // init stat indices/offsets
        if (typeOffsets == null) {
            typeOffsets = new int[WAMPMessageType.getMaxTypeId() + 1];
            synchronized (typeOffsets) {
                typeIndices = new int[typeOffsets.length];
                Arrays.fill(typeOffsets, -1);
                Arrays.fill(typeIndices, -1);
                int off = 1;
                for (int i = 1; i < typeOffsets.length; i++) {
                    if (WAMPMessageType.getType(i) != null) {
                        typeIndices[off] = i;
                        typeOffsets[i] = off++;
                    }
                }
                typesCount = off;
            }
        }
    }

    @Override
    public int getGroupSize() {
        if (typesCount == 0) {
            initTypesCount();
        }
        return typesCount * 2;
    }

    @Override
    public String name(int idx) {
        if (typesCount == 0) {
            initTypesCount();
        }
        idx -= getGroupOffset();
        if (idx >= 0 && idx < typesCount * 2) {
            if (idx >= typesCount) {
                idx -= typesCount;
            }
            if (idx == 0) {
                return "All";
            } else {
                if (typeIndices[idx] != -1) {
                    return WAMPMessageType.getTypeName(typeIndices[idx]);
                }
            }
        }
        return null;
    }

    public void onSent(WAMPMessage message) {
        if (message != null) {
            getData().counters().incrementAndGet(getGroupOffset());
            int off = typeOffsets[message.getType().getId()];
            if (off >= 0) {
                getData().counters().incrementAndGet(getGroupOffset() + off);
                touch();
                if (getParent() instanceof WAMPMessageStatistics) {
                    ((WAMPMessageStatistics) getParent()).onSent(message);
                } else if (getParent() instanceof StatisticsGroup) {
                    WAMPMessageStatistics pstat = ((StatisticsGroup) getParent()).getCompatibleParent(WAMPMessageStatistics.class);
                    if (pstat != null) {
                        pstat.onSent(message);
                    }
                }
            }
        }
    }

    public void onReceived(WAMPMessage message) {
        if (message != null) {
            getData().counters().incrementAndGet(getGroupOffset() + typesCount);
            int off = typeOffsets[message.getType().getId()];
            if (off >= 0) {
                getData().counters().incrementAndGet(getGroupOffset() + typesCount + off);
                touch();
                if (getParent() instanceof WAMPMessageStatistics) {
                    ((WAMPMessageStatistics) getParent()).onReceived(message);
                } else if (getParent() instanceof StatisticsGroup) {
                    WAMPMessageStatistics pstat = ((StatisticsGroup) getParent()).getCompatibleParent(WAMPMessageStatistics.class);
                    if (pstat != null) {
                        pstat.onReceived(message);
                    }
                }
            }
        }
    }

    /**
     * @return the inputQueueSize
     */
    public int getInputQueueSize() {
        return inputQueueSize;
    }

    /**
     * @param inputQueueSize the inputQueueSize to set
     */
    public void setInputQueueSize(int inputQueueSize) {
        this.inputQueueSize = inputQueueSize;
    }

    /**
     * @return the outputQueueSize
     */
    public int getOutputQueueSize() {
        return outputQueueSize;
    }

    /**
     * @param outputQueueSize the outputQueueSize to set
     */
    public void setOutputQueueSize(int outputQueueSize) {
        this.outputQueueSize = outputQueueSize;
    }

    @Override
    public String dumpStatistics(int idx, boolean compact) {
        if (idx < getGroupOffset() || idx >= getGroupOffset() + (typesCount * 2)) {
            return "";//super.dumpStatistics(idx, compact);
        } else {
            StringBuilder sb = new StringBuilder();
            String name = name(idx);
            long sent = get(idx);
            long received = get(idx + typesCount);
            if (compact) {
                sb.append("'");
                sb.append(name);
                sb.append("'[");
                sb.append(sent);
                sb.append(", ");
                sb.append(received);
                sb.append("]");
            } else {
                //sb.append("\n  ");
                int l = sb.length() + 20;
                sb.append(name);
                while (sb.length() < l) {
                    sb.append(' ');
                }
                l = sb.length() + 12;
                sb.append(sent);
                while (sb.length() < l) {
                    sb.append(' ');
                }
                sb.append(received);
            }
            return sb.toString();
        }
    }

    @Override
    public String dumpInlineInfo() {
        String r = super.dumpInlineInfo();
        if (r == null) {
            r = "";
        }
        return r + ", input queue=" + getInputQueueSize() + ", output queue=" + getOutputQueueSize();
    }

    @Override
    public boolean validDumpIndex(int idx) {
        if (idx < getGroupOffset() || idx >= getGroupOffset() + (typesCount * 2)) {
            return false;//super.validDumpIndex(idx);
        }
        int idx2 = idx - getGroupOffset();
        // All is always viewable
        if (idx2 == 0) {
            //return true;
        }
        // points to 1st (SEND) counters (RECEIVE counters are displayed together with SEND ones)
        if (idx2 < typesCount) {
            // if has value (SEND or RECEIVE) -> display
            if (get(idx) > 0 || get(idx + typesCount) > 0) {
                return true;
            }
        }
        return false;
    }
}
