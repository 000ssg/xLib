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
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author 000ssg
 */
public class ByteBufferPipe extends BufferPipe<ByteBuffer> {

    List<byte[]> data;
    int pos = 0; // pos in current buffer (data element at index 0)

    public ByteBufferPipe() {
        data = new ArrayList<>();
    }

    public ByteBufferPipe(BufferPipe<ByteBuffer> nested) {
        super(nested);
        data = new ArrayList<>();
    }

    @Override
    public void setNested(BufferPipe nested) throws IOException {
        if (this.nested == null && nested != null) {
            if (data != null && !data.isEmpty()) {
                for (byte[] bb : data) {
                    nested.write(ByteBuffer.wrap(bb));
                }
            }
            data = null;
            this.nested = nested;
        } else if (nested != null) {
            throw new IOException("Cannot change nested Buffered.");
        } else {
            data = new ArrayList<byte[]>();
        }
    }

    /**
     * Adds provided data by making COPY of it.
     *
     * @param bbs
     * @throws IOException
     */
    @Override
    public void write(ByteBuffer... bbs) throws IOException {
        if (nested != null) {
            nested.write(bbs);
        } else if (bbs != null) {
            synchronized (data) {
                for (ByteBuffer bb : bbs) {
                    if (bb != null && bb.hasRemaining()) {
                        byte[] buf = new byte[bb.remaining()];
                        bb.get(buf);
                        data.add(buf);
                        length += buf.length;
                        size += buf.length;
                    }
                }
            }
        }
    }

    @Override
    public int read(ByteBuffer bb) throws IOException {
        if (nested != null) {
            return nested.read(bb);
        }
        if (data.isEmpty() && closed) {
            return -1;
        }
        int len = bb.remaining();
        int l = 0;
        synchronized (data) {
            Iterator<byte[]> it = data.iterator();
            while (len > 0 && it.hasNext()) {
                byte[] buf = it.next();
                int sz = Math.min(len, buf.length - pos);
                bb.put(buf, pos, sz);
                len -= sz;
                pos += sz;
                l += sz;
                if (buf.length == pos) {
                    it.remove();
                    pos = 0;
                }
                if (len == 0) {
                    break;
                }
            }
        }
        size -= l;
        return l;
    }

    /**
     * Utility read() method. Uses base read(T).
     *
     * @param expectedSize
     * @return
     * @throws IOException
     */
    @Override
    public ByteBuffer read(int expectedSize) throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(expectedSize);
        int c = read(bb);
        if (c == -1) {
            return null;
        }
        ((Buffer) bb).flip();
        return bb;
    }

    @Override
    public Object clone() {
        ByteBufferPipe copy = (ByteBufferPipe) super.clone();
        copy.data.clear();
        copy.pos = 0;
        return copy;
    }

    @Override
    public String toString() {
        if (data != null) {
            String ds = "";
            long sz = 0;
            for (byte[] bb : data) {
                ds += " " + bb.length;
                sz += bb.length;
            }
            return getClass().getSimpleName() + "{" + "data=" + ds + "-> " + sz + "/" + data.size() + ", pos=" + pos + '}';
        } else {
            return getClass().getSimpleName() + "{no data}";
        }
    }

}
