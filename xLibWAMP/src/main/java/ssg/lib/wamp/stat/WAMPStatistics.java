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

import ssg.lib.stat.Statistics;
import ssg.lib.stat.StatisticsBase;
import ssg.lib.stat.StatisticsBase.StatisticsListener;
import ssg.lib.stat.StatisticsGroup;

/**
 *
 * @author 000ssg
 */
public class WAMPStatistics extends StatisticsGroup implements StatisticsListener {

    private WAMPCallStatistics callStatistics;
    private WAMPMessageStatistics messageStatistics;

    public WAMPStatistics() {
        super(null, new WAMPCallStatisticsImpl(), new WAMPMessageStatisticsImpl());
        init(null);
    }

    public WAMPStatistics(String name) {
        super(name, new WAMPCallStatisticsImpl(), new WAMPMessageStatisticsImpl());
        init(null);
    }

    public WAMPStatistics(String name, Statistics... groups) {
        super(name, groups);
        init(null);
    }

    @Override
    public void setGroups(Statistics... groups) {
        super.setGroups(groups);
        if (getGroups() != null) {
            for (Statistics group : getGroups()) {
                if(group instanceof StatisticsBase) {
                    ((StatisticsBase) group).setStatisticsListener(this);
                }
                if (group instanceof WAMPCallStatistics) {
                    setCallStatistics((WAMPCallStatistics) group);
                }
                if (group instanceof WAMPMessageStatistics) {
                    setMessageStatistics((WAMPMessageStatistics) group);
                }
            }
        }
    }

    /**
     * @return the callStatistics
     */
    public WAMPCallStatistics getCallStatistics() {
        return callStatistics;
    }

    /**
     * @param callStatistics the callStatistics to set
     */
    void setCallStatistics(WAMPCallStatistics callStatistics) {
        this.callStatistics = callStatistics;
        if (callStatistics != null) {
            callStatistics.setParent(this);
        }
    }

    /**
     * @return the messageStatistics
     */
    public WAMPMessageStatistics getMessageStatistics() {
        return messageStatistics;
    }

    /**
     * @param messageStatistics the messageStatistics to set
     */
    void setMessageStatistics(WAMPMessageStatistics messageStatistics) {
        this.messageStatistics = messageStatistics;
        if (messageStatistics != null) {
            messageStatistics.setParent(this);
        }
    }

    public WAMPCallStatistics createChildCallStatistics(String name) {
        if (getCallStatistics() != null) {
            return getCallStatistics().createChild(null, name);
        } else {
            return null;
        }
    }

    public WAMPMessageStatistics createChildMessageStatistics(String name) {
        if (getMessageStatistics() != null) {
            return getMessageStatistics().createChild(null, name);
        } else {
            return null;
        }
    }

    @Override
    public void onTouched(Statistics stat) {
        touch();
    }
    
    
}
