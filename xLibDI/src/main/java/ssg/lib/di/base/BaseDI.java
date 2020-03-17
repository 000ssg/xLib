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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import ssg.lib.di.DF;
import ssg.lib.di.DI;

/**
 * Implements filter-specific portion of DI interface delegating data processing
 * to successors via declared abstract methods: size, consume, produce.
 *
 * @author 000ssg
 * @param <T>
 * @param <P>
 */
public abstract class BaseDI<T, P> implements DI<T, P> {

    DF<T, P> filter;

    public <D extends DI<T, P>> D configure(DF<T, P> filter) {
        filter(filter);
        return (D) this;
    }

    @Override
    public long write(P provider, Collection<T>... data) throws IOException {
        long c = size(data);
        if (filter != null) {
            List<T> f = filter.onWrite(this, provider, data);
            c = c - size(data);
            if (filter.isReady(provider)) {
                data = (f != null) ? new Collection[]{f} : null;
                consume(provider, data);
            }
        } else {
            consume(provider, data);
        }
        return c - size(data);
    }

    @Override
    public List<T> read(P provider) throws IOException {
        List<T> data = null;
        if (filter != null) {
            if (filter.isReady(provider)) {
                data = produce(provider);
                data = filter.onRead(this, provider, data);
            } else {
                data = filter.onRead(this, provider, data);
                if (filter.isReady(provider)) {
                    onFilterReady(provider);
                }
            }
        } else {
            data = produce(provider);
        }
        return data;
    }

    /**
     * By default no blocking of actual read/write operations.
     *
     * @param provider
     * @return
     * @throws IOException
     */
    @Override
    public boolean isReady(P provider) throws IOException {
        return true;
    }

    @Override
    public void filter(DF<T, P> filter) {
        this.filter = filter;
    }

    @Override
    public DF<T, P> filter() {
        return filter;
    }

    @Override
    public void delete(P provider) throws IOException {
        if (filter != null) {
            filter.delete(provider);
        }
    }

    @Override
    public Collection<P> providers() {
        Collection<P> r = new LinkedHashSet<>();
        if (filter != null) {
            r.addAll(filter.providers());
        }
        return r;
    }

    @Override
    public void onProviderEvent(P provider, String event, Object... params) {
        if (filter != null) {
            filter.onProviderEvent(provider, event, params);
        }
    }

    @Override
    public void healthCheck(P provider) throws IOException {
        // no health check procedure by default
        if (filter != null) {
            filter.healthCheck(provider);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////// tools
    ////////////////////////////////////////////////////////////////////////////
    public void onFilterReady(P provider) throws IOException {
        // Use to adjust
    }

    public abstract long size(Collection<T>... data);

    public abstract void consume(P provider, Collection<T>... data) throws IOException;

    public abstract List<T> produce(P provider) throws IOException;
}
