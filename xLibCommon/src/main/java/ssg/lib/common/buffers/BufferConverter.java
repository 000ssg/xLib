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
package ssg.lib.common.buffers;

import java.io.IOException;
import java.io.Serializable;
import java.nio.Buffer;

/**
 * Generic buffer-based data conversion interface. Data are entered with "write"
 * and retrieved with "read". Data may be flushed to ensure all possible entered
 * data are converted into output data. Flush must be automatically preformed on
 * close.
 *
 * @author 000ssg
 */
public interface BufferConverter<S extends Buffer, T extends Buffer> extends Serializable, Cloneable {

    /**
     * Add data to converter.
     *
     * @param bufs
     * @throws IOException
     */
    void write(S... bufs) throws IOException;

    /**
     * Retrieve data after conversion into provided buffer.
     *
     * @param buf
     * @return
     * @throws IOException
     */
    int read(T buf) throws IOException;

    /**
     * Try to get as much data as expected. May return less.
     *
     * @param expectedSize
     * @return
     * @throws IOException
     */
    T read(int expectedSize) throws IOException;

    /**
     * Number of added data items.
     *
     * @return
     */
    long getSourceLength();

    /**
     * Number of produced data items.
     *
     * @return
     */
    long getTargetLength();

    /**
     * Number of readable data items. Increases on writes and decreases on reads.
     *
     * @return
     */
    long getAvailable();

    /**
     * Finalizes conversion and flushes all converted data.
     *
     * @throws IOException
     */
    void close() throws IOException;

    /**
     * If true then write operations are allowed.
     *
     * @return
     */
    boolean isOpen();

    /**
     * Apply conversion to source data if non-empty.
     *
     * @throws IOException
     */
    void flush() throws IOException;
}
