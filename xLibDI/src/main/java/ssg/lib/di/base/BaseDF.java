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
import ssg.lib.di.DM;

/**
 *
 * @author 000ssg
 */
public abstract class BaseDF<T, P> implements DF<T, P> {

    DF<T, P> filter;

    @Override
    public List<T> onWrite(DM<P> owner, P provider, Collection<T>... data) throws IOException {
        List<T> r = null;
        if (filter != null) {
            List<T> f = filter.onWrite(owner, provider, data);
            data = (f != null) ? new Collection[]{f} : null;
        }
        return writeFilter(owner, provider, data);
    }

    @Override
    public List<T> onRead(DM<P> owner, P provider, Collection<T>... data) throws IOException {
        List<T> r = readFilter(owner, provider, data);
        if (filter != null) {
            r = filter.onRead(owner, provider, r);
        }
        return r;
    }

    /**
     * By default filter does not block actual read/write operations.
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
    public void healthCheck(P provider) throws IOException {
        if (filter != null) {
            filter.healthCheck(provider);
        }
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

    public abstract List<T> writeFilter(DM<P> owner, P provider, Collection<T>... data) throws IOException;

    public abstract List<T> readFilter(DM<P> owner, P provider, Collection<T>... data) throws IOException;

}
