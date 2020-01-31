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
package ssg.lib.wamp.rpc.impl;

import java.util.Map;
import ssg.lib.wamp.stat.WAMPCallStatistics;

/**
 * Procedure metadata. Actual impelemntations provide callee and dealer specific
 * contexts.
 *
 * @author sesidoro
 */
public class Procedure {

    private long id;
    private String name;
    private Map<String, Object> options;
    private WAMPCallStatistics statistics;

    public Procedure(String name, Map<String, Object> options) {
        this.name = name;
        this.options = options;
    }

    @Override
    public String toString() {
        return (getClass().isAnonymousClass()
                ? getClass().getName()
                : getClass().getSimpleName())
                + "{"
                + "id=" + getId()
                + ", name=" + getName()
                + ", options=" + getOptions()
                + ((statistics != null && statistics.hasCalls()) ? ", " + statistics : "")
                + '}';
    }

    public <T extends Procedure> T statistics(WAMPCallStatistics statistics) {
        setStatistics(statistics);
        return (T) this;
    }

    /**
     * @return the statistics
     */
    public WAMPCallStatistics getStatistics() {
        return statistics;
    }

    /**
     * @param statistics the statistics to set
     */
    public void setStatistics(WAMPCallStatistics statistics) {
        this.statistics = statistics;
    }

    /**
     * @return the id
     */
    public long getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the options
     */
    public Map<String, Object> getOptions() {
        return options;
    }

}
