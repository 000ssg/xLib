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
import java.util.Collection;

/**
 * Non-blocking cascaded T reader.
 *
 * @author 000ssg
 */
public abstract class BufferPipe<T extends Buffer> implements Serializable, Cloneable {

    long length; // totally written
    long size; // left in buffer
    boolean closed = false; // closed is detected/forced.
    //boolean flushOnDemand = true; // flush when reading more than in out...

    BufferPipe<T> nested;

    public BufferPipe() {
    }

    public BufferPipe(BufferPipe<T> nested) {
        this.nested = nested;
    }

    public BufferPipe(T... bufs) throws IOException {
        write(bufs);
    }

    public BufferPipe(Collection<T>... bufs) throws IOException {
        if (bufs != null) {
            for (Collection<T> bbs : bufs) {
                if (bbs != null && !bbs.isEmpty()) {
                    for (T bb : bbs) {
                        write(bb);
                    }
                }
            }
        }
    }

    public String getChainString() {
        StringBuilder sb = new StringBuilder();
        if (getClass().isAnonymousClass()) {
            sb.append(getClass().getName());
        } else {
            sb.append(getClass().getSimpleName());
        }
        if (nested != null) {
            sb.append(" | " + nested.getChainString());
        }
        return sb.toString();
    }

    /**
     * force EOf flag.
     */
    public void close() throws IOException {
        if (nested != null) {
            nested.close();
        }
        closed = true;
    }

    /**
     * Returns closed status.
     *
     * @return
     */
    public boolean isClosed() {
        return closed;
    }

    public void setNested(BufferPipe nested) throws IOException {
        if (this.nested == null && nested != null) {
            this.nested = nested;
        } else if (nested != null) {
            throw new IOException("Cannot change nested Buffered.");
        }
    }

    /**
     * Adds provided data by making COPY of it.
     *
     * @param bbs
     * @throws IOException
     */
    public abstract void write(T... bbs) throws IOException;

    public long getLength() {
        return (nested != null) ? nested.getLength() : length;
    }

    public long getAvailable() {
        return (nested != null) ? nested.getAvailable() : size;
    }

    @Override
    public Object clone() {
        try {
            BufferPipe copy = (BufferPipe) super.clone();
            copy.closed = false;
            copy.length = 0;
            copy.size = 0;
            if (copy.nested != null) {
                copy.nested = (BufferPipe) copy.nested.clone();
            }
            return copy;
        } catch (CloneNotSupportedException cnsex) {
            return null;
        }
    }

    public abstract int read(T bb) throws IOException;

    /**
     * Utility read() method. Uses base read(T).
     *
     * @param expectedSize
     * @return
     * @throws IOException
     */
    public abstract T read(int expectedSize) throws IOException;

}
