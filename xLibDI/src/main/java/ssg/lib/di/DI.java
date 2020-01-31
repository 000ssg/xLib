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
 * @author 000ssg
 */
public interface DI<T, P> extends DM<P> {

    ////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////// I/O
    ////////////////////////////////////////////////////////////////////////////
    /**
     * Accept provider data
     *
     * @param provider
     * @param data
     * @return
     * @throws IOException
     */
    long write(P provider, Collection<T>... data) throws IOException;

    /**
     * Return data for provider
     *
     * @param provider
     * @return
     * @throws IOException
     */
    List<T> read(P provider) throws IOException;

    ////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////// filter
    ////////////////////////////////////////////////////////////////////////////
    void filter(DF<T, P> filter);

    DF<T, P> filter();
}
