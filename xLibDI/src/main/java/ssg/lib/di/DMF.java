/*
 * The MIT License
 *
 * Copyright 2020 000ssg.
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
package ssg.lib.di;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;

/**
 * Extension of Data management with filter and filter-aware default
 * implementation of most methods.
 *
 * Intermediate utility interface for core DI/DF declaration.
 *
 * @author 000ssg
 * @param <T>
 * @param <P>
 */
public interface DMF<T, P> extends DM<P> {

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////// filter
    ////////////////////////////////////////////////////////////////////////////
    void filter(DF<T, P> filter);

    DF<T, P> filter();

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////// DM
    ////////////////////////////////////////////////////////////////////////////
    @Override
    default void healthCheck(P provider) throws IOException {
        DF<T, P> filter = filter();
        if (filter != null) {
            filter.healthCheck(provider);
        }
    }

    @Override
    default void delete(P provider) throws IOException {
        DF<T, P> filter = filter();
        if (filter != null) {
            filter.delete(provider);
        }
    }

    @Override
    default Collection<P> providers() {
        Collection<P> r = new LinkedHashSet<>();
        DF<T, P> filter = filter();
        if (filter != null) {
            r.addAll(filter.providers());
        }
        return r;
    }

    @Override
    default void onProviderEvent(P provider, String event, Object... params) {
        DF<T, P> filter = filter();
        if (filter != null) {
            filter.onProviderEvent(provider, event, params);
        }
    }
}
