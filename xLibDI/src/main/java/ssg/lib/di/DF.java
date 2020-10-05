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
 * Data filtering: modify data on read and on write operations. Supports nested
 * filters to allow cascaded transformations.
 *
 * @author 000ssg
 * @param <T> input data type
 * @param <P>
 */
public interface DF<T, P> extends DMF<T, P> {

    ////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////// I/O
    ////////////////////////////////////////////////////////////////////////
    /**
     * Performs data pre-processing on "write" operation.
     *
     * @param owner
     * @param provider
     * @param data
     * @return
     * @throws IOException
     */
    default List<T> onWrite(DM<P> owner, P provider, Collection<T>... data) throws IOException {
        DF<T, P> filter = filter();
        if (filter != null) {
            List<T> f = filter.onWrite(owner, provider, data);
            data = (f != null) ? new Collection[]{f} : null;
        }
        return writeFilter(owner, provider, data);
    }

    /**
     * Performs data post-processing on "read" operation
     *
     * @param owner
     * @param provider
     * @param data
     * @return
     * @throws IOException
     */
    default List<T> onRead(DM<P> owner, P provider, Collection<T>... data) throws IOException {
        DF<T, P> filter = filter();
        List<T> r = readFilter(owner, provider, data);
        if (filter != null) {
            r = filter.onRead(owner, provider, r);
        }
        return r;
    }

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////// impl
    ////////////////////////////////////////////////////////////////////////////
    /**
     * Process external data and return filtered input.
     *
     * @param owner
     * @param provider
     * @param data
     * @return
     * @throws IOException
     */
    List<T> writeFilter(DM<P> owner, P provider, Collection<T>... data) throws IOException;

    /**
     * Process output data and return filtered result.
     *
     * @param owner
     * @param provider
     * @param data
     * @return
     * @throws IOException
     */
    List<T> readFilter(DM<P> owner, P provider, Collection<T>... data) throws IOException;
}
