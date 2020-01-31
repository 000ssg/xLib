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
package ssg.lib.http.base;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import ssg.lib.common.buffers.BufferTools;

/**
 *
 * @author 000ssg
 */
public class Body {

    HttpData http;

    List<ByteBuffer> data = new ArrayList<ByteBuffer>();
    long length;
    long sz;
    boolean sent = false;

    public Body() {
    }

    public Body(HttpData http) {
        this.http = http;
    }

    public void initFrom(Body body) {
        data.add(ByteBuffer.wrap(BufferTools.toBytes(true, body.data())));
        length = body.length();
        sz = body.size();
    }

    public void add(byte[] bbs) throws IOException {
        if (bbs != null && bbs.length > 0) {
            add(ByteBuffer.wrap(bbs));
        }
    }

    public void add(ByteBuffer... bbs) throws IOException {
        if (bbs != null) {
            for (ByteBuffer bb : bbs) {
                if (bb != null && bb.hasRemaining()) {
                    int c = bb.remaining();
                    ByteBuffer bi = ByteBuffer.allocate(bb.remaining());
                    bi.put(bb);
                    length += c;
                    synchronized (data) {
                        sz += c;
                        ((Buffer) bi).flip();
                        data.add(bi);
                    }
                }
            }
        }
    }

    /**
     * Primary remove method. Other "remove"s just overload AND use it.
     *
     * @return
     */
    public Collection<ByteBuffer> removeAsCollection() throws IOException {
        Collection<ByteBuffer> r = new ArrayList<ByteBuffer>();
        synchronized (data) {
            if (sz == 0) {
            } else {
                r.addAll(data);
                sz -= BufferTools.getRemaining(r);
                data.clear();
            }
        }
        return r;
    }

    public ByteBuffer[] remove() throws IOException {
        ByteBuffer[] r = null;
        if (sz == 0) {
            r = new ByteBuffer[0];
        } else {
            Collection<ByteBuffer> dd = removeAsCollection();
            r = dd.toArray(new ByteBuffer[dd.size()]);
            sz -= BufferTools.getRemaining(r);
        }
        return r;
    }

    public byte[] removeAsBytes() throws IOException {
        byte[] r = new byte[(int) sz];
        if (sz == 0) {
        } else {
            int off = 0;
            for (ByteBuffer bb : removeAsCollection()) {
                int c = bb.remaining();
                bb.get(r, off, c);
                off += c;
            }
        }
        return r;
    }

    /**
     * Returns currently available body content as bytes without removing it.
     *
     * @return
     */
    public byte[] asBytes() {
        byte[] r = new byte[(int) length];//http.size];
        synchronized (data) {
            if (sz == 0) {
            } else {
                int off = 0;
                for (ByteBuffer bb : data) {
                    int c = bb.remaining();
                    bb.duplicate().get(r, off, c);
                    off += c;
                }
            }
        }
        return r;
    }

    public List<ByteBuffer> data() {
        return data;
    }

    /**
     * Body FULL length
     *
     * @return
     */
    public long length() {
        return length;
    }

    /**
     * Body in-cache size
     *
     * @return
     */
    public long size() {
        return sz;
    }

    public boolean isEmpty() {
        return sz == 0;
    }

    public void fixBodyBeforeSend() throws IOException {
        if (sent) {
            throw new IOException("Cannot fix already sent body.");
        }
    }

    @Override
    public String toString() {
        return "Body{" + "data=" + data.size() + ", length=" + length + ", size=" + sz + ((sz > 0) ? "\n  " + BufferTools.toText(null, data()) + "\n" : "") + '}';
    }

}
