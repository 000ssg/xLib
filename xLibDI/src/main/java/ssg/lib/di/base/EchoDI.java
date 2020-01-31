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
public abstract class EchoDI<T, P> extends BaseDI<T, P> {

    Map<P, List<T>> echo = new LinkedHashMap<>();

    @Override
    public Collection<P> providers() {
        Collection<P> r = super.providers();
        if (!echo.isEmpty()) {
            if (r == null) {
                r = echo.keySet();
            } else {
                for (P p : echo.keySet()) {
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
            synchronized (echo) {
                if (echo.containsKey(provider)) {
                    echo.remove(provider);
                }
            }
        }
    }

    @Override
    public long size(Collection<T>... data) {
        long c = 0;
        if (data != null) {
            for (Collection<T> ts : data) {
                if (ts != null) {
                    c += ts.size();
                }
            }
        }
        return c;
    }

    @Override
    public void consume(P provider, Collection<T>... data) throws IOException {
        List<T> buf = echo.get(provider);
        if (buf == null) {
            buf = new ArrayList<>();
            echo.put(provider, buf);
        }
        List<T> r = echo(provider, data);
        if (r != null && !r.isEmpty()) {
            synchronized (buf) {
                buf.addAll(r);
            }
        }
    }

    @Override
    public List<T> produce(P provider) throws IOException {
        List<T> r = null;
        List<T> buf = echo.get(provider);
        if (buf != null && !buf.isEmpty()) {
            r = new ArrayList<>();
            synchronized (buf) {
                r.addAll(buf);
                buf.clear();
            }
        }
        return r;
    }

    public abstract List<T> echo(P provider, Collection<T>... data);

    public static class BufferEchoDI<T extends Buffer, P> extends EchoDI<T, P> {

        @Override
        public long size(Collection<T>... data) {
            return BufferTools.getRemaining(data);
        }

        @Override
        public List<T> echo(P provider, Collection<T>... data) {
            if (!BufferTools.hasRemaining(data)) {
                return null;
            }
            List<T> r = new ArrayList<>();
            BufferTools.moveBuffersTo(r, data);
            return r;
        }
    }
}
