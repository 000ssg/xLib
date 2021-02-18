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
package ssg.lib.di;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Data interface: write/read data with optional filtering.
 *
 * DI organizes data flow with optional filtering as follows:
 *
 * from provider -%gt; <code>write<code> [ filter.<code>onWrite</code> ] -&gt;
 * <code>consume</code>
 *
 * <code>produce</code> -&gt; <code>read</code> [ filter.<code>onRead</code> ]
 * -&gt; to provider
 *
 * If filter is present, then <code>consume</code> and <code>produce</code>
 * methods are invoked only if filter is ready (filter.<code>isReady</code>) to
 * enable provider channel configuration (e.g. as required for establishing of
 * SSL connection).
 *
 * Default implementations for read/write operations are provided.
 *
 * @author 000ssg
 */
public interface DI<T, P> extends DMF<T, P> {

    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////// I/O
    ////////////////////////////////////////////////////////////////////////////
    /**
     * Accept provider data. Data are passed to <code>consume</code> directly
     * or, if filter is defined, after pre-processing by filter.
     *
     * @param provider
     * @param data
     * @return
     * @throws IOException
     */
    default long write(P provider, Collection<T>... data) throws IOException {
        long c = size(data);
        DF<T, P> filter = filter();
        if (filter != null) {
            List<T> f = filter.onWrite(this, provider, data);
            c = c - size(data);
            if (filter.isReady(provider)) {
                data = (f != null) ? new Collection[]{f} : null;
                consume(provider, data);
            }
        } else {
            consume(provider, data);
            c = c - size(data);
        }
        return c;
    }

    /**
     * Return data for provider. Data are retrieved via <code>produce</code>
     * and, if filter is defined, filtered (processed) for output.
     *
     * @param provider
     * @return
     * @throws IOException
     */
    default List<T> read(P provider) throws IOException {
        List<T> data = null;
        DF<T, P> filter = filter();
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
     * Callback to apply provider-specific actions when filter notifies
     * readiness. Nothing by default.
     *
     * @param provider
     * @throws IOException
     */
    default void onFilterReady(P provider) throws IOException {
        // Use to adjust
    }

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////// base
    ////////////////////////////////////////////////////////////////////////////
    /**
     * Evaluates data size or 0 if no data.
     *
     * @param data
     * @return
     */
    public abstract long size(Collection<T>... data);

    /**
     * Consume provider data (filtered, if any filter).
     *
     * @param provider
     * @param data
     * @throws IOException
     */
    public abstract void consume(P provider, Collection<T>... data) throws IOException;

    /**
     * Prepare data for provider.
     *
     * @param provider
     * @return
     * @throws IOException
     */
    public abstract List<T> produce(P provider) throws IOException;
}
