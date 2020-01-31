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
package ssg.lib.di.base;

import java.io.IOException;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ssg.lib.common.buffers.BufferTools;

/**
 *
 * @author 000ssg
 */
public class BufferingDI<T extends Buffer, P> extends BaseDI<T, P> {

    Map<P, List<T>[]> buffers = new LinkedHashMap<>();

    public BufferingDI() {
    }

    @Override
    public Collection<P> providers() {
        Collection<P> r = super.providers();
        if (!buffers.isEmpty()) {
            if (r == null) {
                r = buffers.keySet();
            } else {
                for (P p : buffers.keySet()) {
                    if (!r.contains(p)) {
                        r.add(p);
                    }
                }
            }
        }
        return r;
    }

    @Override
    public void delete(P provider) throws IOException {
        super.delete(provider);
        if (provider != null) {
            synchronized (buffers) {
                if (buffers.containsKey(provider)) {
                    buffers.remove(provider);
                }
            }
        }
    }

    @Override
    public long size(Collection<T>... data) {
        return BufferTools.getRemaining(data);
    }

    @Override
    public void consume(P provider, Collection<T>... data) throws IOException {
        List<T> buf = getBuffer(provider, true);
        if (data != null) {
            BufferTools.moveBuffersTo(buf, data);
        }
    }

    @Override
    public List<T> produce(P provider) throws IOException {
        List<T> buf = getBuffer(provider, false);
        if (buf != null && !buf.isEmpty()) {
            List<T> r = new ArrayList<>();
            synchronized (buf) {
                r.addAll(buf);
                buf.clear();
            }
            return r;
        } else {
            return null;
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////// tools
    ////////////////////////////////////////////////////////////////////////////
    /**
     * Returns appropriate buffer creating it if missing.
     *
     * @param provider
     * @param input
     * @return
     */
    List<T> getBuffer(P provider, boolean input) {
        List<T> r = null;
        List<T>[] bufs = buffers.get(provider);
        if (bufs == null) {
            bufs = new ArrayList[2];
            buffers.put(provider, bufs);
        }
        if (input) {
            if (bufs[0] == null) {
                bufs[0] = new ArrayList<T>();
            }
            r = bufs[0];
        } else {
            if (bufs[1] == null) {
                bufs[1] = new ArrayList<T>();
            }
            r = bufs[1];
        }
        return r;
    }

    public boolean hasInput(P provider) {
        List<T>[] bufs = buffers.get(provider);
        return bufs != null && bufs[0] != null && BufferTools.hasRemaining(bufs[0]);
    }

    public boolean hasOutput(P provider) {
        List<T>[] bufs = buffers.get(provider);
        return bufs != null && bufs[1] != null && BufferTools.hasRemaining(bufs[1]);
    }

    /**
     * Push data for output via "read".
     *
     * @param provider
     * @param data
     * @return
     * @throws IOException
     */
    public long push(P provider, Collection<T>... data) throws IOException {
        long c = 0;
        if (data != null) {
            List<T> buf = getBuffer(provider, false);
            c = BufferTools.moveBuffersTo(buf, data);
        }
        return c;
    }

    /**
     * Fetch data from input provided via "write".
     *
     * @param provider
     * @return
     * @throws IOException
     */
    public List<T> fetch(P provider) throws IOException {
        List<T> buf = getBuffer(provider, true);
        if (buf != null && !buf.isEmpty()) {
            List<T> r = new ArrayList<>();
            synchronized (buf) {
                r.addAll(buf);
                buf.clear();
            }
            return r;
        } else {
            return null;
        }
    }
}
