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

/**
 * Represents RPC call. Actual implementations represent Caller, Dealer, and
 * Callee contexts.
 *
 * @author 000ssg
 */
public class Call {

    private long id;
    long started = System.nanoTime();
    long timeout = 0;
    private boolean progressiveResult = false;

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

    public long getStarted() {
        return started;
    }

    public long durationNano() {
        return System.nanoTime() - started;
    }

    /**
     * Returns true if timeout is set (!=0) procedure execution time exceeds
     * timeout.
     *
     * @return
     */
    public boolean isOvertime() {
        return hasTimeout() && timeout < (durationNano() / 1000000);
    }

    public boolean hasTimeout() {
        return timeout != 0;
    }

    public long getTimesout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    @Override
    public String toString() {
        return (getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName())
                + "{"
                + "id=" + getId()
                + ", started=" + getStarted() + " (wip" + (timeout != 0 ? "/timeout" : "") + "=" + (System.nanoTime() - started) / 1000000f + (timeout != 0 ? "/" + timeout : "") + "ms)"
                + ", progressive result=" + isProgressiveResult()
                + '}';
    }

    /**
     * @return the progressiveResult
     */
    public boolean isProgressiveResult() {
        return progressiveResult;
    }

    /**
     * @param progressiveResult the progressiveResult to set
     */
    public void setProgressiveResult(boolean progressiveResult) {
        this.progressiveResult = progressiveResult;
    }

}
